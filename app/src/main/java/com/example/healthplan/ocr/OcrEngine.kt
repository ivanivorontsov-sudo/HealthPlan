package com.example.healthplan.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Офлайн-OCR на Tesseract (tessdata_fast rus+eng из assets).
 *
 * Почему так (см. RESEARCH.md §10): на снимках плотного текста этикетки режим PSM_AUTO (3) после глобального порога
 * Оцу терял почти весь текст (оставались 3–4 нижние строки). Поэтому:
 * - по умолчанию PSM 6 (единый блок текста) на полутоновом изображении, порог выбирает Tesseract;
 * - несколько проходов (PSM 6 / Оцу, PSM 6 / Саувола, PSM 3 / авто), лучший выбирается по результату разбора;
 * - масштаб подбирается по шагу строк (мелкий текст увеличивается), наклон выравнивается.
 */
object OcrEngine {
    private val LANGS = listOf("rus", "eng")

    data class Pass(val name: String, val psm: Int, val thresholding: Int)
    val PASSES = listOf(
        Pass("блок, Оцу", TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, 0),
        Pass("блок, Саувола", TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, 2),
        Pass("авто", TessBaseAPI.PageSegMode.PSM_AUTO, 0),
    )

    data class Result(val text: String, val passName: String, val passesRun: Int, val scale: Double, val skewDeg: Double, val millis: Long)

    fun ensureTessdata(ctx: Context): File {
        val dir = File(ctx.filesDir, "tessdata").apply { mkdirs() }
        for (l in LANGS) {
            val out = File(dir, "$l.traineddata")
            val size = ctx.assets.openFd("tessdata/$l.traineddata").use { it.length }
            if (!out.exists() || out.length() != size) {
                val tmp = File(dir, "$l.traineddata.tmp")
                ctx.assets.open("tessdata/$l.traineddata").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(out)
            }
        }
        return ctx.filesDir
    }

    /** null — если файл недоступен/не картинка/не хватает памяти (никогда не бросает исключений). */
    fun load(ctx: Context, uri: Uri, maxSide: Int = 3200): Bitmap? = try { loadUnsafe(ctx, uri, maxSide) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    /** Загрузка с поворотом по EXIF; длинная сторона не больше [maxSide] (без лишнего уменьшения в 2 раза). */
    private fun loadUnsafe(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val long = max(bmp.width, bmp.height)
        if (long > maxSide) {
            val k = maxSide.toDouble() / long
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt(), (bmp.height * k).roundToInt(), true)
        }
        val rot = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: 1
        } catch (_: Exception) { 1 }
        val deg = when (rot) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        return if (deg == 0f) bmp else rotate(bmp, deg)
    }

    fun rotate(b: Bitmap, deg: Float): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)

    fun toGray(b: Bitmap): Gray {
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return ImagePrep.fromArgb(px, b.width, b.height)
    }

    fun toBitmap(g: Gray): Bitmap = Bitmap.createBitmap(ImagePrep.toArgb(g), g.w, g.h, Bitmap.Config.ARGB_8888)

    /**
     * Распознавание (вызывать не в UI-потоке). Проходы идут по очереди, пока не найдены все четыре значения
     * (ккал, Б, Ж, У); из выполненных выбирается лучший по [LabelParser.score]. Если лучшему не хватает значений,
     * к тексту добавляется текст следующего по качеству прохода (парсер берёт недостающее оттуда).
     */
    fun recognize(ctx: Context, bmp: Bitmap, progress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val t0 = System.currentTimeMillis()
        val base = ensureTessdata(ctx)
        val prep = ImagePrep.prepare(toGray(bmp))
        val img = toBitmap(prep.gray)
        val api = TessBaseAPI()
        try {
            if (!api.init(base.absolutePath, LANGS.joinToString("+"))) return Result("", "", 0, prep.scale, prep.skewDeg, 0)
            api.setVariable("user_defined_dpi", "300")
            api.setVariable("preserve_interword_spaces", "1")
            val results = mutableListOf<Pair<Pass, String>>()
            for ((i, p) in PASSES.withIndex()) {
                progress(i + 1, PASSES.size)
                api.setPageSegMode(p.psm)
                api.setVariable("thresholding_method", p.thresholding.toString())
                api.setImage(img)
                val t = (api.utF8Text ?: "").trim()
                results += p to t
                api.clear()
                val parsed = LabelParser.parse(t)
                if (parsed.foundDirect >= 4 && parsed.warnings.none { "запятая" in it || "25%" in it }) break
            }
            val ranked = results.sortedByDescending { LabelParser.score(it.second) }
            val best = ranked.first()
            var text = best.second
            if (LabelParser.parse(text).foundDirect < 4 && ranked.size > 1) {
                val extra = ranked[1].second
                val combined = "$text\n\n— другой вариант распознавания —\n$extra"
                if (LabelParser.parse(combined).foundDirect > LabelParser.parse(text).foundDirect ||
                    LabelParser.score(combined) > LabelParser.score(text) + 1.0) text = combined
            }
            return Result(text, best.first.name, results.size, prep.scale, prep.skewDeg, System.currentTimeMillis() - t0)
        } finally { api.recycle() }
    }
}
