package com.example.healthplan

import com.example.healthplan.ocr.Gray
import com.example.healthplan.ocr.ImagePrep
import com.example.healthplan.ocr.LabelParser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Настоящее распознавание снимка пользователя: та же предобработка (ImagePrep), те же tessdata_fast rus+eng из assets
 * и те же режимы, что в приложении, но через Tesseract CLI. Пропускается, если tesseract не установлен.
 * Отчёт пишется в build/ocr-report.txt.
 */
class OcrPipelineTest {
    private fun tesseractAvailable() = try { ProcessBuilder("tesseract", "--version").redirectErrorStream(true).start().waitFor() == 0 } catch (_: Exception) { false }

    /** Бинарный PGM (P5, оттенки серого) → Gray (без java.awt: в unit-тестах Android его нет). */
    private fun load(res: String): Gray {
        val b = javaClass.classLoader!!.getResourceAsStream(res).use { it.readBytes() }
        var pos = 0
        fun token(): String {
            while (b[pos].toInt().toChar().isWhitespace()) pos++
            val st = pos; while (!b[pos].toInt().toChar().isWhitespace()) pos++
            return String(b, st, pos - st)
        }
        check(token() == "P5"); val w = token().toInt(); val h = token().toInt(); token(); pos++
        return Gray(w, h, b.copyOfRange(pos, pos + w * h))
    }

    private fun save(g: Gray, f: File) = f.outputStream().use { it.write("P5\n${g.w} ${g.h}\n255\n".toByteArray()); it.write(g.px) }

    private fun ocr(f: File, psm: Int, thr: Int): String {
        val p = ProcessBuilder("tesseract", f.path, "-", "--tessdata-dir", File("src/main/assets/tessdata").absolutePath,
            "-l", "rus+eng", "--psm", psm.toString(), "-c", "thresholding_method=$thr", "-c", "user_defined_dpi=300", "-c", "preserve_interword_spaces=1")
            .redirectError(File("build/tesseract-stderr.txt")).start()
        val out = p.inputStream.bufferedReader().readText(); p.waitFor(); return out.trim()
    }

    @Test fun userCreamLabelPhoto() {
        assumeTrue("tesseract CLI не установлен", tesseractAvailable())
        val prep = ImagePrep.prepare(load("ocr/cream_label.pgm"))
        val f = File("build/ocr-cream-prepared.pgm").apply { parentFile.mkdirs() }; save(prep.gray, f)
        val report = StringBuilder("scale=${prep.scale} skew=${prep.skewDeg} pitch=${prep.pitchPx} size=${prep.gray.w}x${prep.gray.h}\n")
        // те же проходы, что в OcrEngine.PASSES: PSM 6 / Оцу, PSM 6 / Саувола, PSM 3 / авто
        val passes = listOf(6 to 0, 6 to 2, 3 to 0).map { (psm, thr) -> Triple(psm, thr, ocr(f, psm, thr)) }
        passes.forEach { (psm, thr, t) ->
            val p = LabelParser.parse(t)
            report.append("\n=== psm=$psm thr=$thr score=${"%.1f".format(LabelParser.score(t))} → Б=${p.protein} Ж=${p.fat} У=${p.carbs} ккал=${p.kcal}\n$t\n")
        }
        val best = passes.maxByOrNull { LabelParser.score(it.third) }!!
        val p = LabelParser.parse(best.third)
        report.append("\nЛУЧШИЙ: psm=${best.first} thr=${best.second} → Б=${p.protein} Ж=${p.fat} У=${p.carbs} ккал=${p.kcal} предупреждения=${p.warnings}\n")
        File("build/ocr-report.txt").writeText(report.toString())
        println(report)
        assertEquals(2.6, p.protein!!, 0.001)
        assertEquals(10.0, p.fat!!, 0.001)
    }

    @Test fun skewEstimateOnSyntheticLines() {
        // горизонтальные «строки» с наклоном 3° вниз вправо
        val w = 600; val h = 400; val px = ByteArray(w * h) { 255.toByte() }
        val t = Math.tan(Math.toRadians(3.0))
        for (line in 0 until 8) for (x in 20 until 580) for (dy in 0 until 10) {
            val y = (40 + line * 40 + x * t).toInt() + dy
            if (y in 0 until h && (x / 12) % 3 != 0) px[y * w + x] = 0
        }
        val g = Gray(w, h, px)
        assertEquals(3.0, ImagePrep.estimateSkew(ImagePrep.binarize(g)), 0.35)
        val straight = ImagePrep.rotate(g, 3.0)
        assertEquals(0.0, ImagePrep.estimateSkew(ImagePrep.binarize(straight)), 0.35)
        assertEquals(40.0, ImagePrep.estimatePitch(ImagePrep.binarize(straight))!!, 3.0)
    }
}
