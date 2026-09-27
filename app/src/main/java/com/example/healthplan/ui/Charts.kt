package com.example.healthplan.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Кольцо выполнения нормы с процентом в центре. */
class RingView(ctx: Context, var pct: Double, var color: Int, var label: String, var sub: String) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#E0E0E0") }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Ui.MUTED }

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 1.35f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val stroke = w * 0.11f
        val r = w / 2f - stroke
        val cx = w / 2f; val cy = w / 2f
        track.strokeWidth = stroke; arc.strokeWidth = stroke; arc.color = color
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, 0f, 360f, false, track)
        c.drawArc(rect, -90f, (min(pct, 100.0) / 100.0 * 360).toFloat(), false, arc)
        if (pct > 100) { arc.alpha = 120; c.drawArc(rect, -90f, (min(pct - 100, 100.0) / 100.0 * 360).toFloat(), false, arc); arc.alpha = 255 }
        txt.textSize = w * 0.22f; txt.color = color
        c.drawText("${pct.toInt()}%", cx, cy + txt.textSize * 0.35f, txt)
        small.textSize = w * 0.14f; small.color = Color.BLACK; small.isFakeBoldText = true
        c.drawText(label, cx, w + w * 0.12f, small)
        small.textSize = w * 0.11f; small.color = Ui.MUTED; small.isFakeBoldText = false
        c.drawText(sub, cx, w + w * 0.27f, small)
    }
}

/** Столбики % нормы по дням (7 дней), линия 100%. */
class BarChartView(ctx: Context, val labels: List<String>, val values: List<Double>, val colors: List<Int>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(context, 190))
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val bottom = h - Ui.dp(context, 26); val top = Ui.dp(context, 18).toFloat()
        val maxV = max(130.0, values.maxOrNull() ?: 0.0)
        val n = max(1, values.size); val slot = w / n
        fun y(v: Double) = (bottom - (v / maxV) * (bottom - top)).toFloat()
        p.color = Color.parseColor("#90A4AE"); p.strokeWidth = Ui.dp(context, 1).toFloat()
        c.drawLine(0f, y(100.0), w, y(100.0), p)
        p.textSize = Ui.dp(context, 11).toFloat(); p.textAlign = Paint.Align.LEFT; c.drawText("100%", 2f, y(100.0) - 4, p)
        p.textAlign = Paint.Align.CENTER
        values.forEachIndexed { i, v ->
            val x0 = i * slot + slot * 0.2f; val x1 = (i + 1) * slot - slot * 0.2f
            p.color = colors.getOrElse(i) { Ui.PRIMARY }
            c.drawRoundRect(RectF(x0, y(v), x1, bottom), 8f, 8f, p)
            p.color = Color.BLACK; p.textSize = Ui.dp(context, 12).toFloat()
            c.drawText("${v.toInt()}", (x0 + x1) / 2, y(v) - 6, p)
            p.color = Ui.MUTED; p.textSize = Ui.dp(context, 13).toFloat()
            c.drawText(labels.getOrElse(i) { "" }, (x0 + x1) / 2, h - Ui.dp(context, 8), p)
        }
    }
}

/** Простая линейная диаграмма веса. */
class LineChartView(ctx: Context, val labels: List<String>, val values: List<Double>, val target: Double?) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(context, 200))
    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val left = Ui.dp(context, 44).toFloat(); val right = w - Ui.dp(context, 12); val top = Ui.dp(context, 12).toFloat(); val bottom = h - Ui.dp(context, 28)
        val all = values + listOfNotNull(target)
        var lo = all.min() - 1; var hi = all.max() + 1
        if (hi - lo < 2) { lo -= 1; hi += 1 }
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        fun x(i: Int) = if (values.size == 1) (left + right) / 2 else left + i * (right - left) / (values.size - 1)
        p.textSize = Ui.dp(context, 12).toFloat(); p.color = Ui.MUTED; p.textAlign = Paint.Align.RIGHT
        for (k in 0..3) { val v = lo + (hi - lo) * k / 3; c.drawText(Ui.f1(v), left - 6, y(v) + 4, p)
            p.strokeWidth = 1f; c.drawLine(left, y(v), right, y(v), p.apply { alpha = 60 }); p.alpha = 255 }
        target?.let { p.color = Ui.GREEN; p.strokeWidth = Ui.dp(context, 2).toFloat(); c.drawLine(left, y(it), right, y(it), p) }
        p.color = Ui.PRIMARY; p.strokeWidth = Ui.dp(context, 3).toFloat()
        for (i in 1 until values.size) c.drawLine(x(i - 1), y(values[i - 1]), x(i), y(values[i]), p)
        values.forEachIndexed { i, v -> c.drawCircle(x(i), y(v), Ui.dp(context, 4).toFloat(), p) }
        p.color = Ui.MUTED; p.textAlign = Paint.Align.CENTER
        if (labels.isNotEmpty()) { c.drawText(labels.first(), x(0), h - 6, p); if (labels.size > 1) c.drawText(labels.last(), x(values.size - 1), h - 6, p) }
    }
}
