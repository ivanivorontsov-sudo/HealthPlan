package com.example.healthplan.ui

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.healthplan.*
import com.example.healthplan.core.Calc
import com.example.healthplan.food.Food
import com.example.healthplan.food.N
import com.example.healthplan.food.Norm
import com.example.healthplan.food.NormStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DiaryTab(a: MainActivity) : Tab(a) {
    companion object { var date: LocalDate = LocalDate.now(); var chartNorm = 0 }

    override fun build(): View {
        val s = a.store; val calc = Calc.of(s.profile)
        val ds = date.toString()
        val slots = s.mealSlots(); val targets = Day.mealTargets(calc, s)
        val entries = s.diaryFor(ds)
        val col = Ui.column(a)
        col.h1("Дневник питания")
        col.row {
            button("◀", outlined = true, weight = 1f) { date = date.minusDays(1); a.refresh() }
            addView(TextView(context).apply {
                text = if (date == LocalDate.now()) "Сегодня" else date.format(DateTimeFormatter.ofPattern("d MMM, EE", Ui.RU))
                textSize = 19f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 2f))
            button("▶", outlined = true, weight = 1f) { date = date.plusDays(1); a.refresh() }
        }

        val tot = DayNutrition.totals(s, ds); val m = calc.macros
        col.card {
            text("Выполнение норм за день", 20f, true)
            normRings(DayNutrition.percents(s, calc, ds), listOf(
                "${Ui.f0(tot.kcal)}/${Ui.f0(calc.target.kcal)}", "${Ui.f0(tot.p)}/${Ui.f0(m.proteinG)} г", "${Ui.f0(tot.f)}/${Ui.f0(m.fatG)} г",
                "${Ui.f0(tot.c)}/${Ui.f0(m.carbsG)} г", "${Ui.f0(tot.fib)}/${Ui.f0(calc.fiberG)} г", "${Ui.f1(s.waterMl(ds) / 1000.0)}/${Ui.f1(calc.waterMl / 1000.0)} л"))
            row {
                button("Вода +250", weight = 1f) { s.addWater(ds, 250); a.refresh() }
                button("+500", weight = 1f) { s.addWater(ds, 500); a.refresh() }
                button("−250", outlined = true, weight = 1f) { s.addWater(ds, -250); a.refresh() }
            }
        }

        slots.forEachIndexed { i, sl ->
            val list = entries.filter { it.meal == i }
            val mt = list.fold(N.ZERO) { acc, e -> acc + N(e.kcal100, e.p100, e.f100, e.c100, e.fib100) * (e.grams / 100.0) }
            col.card {
                row {
                    addView(TextView(context).apply { text = sl.name; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
                    val pct = if (targets[i].kcal > 0) mt.kcal / targets[i].kcal * 100 else 0.0
                    addView(TextView(context).apply { text = "${Ui.f0(mt.kcal)} / ${Ui.f0(targets[i].kcal)} ккал"; textSize = 17f
                        setTextColor(if (list.isEmpty()) Ui.MUTED else Ui.color(NormStatus.of(Norm.KCAL, pct))) })
                }
                muted("Б ${Ui.f0(mt.p)}/${Ui.f0(targets[i].p)} · Ж ${Ui.f0(mt.f)}/${Ui.f0(targets[i].f)} · У ${Ui.f0(mt.c)}/${Ui.f0(targets[i].c)} г", 14f)
                list.forEach { e ->
                    val n = N(e.kcal100, e.p100, e.f100, e.c100, e.fib100) * (e.grams / 100.0)
                    val r = row {
                        addView(TextView(context).apply { text = e.name; textSize = 17f }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(TextView(context).apply { text = "${Ui.g(e.grams)} г"; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                    }
                    val sub = text("${Ui.f0(n.kcal)} ккал · Б ${Ui.f1(n.p)} · Ж ${Ui.f1(n.f)} · У ${Ui.f1(n.c)} · Кл ${Ui.f1(n.fib)}", 14f, color = Ui.MUTED, topDp = 0)
                    val edit = View.OnClickListener {
                        val f = Food(e.foodId, e.name, "", N(e.kcal100, e.p100, e.f100, e.c100, e.fib100))
                        Dialogs.grams(a, f, e.grams, { g -> s.updateDiary(e.copy(grams = g)); a.refresh() }, { s.deleteDiary(e.id); a.refresh() })
                    }
                    r.setOnClickListener(edit); sub.setOnClickListener(edit)
                    r.minimumHeight = Ui.dp(a, 44)
                }
                row {
                    button("+ Продукт", weight = 1f) {
                        a.startActivity(Intent(a, FoodPickerActivity::class.java).putExtra("meal", i).putExtra("date", ds))
                    }
                    button("Рассчитать граммы", outlined = true, weight = 1f) {
                        a.startActivity(Intent(a, MealBuilderActivity::class.java).putExtra("meal", i).putExtra("date", ds))
                    }
                }
            }
        }

        // --- Недельная история
        col.card {
            text("История за 7 дней, % нормы", 20f, true)
            val names = Norm.values()
            val chips = com.google.android.material.chip.ChipGroup(context).apply { isSingleSelection = true }
            names.forEachIndexed { idx, n ->
                chips.addView(com.google.android.material.chip.Chip(context).apply {
                    text = n.title; isCheckable = true; isChecked = idx == chartNorm; textSize = 15f
                    setOnClickListener { chartNorm = idx; a.refresh() }
                })
            }
            addView(chips, lp(topDp = 6))
            val days = (6 downTo 0).map { date.minusDays(it.toLong()) }
            val vals = days.map { DayNutrition.percents(s, calc, it.toString())[chartNorm] }
            addView(BarChartView(context, days.map { it.format(DateTimeFormatter.ofPattern("EE", Ui.RU)) }, vals,
                vals.map { Ui.color(NormStatus.of(names[chartNorm], it)) }), lp(topDp = 8))
        }
        col.card {
            text("Итого за день", 20f, true)
            kv("Калории", "${Ui.f0(tot.kcal)} ккал")
            kv("Белки", "${Ui.f1(tot.p)} г"); kv("Жиры", "${Ui.f1(tot.f)} г"); kv("Углеводы (усвояемые)", "${Ui.f1(tot.c)} г"); kv("Клетчатка", "${Ui.f1(tot.fib)} г")
        }
        return Ui.scroll(a, col)
    }
}
