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
import kotlin.math.min

/** Офлайн-OCR на Tesseract (tessdata_fast rus+eng из assets). */
object OcrEngine {
    private val LANGS = listOf("rus", "eng")

    fun ensureTessdata(ctx: Context): File {
        val dir = File(ctx.filesDir, "tessdata").apply { mkdirs() }
        for (l in LANGS) {
            val out = File(dir, "$l.traineddata")
            val size = ctx.assets.openFd("tessdata/$l.traineddata").use { it.length }
            if (!out.exists() || out.length() != size) {
                ctx.assets.open("tessdata/$l.traineddata").use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
        }
        return ctx.filesDir
    }

    /** Загрузка с уменьшением (≤ maxSide) и поворотом по EXIF. */
    /** null — если файл недоступен/не картинка/не хватает памяти (никогда не бросает исключений). */
    fun load(ctx: Context, uri: Uri, maxSide: Int = 2400): Bitmap? = try { loadUnsafe(ctx, uri, maxSide) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    private fun loadUnsafe(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: 1
        } catch (_: Exception) { 1 }
        val deg = when (rot) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        return if (deg == 0f) bmp else rotate(bmp, deg)
    }

    fun rotate(b: Bitmap, deg: Float): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)

    /** Оттенки серого + растяжение контраста + порог Оцу; масштаб так, чтобы ширина текста была читаема (~1600–2400 px). */
    fun preprocess(src: Bitmap): Bitmap {
        val longSide = max(src.width, src.height)
        val scale = when { longSide < 1400 -> 1600f / longSide; longSide > 2600 -> 2400f / longSide; else -> 1f }
        val b = if (scale != 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(px.size); val hist = IntArray(256)
        for (i in px.indices) {
            val c = px[i]; val g = ((c shr 16 and 0xff) * 299 + (c shr 8 and 0xff) * 587 + (c and 0xff) * 114) / 1000
            gray[i] = g; hist[g]++
        }
        // растяжение контраста по 1% / 99% перцентилям
        var lo = 0; var hi = 255; var acc = 0; val n = px.size
        while (lo < 255 && acc + hist[lo] < n / 100) { acc += hist[lo]; lo++ }
        acc = 0; while (hi > 0 && acc + hist[hi] < n / 100) { acc += hist[hi]; hi-- }
        val range = max(1, hi - lo)
        val hist2 = IntArray(256)
        for (i in gray.indices) { val v = min(255, max(0, (gray[i] - lo) * 255 / range)); gray[i] = v; hist2[v]++ }
        val t = otsu(hist2, n)
        for (i in gray.indices) { val v = if (gray[i] > t) 255 else 0; px[i] = (0xff shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0; for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0; var best = 0.0; var th = 127
        for (i in 0..255) {
            wB += hist[i]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; th = i }
        }
        return th
    }

    /** Распознавание (вызывать не в UI-потоке). */
    fun recognize(ctx: Context, bmp: Bitmap, binarize: Boolean = true): String {
        val base = ensureTessdata(ctx)
        val api = TessBaseAPI()
        try {
            if (!api.init(base.absolutePath, LANGS.joinToString("+"))) return ""
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(if (binarize) preprocess(bmp) else bmp)
            return api.getUTF8Text() ?: ""
        } finally { api.recycle() }
    }
}
