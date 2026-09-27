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

    private fun cropped(b: Bitmap): Bitmap = when (crop) {
        1 -> Bitmap.createBitmap(b, 0, 0, b.width, b.height / 2 + b.height / 10)
        2 -> Bitmap.createBitmap(b, 0, b.height * 4 / 10, b.width, b.height - b.height * 4 / 10)
        3 -> Bitmap.createBitmap(b, b.width / 10, b.height / 10, b.width * 8 / 10, b.height * 8 / 10)
        else -> b
    }

    private fun render(): Unit = screen {
        h1("Распознать этикетку")
        muted("Сфотографируйте блок «Пищевая ценность» крупно, ровно и при хорошем свете. Распознавание работает без интернета (русский и английский). Потом проверьте цифры.", 14f)
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
                val cg = com.google.android.material.chip.ChipGroup(context).apply { isSingleSelection = true }
                listOf("Весь снимок", "Верх", "Низ", "Центр").forEachIndexed { i, s ->
                    cg.addView(com.google.android.material.chip.Chip(context).apply { text = s; isCheckable = true; isChecked = i == crop; setOnClickListener { crop = i; raw = null; render() } })
                }
                addView(cg, lp())
                button(if (busy) "Распознаю… (10–40 с)" else "Распознать текст") {
                    if (busy) return@button
                    busy = true; render()
                    val img = cropped(b)
                    thread {
                        val t = try { OcrEngine.recognize(this@OcrActivity, img) } catch (e: Throwable) { "Ошибка OCR: ${e.message}" }
                        runOnUiThread { busy = false; raw = t; render() }
                    }
                }.isEnabled = !busy
            }
        }
        raw?.let { r ->
            val p = LabelParser.parse(r)
            card(stroke = if (p.found >= 3) Ui.GREEN else Ui.YELLOW) {
                text("Найдено", 18f, true)
                fun v(x: Double?) = x?.let { Ui.f1(it) } ?: "—"
                kv("Калорийность", p.kcal?.let { "${Ui.f0(it)} ккал" } ?: "—", if (p.kcalFromKj) "пересчитано из ${Ui.f0(p.kj ?: 0.0)} кДж" else null)
                kv("Белки", "${v(p.protein)} г"); kv("Жиры", "${v(p.fat)} г"); kv("Углеводы", "${v(p.carbs)} г"); kv("Клетчатка", "${v(p.fiber)} г")
                kv("Значения", if (p.portionG != null) "на порцию ${Ui.g(p.portionG)} г" else "на 100 г")
                p.warnings.forEach { muted("⚠ $it", 14f) }
                button("Проверить и сохранить как свой продукт") {
                    val i = Intent(this@OcrActivity, CustomFoodActivity::class.java).putExtra("ocr", true).putExtra("raw", r)
                        .putExtra("per100", p.portionG == null)
                    p.kcal?.let { i.putExtra("kcal", it) }; p.protein?.let { i.putExtra("p", it) }; p.fat?.let { i.putExtra("f", it) }
                    p.carbs?.let { i.putExtra("c", it) }; p.fiber?.let { i.putExtra("fib", it) }; p.portionG?.let { i.putExtra("portion", it) }
                    startActivity(i); finish()
                }
            }
            card {
                text("Распознанный текст (можно поправить)", 16f, true)
                rawEt = input("Текст", r, number = false).apply { isSingleLine = false; minLines = 4; textSize = 14f }
                button("Разобрать заново", outlined = true) { raw = rawEt?.text?.toString() ?: r; render() }
            }
        }
    }
}
