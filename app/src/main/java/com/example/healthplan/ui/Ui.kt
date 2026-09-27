package com.example.healthplan.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.healthplan.core.Level
import com.example.healthplan.food.Status
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/** Небольшой DSL для построения экранов кодом (крупный шрифт, карточки, без «простыней» текста). */
object Ui {
    val GREEN = Color.parseColor("#2E7D32"); val YELLOW = Color.parseColor("#F9A825"); val RED = Color.parseColor("#C62828")
    val PRIMARY = Color.parseColor("#00695C"); val MUTED = Color.parseColor("#5F6B6A")
    val RU: Locale = Locale("ru")

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun color(l: Level) = when (l) { Level.OK -> GREEN; Level.WARN -> YELLOW; Level.BAD -> RED }
    fun color(s: Status) = when (s) { Status.GREEN -> GREEN; Status.YELLOW -> YELLOW; Status.RED -> RED }

    fun f0(x: Double) = String.format(RU, "%,.0f", x).replace('\u00A0', ' ')
    fun f1(x: Double) = String.format(RU, "%.1f", x)
    fun g(x: Double) = if (x % 1.0 == 0.0) f0(x) else f1(x)

    fun column(ctx: Context, padDp: Int = 16) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, padDp); setPadding(p, p, p, dp(ctx, 96))
    }

    fun scroll(ctx: Context, content: View) = ScrollView(ctx).apply { isFillViewport = true; addView(content) }
}

fun LinearLayout.lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, topDp: Int = 0, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight).apply { topMargin = Ui.dp(context, topDp) }

fun LinearLayout.text(s: CharSequence, sp: Float = 17f, bold: Boolean = false, color: Int? = null, topDp: Int = 4, gravity: Int = Gravity.START): TextView =
    TextView(context).apply {
        text = s; textSize = sp; this.gravity = gravity
        if (bold) setTypeface(typeface, Typeface.BOLD)
        color?.let { setTextColor(it) }
        setLineSpacing(0f, 1.15f)
    }.also { addView(it, lp(topDp = topDp)) }

fun LinearLayout.h1(s: String) = text(s, 26f, true, topDp = 8)
fun LinearLayout.h2(s: String) = text(s, 21f, true, topDp = 14)
fun LinearLayout.muted(s: CharSequence, sp: Float = 15f) = text(s, sp, color = Ui.MUTED)

fun LinearLayout.card(topDp: Int = 12, stroke: Int? = null, block: LinearLayout.() -> Unit): MaterialCardView {
    val c = MaterialCardView(context).apply {
        radius = Ui.dp(context, 18).toFloat(); cardElevation = Ui.dp(context, 2).toFloat()
        if (stroke != null) { strokeColor = stroke; strokeWidth = Ui.dp(context, 3) }
        useCompatPadding = false
    }
    val inner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(context, 16); setPadding(p, p, p, p) }
    inner.block(); c.addView(inner); addView(c, lp(topDp = topDp)); return c
}

fun LinearLayout.row(block: LinearLayout.() -> Unit): LinearLayout {
    val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    r.block(); addView(r, lp(topDp = 4)); return r
}

/** Строка «название — значение» крупно; [sub] — пояснение (формула/категория). */
fun LinearLayout.kv(label: String, value: String, sub: String? = null, valueColor: Int? = null) {
    val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val l = TextView(context).apply { text = label; textSize = 17f }
    val v = TextView(context).apply { text = value; textSize = 21f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END; valueColor?.let { setTextColor(it) } }
    r.addView(l, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(v, LinearLayout.LayoutParams(-2, -2))
    addView(r, lp(topDp = 10))
    if (sub != null) text(sub, 14f, color = Ui.MUTED, topDp = 0)
}

fun LinearLayout.divider() = View(context).apply { setBackgroundColor(Color.parseColor("#1F000000")) }.also { addView(it, lp(h = Ui.dp(context, 1), topDp = 10)) }

fun LinearLayout.button(label: String, outlined: Boolean = false, topDp: Int = 10, weight: Float = 0f, onClick: () -> Unit): MaterialButton {
    val b = if (outlined) MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle) else MaterialButton(context)
    b.text = label; b.textSize = 17f; b.isAllCaps = false; b.minHeight = Ui.dp(context, 56)
    b.cornerRadius = Ui.dp(context, 14)
    b.setOnClickListener { onClick() }
    addView(b, if (weight > 0) lp(w = 0, weight = weight, topDp = topDp).apply { marginEnd = Ui.dp(context, 6) } else lp(topDp = topDp))
    return b
}

fun LinearLayout.input(hint: String, value: String = "", number: Boolean = true, decimal: Boolean = true, topDp: Int = 8): TextInputEditText {
    val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { this.hint = hint }
    val et = TextInputEditText(til.context).apply {
        setText(value); textSize = 18f
        inputType = if (number) (InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    til.addView(et); addView(til, lp(topDp = topDp)); return et
}

fun LinearLayout.check(label: String, checked: Boolean, onChange: ((Boolean) -> Unit)? = null): CheckBox =
    CheckBox(context).apply { text = label; textSize = 17f; isChecked = checked; minHeight = Ui.dp(context, 48)
        setOnCheckedChangeListener { _, b -> onChange?.invoke(b) } }.also { addView(it, lp(topDp = 2)) }

fun EditText.num(): Double? = text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()

fun colorChip(ctx: Context, color: Int): ColorStateList = ColorStateList.valueOf(color)
