package com.example.healthplan.ocr

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Полутоновое изображение 0..255 (0 — чёрный). Чистый Kotlin — одинаково работает на Android и в unit-тестах. */
class Gray(val w: Int, val h: Int, val px: ByteArray) {
    operator fun get(x: Int, y: Int) = px[y * w + x].toInt() and 0xff
}

/**
 * Предобработка фото этикетки для Tesseract:
 * оттенки серого → растяжение контраста → оценка шага строк и масштабирование (мелкий текст увеличивается,
 * огромный — уменьшается) → оценка и исправление наклона (проекционный профиль) → при необходимости адаптивный порог.
 * Бинаризацию по умолчанию делает сам Tesseract (Оцу или Сауволы) — LSTM лучше работает с полутоновым входом.
 */
object ImagePrep {
    /** Желаемый шаг строк текста, px. Эксперименты с tessdata_fast: 40–90 px — лучше всего. */
    const val TARGET_PITCH = 56.0
    const val MAX_PIXELS = 7_000_000
    const val MAX_SIDE = 3200

    data class Prepared(val gray: Gray, val scale: Double, val skewDeg: Double, val pitchPx: Double?)

    fun fromArgb(argb: IntArray, w: Int, h: Int): Gray {
        val out = ByteArray(w * h)
        for (i in 0 until w * h) {
            val c = argb[i]
            out[i] = (((c shr 16 and 0xff) * 299 + (c shr 8 and 0xff) * 587 + (c and 0xff) * 114) / 1000).toByte()
        }
        return Gray(w, h, out)
    }

    fun toArgb(g: Gray): IntArray = IntArray(g.w * g.h) { val v = g.px[it].toInt() and 0xff; (0xff shl 24) or (v shl 16) or (v shl 8) or v }

    /** Растяжение контраста по перцентилям 1% / 99%. */
    fun stretch(g: Gray): Gray {
        val hist = IntArray(256); for (b in g.px) hist[b.toInt() and 0xff]++
        val n = g.px.size; var lo = 0; var hi = 255; var acc = 0
        while (lo < 255 && acc + hist[lo] < n / 100) { acc += hist[lo]; lo++ }
        acc = 0; while (hi > 0 && acc + hist[hi] < n / 100) { acc += hist[hi]; hi-- }
        if (hi - lo < 8) return g
        val lut = ByteArray(256) { min(255, max(0, (it - lo) * 255 / (hi - lo))).toByte() }
        return Gray(g.w, g.h, ByteArray(n) { lut[g.px[it].toInt() and 0xff] })
    }

    /** Масштабирование: билинейно при увеличении, усреднением по площади при уменьшении. */
    fun scale(g: Gray, nw: Int, nh: Int): Gray {
        if (nw == g.w && nh == g.h) return g
        val out = ByteArray(nw * nh)
        val sx = g.w.toDouble() / nw; val sy = g.h.toDouble() / nh
        if (sx > 1.0 && sy > 1.0) {
            for (y in 0 until nh) {
                val y0 = (y * sy).toInt(); val y1 = min(g.h, max(y0 + 1, ((y + 1) * sy).toInt()))
                for (x in 0 until nw) {
                    val x0 = (x * sx).toInt(); val x1 = min(g.w, max(x0 + 1, ((x + 1) * sx).toInt()))
                    var s = 0; for (yy in y0 until y1) { val row = yy * g.w; for (xx in x0 until x1) s += g.px[row + xx].toInt() and 0xff }
                    out[y * nw + x] = (s / ((y1 - y0) * (x1 - x0))).toByte()
                }
            }
        } else {
            for (y in 0 until nh) {
                val fy = max(0.0, (y + 0.5) * sy - 0.5); val y0 = min(g.h - 1, fy.toInt()); val y1 = min(g.h - 1, y0 + 1); val dy = fy - y0
                for (x in 0 until nw) {
                    val fx = max(0.0, (x + 0.5) * sx - 0.5); val x0 = min(g.w - 1, fx.toInt()); val x1 = min(g.w - 1, x0 + 1); val dx = fx - x0
                    val v = (g[x0, y0] * (1 - dx) + g[x1, y0] * dx) * (1 - dy) + (g[x0, y1] * (1 - dx) + g[x1, y1] * dx) * dy
                    out[y * nw + x] = v.roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return Gray(nw, nh, out)
    }

    /**
     * Адаптивный порог (Сауволы) по сетке плиток с билинейной интерполяцией порога — не требует интегрального
     * изображения, поэтому экономен по памяти. Возвращает 0/255.
     */
    fun binarize(g: Gray, tile: Int = max(16, min(g.w, g.h) / 24), k: Double = 0.2): Gray {
        val tx = (g.w + tile - 1) / tile; val ty = (g.h + tile - 1) / tile
        val thr = DoubleArray(tx * ty)
        for (j in 0 until ty) for (i in 0 until tx) {
            // окно 3×3 плитки вокруг текущей
            val x0 = max(0, (i - 1) * tile); val x1 = min(g.w, (i + 2) * tile)
            val y0 = max(0, (j - 1) * tile); val y1 = min(g.h, (j + 2) * tile)
            var s = 0L; var s2 = 0L; var n = 0
            var y = y0; while (y < y1) { var x = x0; val row = y * g.w; while (x < x1) { val v = g.px[row + x].toInt() and 0xff; s += v; s2 += v * v; n++; x += 2 }; y += 2 }
            val m = s.toDouble() / n; val sd = sqrt(max(0.0, s2.toDouble() / n - m * m))
            thr[j * tx + i] = m * (1 + k * (sd / 128.0 - 1))
        }
        val out = ByteArray(g.w * g.h)
        for (y in 0 until g.h) {
            val fy = ((y + 0.5) / tile - 0.5).coerceIn(0.0, (ty - 1).toDouble()); val j0 = fy.toInt(); val j1 = min(ty - 1, j0 + 1); val dy = fy - j0
            for (x in 0 until g.w) {
                val fx = ((x + 0.5) / tile - 0.5).coerceIn(0.0, (tx - 1).toDouble()); val i0 = fx.toInt(); val i1 = min(tx - 1, i0 + 1); val dx = fx - i0
                val t = (thr[j0 * tx + i0] * (1 - dx) + thr[j0 * tx + i1] * dx) * (1 - dy) + (thr[j1 * tx + i0] * (1 - dx) + thr[j1 * tx + i1] * dx) * dy
                out[y * g.w + x] = if ((g.px[y * g.w + x].toInt() and 0xff) > t) 255.toByte() else 0
            }
        }
        return Gray(g.w, g.h, out)
    }

    /** Координаты «чернильных» пикселей бинарного изображения (с прореживанием до ~maxPts). */
    private fun inkPoints(bin: Gray, maxPts: Int = 120_000): Pair<IntArray, IntArray> {
        var n = 0; for (b in bin.px) if (b.toInt() == 0) n++
        val step = max(1, n / maxPts)
        val xs = IntArray(n / step + 1); val ys = IntArray(n / step + 1); var k = 0; var c = 0
        for (y in 0 until bin.h) for (x in 0 until bin.w) if (bin.px[y * bin.w + x].toInt() == 0) { if (c++ % step == 0 && k < xs.size) { xs[k] = x; ys[k] = y; k++ } }
        return xs.copyOf(k) to ys.copyOf(k)
    }

    /** Наклон строк в градусах (положительный — строки идут вниз вправо), по максимуму «резкости» проекционного профиля. */
    fun estimateSkew(bin: Gray, maxDeg: Double = 8.0): Double {
        val (xs, ys) = inkPoints(bin)
        if (xs.size < 200) return 0.0
        fun score(deg: Double): Double {
            val t = Math.tan(Math.toRadians(deg)); val off = (abs(t) * bin.w).toInt() + 2
            val hist = IntArray(bin.h + 2 * off)
            for (i in xs.indices) { val yy = (ys[i] - xs[i] * t).toInt() + off; if (yy in hist.indices) hist[yy]++ }
            var s = 0.0; for (i in 1 until hist.size) { val d = (hist[i] - hist[i - 1]).toDouble(); s += d * d }
            return s
        }
        var best = 0.0; var bestS = score(0.0)
        var a = -maxDeg; while (a <= maxDeg + 1e-9) { val s = score(a); if (s > bestS * 1.0001) { bestS = s; best = a }; a += 0.5 }
        var b = best - 0.4; val end = best + 0.4
        while (b <= end + 1e-9) { val s = score(b); if (s > bestS * 1.0001) { bestS = s; best = b }; b += 0.1 }
        return (best * 10).roundToInt() / 10.0
    }

    /** Шаг строк текста (px) по автокорреляции горизонтального профиля; null — если строки не выражены. */
    fun estimatePitch(bin: Gray): Double? {
        val prof = DoubleArray(bin.h)
        for (y in 0 until bin.h) { var c = 0; val row = y * bin.w; for (x in 0 until bin.w) if (bin.px[row + x].toInt() == 0) c++; prof[y] = c.toDouble() / bin.w }
        val mean = prof.average(); for (i in prof.indices) prof[i] -= mean
        val var0 = prof.sumOf { it * it }; if (var0 <= 1e-9) return null
        val maxLag = min(bin.h / 2, 400)
        val ac = DoubleArray(maxLag + 1)
        for (lag in 4..maxLag) { var s = 0.0; for (i in 0 until bin.h - lag) s += prof[i] * prof[i + lag]; ac[lag] = s / var0 }
        // первый выраженный локальный максимум
        var bestLag = -1; var bestV = 0.15
        for (lag in 6 until maxLag) if (ac[lag] > ac[lag - 1] && ac[lag] >= ac[lag + 1] && ac[lag] > bestV) {
            if (bestLag < 0 || ac[lag] > bestV * 1.25) { bestLag = lag; bestV = ac[lag] }
            if (bestLag > 0 && lag > bestLag * 1.6) break
        }
        return if (bestLag > 0) bestLag.toDouble() else null
    }

    /** Поворот на угол (градусы, против часовой — положительный) с белым фоном, билинейно. */
    fun rotate(g: Gray, deg: Double): Gray {
        if (abs(deg) < 0.05) return g
        val r = Math.toRadians(deg); val c = cos(r); val s = sin(r)
        val nw = (abs(g.w * c) + abs(g.h * s)).roundToInt(); val nh = (abs(g.w * s) + abs(g.h * c)).roundToInt()
        val out = ByteArray(nw * nh); val cx = g.w / 2.0; val cy = g.h / 2.0; val ncx = nw / 2.0; val ncy = nh / 2.0
        for (y in 0 until nh) for (x in 0 until nw) {
            val dx = x - ncx; val dy = y - ncy
            val sx = c * dx - s * dy + cx; val sy = s * dx + c * dy + cy
            val x0 = Math.floor(sx).toInt(); val y0 = Math.floor(sy).toInt()
            out[y * nw + x] = if (x0 < 0 || y0 < 0 || x0 >= g.w - 1 || y0 >= g.h - 1) 255.toByte() else {
                val fx = sx - x0; val fy = sy - y0
                ((g[x0, y0] * (1 - fx) + g[x0 + 1, y0] * fx) * (1 - fy) + (g[x0, y0 + 1] * (1 - fx) + g[x0 + 1, y0 + 1] * fx) * fy).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return Gray(nw, nh, out)
    }

    /** Полная подготовка: контраст → масштаб по шагу строк → выравнивание наклона. */
    fun prepare(src: Gray, targetPitch: Double = TARGET_PITCH): Prepared {
        var g = stretch(src)
        // оценка на уменьшенной копии (≤ 900 px) — быстро
        val probeScale = min(1.0, 900.0 / max(g.w, g.h))
        val probe = if (probeScale < 1.0) scale(g, max(1, (g.w * probeScale).toInt()), max(1, (g.h * probeScale).toInt())) else g
        val pb = binarize(probe)
        val skew = estimateSkew(pb)
        val pitchProbe = estimatePitch(if (abs(skew) >= 0.5) binarize(rotate(probe, skew)) else pb)
        val pitch = pitchProbe?.div(probeScale)
        var k = when {
            pitch == null -> if (max(g.w, g.h) < 1400) 1400.0 / max(g.w, g.h) else 1.0
            pitch < targetPitch * 0.75 -> targetPitch / pitch       // мелкий текст — увеличить
            pitch > targetPitch * 2.0 -> targetPitch * 1.4 / pitch  // очень крупный — уменьшить (быстрее, не хуже)
            else -> 1.0
        }
        k = min(k, 3.0)
        // ограничения по размеру
        k = min(k, MAX_SIDE.toDouble() / max(g.w, g.h))
        k = min(k, sqrt(MAX_PIXELS.toDouble() / (g.w.toDouble() * g.h)))
        if (abs(k - 1.0) > 0.05) g = scale(g, max(1, (g.w * k).roundToInt()), max(1, (g.h * k).roundToInt())) else k = 1.0
        // skew > 0 — строки идут вниз вправо: поворачиваем против часовой стрелки
        if (abs(skew) >= 0.5) g = rotate(g, skew)
        return Prepared(g, k, skew, pitch)
    }
}
