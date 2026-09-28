package com.example.healthplan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.healthplan.ocr.LabelParser
import com.example.healthplan.ocr.OcrEngine
import java.io.File
import kotlin.concurrent.thread

/** Фото этикетки → офлайн-распознавание (Tesseract rus+eng) → разбор пищевой ценности → форма проверки. */
class OcrActivity : BaseActivity() {
    private var bmp: Bitmap? = null
    private var raw: String? = null
    private var busy = false
    private var crop = 0 // 0 весь, 1 верх, 2 низ, 3 центр
    private var photoUri: Uri? = null
    private var rawEt: com.google.android.material.textfield.TextInputEditText? = null

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photoUri?.let { loadUri(it) } }
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { loadUri(it) } }
    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else Toast.makeText(this, "Без доступа к камере можно выбрать фото из галереи", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // «Поделиться» фото из галереи/камеры → сразу в распознавание
        val shared: Uri? = if (savedInstanceState == null && intent?.action == Intent.ACTION_SEND)
            @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri) else null
        if (shared != null) loadUri(shared) else render()
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "label.jpg")
        photoUri = FileProvider.getUriForFile(this, "$packageName.files", f)
        takePhoto.launch(photoUri)
    }

    private fun loadUri(u: Uri) {
        bmp = OcrEngine.load(this, u); raw = null
        if (bmp == null) Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
        render()
    }

    /** Области обрезают только по высоте: строки состава идут через всю ширину, обрезка по бокам резала концы строк. */
    private fun cropped(b: Bitmap): Bitmap = when (crop) {
        1 -> Bitmap.createBitmap(b, 0, 0, b.width, b.height * 6 / 10)
        2 -> Bitmap.createBitmap(b, 0, b.height * 4 / 10, b.width, b.height - b.height * 4 / 10)
        3 -> Bitmap.createBitmap(b, 0, b.height * 15 / 100, b.width, b.height * 70 / 100)
        else -> b
    }

    private var progressText = ""
    private var info: String? = null

    private fun render(): Unit = screen {
        h1("Распознать этикетку")
        muted("Сфотографируйте блок «Пищевая ценность» крупно, ровно и при хорошем свете, чтобы строки попали в кадр целиком. Распознавание работает без интернета (русский и английский). Потом проверьте цифры.", 14f)
        row {
            button("📷 Сфотографировать", weight = 1f) {
                if (ContextCompat.checkSelfPermission(this@OcrActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else camPerm.launch(Manifest.permission.CAMERA)
            }
            button("🖼 Из галереи", outlined = true, weight = 1f) { pickImage.launch("image/*") }
        }
        bmp?.let { b ->
            card {
                val shown = cropped(b)
                addView(ImageView(context).apply { setImageBitmap(shown); adjustViewBounds = true; maxHeight = Ui.dp(context, 420) }, lp(topDp = 4))
                row {
                    button("↺ 90°", outlined = true, weight = 1f) { bmp = OcrEngine.rotate(b, -90f); raw = null; render() }
                    button("↻ 90°", outlined = true, weight = 1f) { bmp = OcrEngine.rotate(b, 90f); raw = null; render() }
                }
                text("Область", 16f, true, topDp = 8)
                muted("По умолчанию распознаётся весь снимок. «Верх», «Низ», «Центр» обрезают только по высоте.", 13f)
                val cg = com.google.android.material.chip.ChipGroup(context).apply { isSingleSelection = true }
                listOf("Весь снимок", "Верх", "Низ", "Центр").forEachIndexed { i, s ->
                    cg.addView(com.google.android.material.chip.Chip(context).apply { text = s; isCheckable = true; isChecked = i == crop; setOnClickListener { crop = i; raw = null; render() } })
                }
                addView(cg, lp())
                button(if (busy) "Распознаю… $progressText" else "Распознать текст") {
                    if (busy) return@button
                    busy = true; progressText = ""; render()
                    val img = cropped(b)
                    thread {
                        val res = try {
                            OcrEngine.recognize(this@OcrActivity, img) { i, n -> runOnUiThread { if (busy) { progressText = "проход $i из $n"; render() } } }
                        } catch (e: Throwable) { null.also { runOnUiThread { Toast.makeText(this@OcrActivity, "Ошибка OCR: ${e.message}", Toast.LENGTH_LONG).show() } } }
                        runOnUiThread {
                            busy = false
                            raw = res?.text ?: ""
                            info = res?.let { "Режим: ${it.passName}; проходов: ${it.passesRun}; масштаб ×${Ui.f1(it.scale)}; наклон ${Ui.f1(it.skewDeg)}°; ${it.millis / 1000} с" }
                            render()
                        }
                    }
                }.isEnabled = !busy
            }
        }
        raw?.let { r ->
            val p = LabelParser.parse(r)
            card(stroke = if (p.foundDirect >= 3) Ui.GREEN else Ui.YELLOW) {
                text("Найдено", 18f, true)
                fun v(x: Double?) = x?.let { Ui.f1(it) } ?: "—"
                kv("Калорийность", p.kcal?.let { "${Ui.f0(it)} ккал" } ?: "—", when {
                    p.kcalFromKj -> "пересчитано из ${Ui.f0(p.kj ?: 0.0)} кДж"
                    p.kcalFromMacros -> "рассчитано по БЖУ (на этикетке не найдено)"
                    else -> null })
                kv("Белки", "${v(p.protein)} г"); kv("Жиры", "${v(p.fat)} г"); kv("Углеводы", "${v(p.carbs)} г"); kv("Клетчатка", "${v(p.fiber)} г")
                kv("Значения", if (p.portionG != null) "на порцию ${Ui.g(p.portionG)} г" else "на 100 г")
                p.warnings.forEach { muted("⚠ $it", 14f) }
                if (r.isBlank()) muted("Текст не распознан. Попробуйте снять ближе и ровнее, при хорошем освещении.", 14f)
                button("Проверить и сохранить как свой продукт") {
                    val i = Intent(this@OcrActivity, CustomFoodActivity::class.java).putExtra("ocr", true).putExtra("raw", r)
                        .putExtra("per100", p.portionG == null)
                    p.kcal?.let { i.putExtra("kcal", it) }; p.protein?.let { i.putExtra("p", it) }; p.fat?.let { i.putExtra("f", it) }
                    p.carbs?.let { i.putExtra("c", it) }; p.fiber?.let { i.putExtra("fib", it) }; p.portionG?.let { i.putExtra("portion", it) }
                    if (p.kcalFromMacros) i.putExtra("kcalCalculated", true)
                    startActivity(i); finish()
                }
            }
            card {
                text("Распознанный текст (можно поправить)", 16f, true)
                info?.let { muted(it, 12f) }
                // Весь текст целиком, с начала: поле растягивается по высоте, курсор — в начале, фокус не забирает
                isFocusableInTouchMode = true; descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
                rawEt = input("Текст", r, number = false).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    isSingleLine = false; minLines = 4; maxLines = Int.MAX_VALUE; isVerticalScrollBarEnabled = false
                    setHorizontallyScrolling(false); textSize = 14f; setSelection(0)
                }
                button("Разобрать заново", outlined = true) { raw = rawEt?.text?.toString() ?: r; render() }
            }
        }
    }
}
