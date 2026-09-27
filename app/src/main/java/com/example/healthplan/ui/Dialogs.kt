package com.example.healthplan.ui

import android.app.TimePickerDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import com.example.healthplan.food.Food
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Dialogs {
    fun time(ctx: Context, min: Int, onPick: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, min / 60, min % 60, true).show()
    }

    fun info(ctx: Context, title: String, msg: String) {
        MaterialAlertDialogBuilder(ctx).setTitle(title).setMessage(msg).setPositiveButton("Понятно", null).show()
    }

    fun confirm(ctx: Context, title: String, msg: String, yes: String = "Да", onYes: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(title).setMessage(msg).setPositiveButton(yes) { _, _ -> onYes() }.setNegativeButton("Отмена", null).show()
    }

    /** Ввод граммов с живым расчётом ккал/БЖУ. [onDelete] — показывает кнопку «Удалить». */
    fun grams(ctx: Context, food: Food, initial: Double?, onOk: (Double) -> Unit, onDelete: (() -> Unit)? = null) {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(ctx, 20); setPadding(p, Ui.dp(ctx, 8), p, 0) }
        box.muted("На 100 г: ${Ui.f0(food.per100.kcal)} ккал · Б ${Ui.f1(food.per100.p)} · Ж ${Ui.f1(food.per100.f)} · У ${Ui.f1(food.per100.c)} · Кл ${Ui.f1(food.per100.fib)}")
        val et = box.input("Граммы", initial?.let { Ui.g(it) } ?: (food.portionG?.let { Ui.g(it) } ?: "100"))
        val res = box.text("", 19f, bold = true, topDp = 10)
        val quick = box.row { }
        listOfNotNull(50.0, 100.0, 150.0, 200.0, food.portionG).distinct().forEach { g ->
            quick.button(if (g == food.portionG) "порция ${Ui.g(g)}" else "${Ui.g(g)} г", outlined = true, topDp = 4, weight = 1f) { et.setText(Ui.g(g)) }
        }
        fun upd() {
            val g = et.num() ?: 0.0; val n = food.nutrients(g)
            res.text = "${Ui.f0(n.kcal)} ккал\nБ ${Ui.f1(n.p)} г · Ж ${Ui.f1(n.f)} г · У ${Ui.f1(n.c)} г · Кл ${Ui.f1(n.fib)} г"
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = upd()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        upd()
        val b = MaterialAlertDialogBuilder(ctx).setTitle(food.name).setView(box)
            .setPositiveButton("Сохранить") { _, _ -> et.num()?.takeIf { it > 0 }?.let(onOk) }
            .setNegativeButton("Отмена", null)
        if (onDelete != null) b.setNeutralButton("Удалить") { _, _ -> onDelete() }
        b.show()
    }
}
