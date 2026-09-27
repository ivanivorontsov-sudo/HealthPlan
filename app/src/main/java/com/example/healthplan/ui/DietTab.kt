package com.example.healthplan.ui

import android.content.Intent
import android.view.View
import android.widget.Toast
import com.example.healthplan.*
import com.example.healthplan.core.Calc
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.DiaryEntry
import com.example.healthplan.food.MealPlanner

class DietTab(a: MainActivity) : Tab(a) {
    override fun build(): View {
        val s = a.store; val p = s.profile; val calc = Calc.of(p)
        val slots = s.mealSlots(); val targets = Day.mealTargets(calc, s)
        val db = App.foods(a); val restr = s.restrictions(p)
        val col = Ui.column(a)
        col.h1("Режим питания")
        col.muted("Подъём ${MealSchedule.fmt(p.wakeMin)} · отбой ${MealSchedule.fmt(p.sleepMin)} · ${p.mealsCount} приёма(ов) пищи · ${Ui.f0(calc.target.kcal)} ккал/сут")
        col.card {
            text("Расписание приёмов пищи", 20f, true)
            muted("Завтрак ≈ через 45 мин после подъёма, последний приём — за 2–3 ч до сна, остальные — равномерно (рекомендация). Нажмите на время, чтобы изменить.", 14f)
            slots.forEachIndexed { i, sl ->
                val t = targets[i]
                row {
                    button(MealSchedule.fmt(sl.timeMin), outlined = true, topDp = 6) {
                        Dialogs.time(a, sl.timeMin) { m -> s.mealTimeOverrides = s.mealTimeOverrides + (sl.index to m); a.refresh() }
                    }.layoutParams = android.widget.LinearLayout.LayoutParams(Ui.dp(a, 100), -2)
                    val box = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(Ui.dp(a, 12), 0, 0, 0) }
                    box.text("${sl.name} · ${(sl.share * 100).toInt()}%", 18f, true, topDp = 0)
                    box.text("${Ui.f0(t.kcal)} ккал · Б ${Ui.f0(t.p)} · Ж ${Ui.f0(t.f)} · У ${Ui.f0(t.c)} г", 15f, color = Ui.MUTED, topDp = 0)
                    addView(box, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                }
            }
            if (s.mealTimeOverrides.isNotEmpty()) button("Сбросить время по подъёму и отбою", outlined = true) { s.mealTimeOverrides = emptyMap(); a.refresh() }
        }

        col.h2("Примеры блюд")
        col.muted("Подобраны из встроенной базы (USDA) с учётом ограничений; граммы рассчитаны под калорийность и БЖУ приёма.", 14f)
        col.row {
            button("Сгенерировать заново", weight = 1f) { s.menuSeed = s.menuSeed + 101; a.refresh() }
            button("Рассчитать граммы", outlined = true, weight = 1f) { a.startActivity(Intent(a, MealBuilderActivity::class.java)) }
        }
        slots.forEachIndexed { i, sl ->
            val sug = MealPlanner.suggest(db.builtIn, MealPlanner.kindOf(sl.name), targets[i], restr, s.menuSeed * 31 + i * 7 + seedFor(i))
            col.card {
                text("${sl.name} · ${MealSchedule.fmt(sl.timeMin)}", 19f, true)
                if (sug.items.isEmpty()) muted("Не удалось подобрать продукты с текущими ограничениями")
                sug.items.forEach { (f, g) ->
                    val n = f.nutrients(g)
                    row {
                        addView(android.widget.TextView(context).apply { text = f.name; textSize = 17f }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                        addView(android.widget.TextView(context).apply { text = "${Ui.g(g)} г"; textSize = 19f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                    }
                    text("${Ui.f0(n.kcal)} ккал · Б ${Ui.f1(n.p)} · Ж ${Ui.f1(n.f)} · У ${Ui.f1(n.c)}", 14f, color = Ui.MUTED, topDp = 0)
                }
                val t = sug.totals
                divider()
                text("Итого: ${Ui.f0(t.kcal)} из ${Ui.f0(targets[i].kcal)} ккал · Б ${Ui.f0(t.p)} · Ж ${Ui.f0(t.f)} · У ${Ui.f0(t.c)} г", 16f, true)
                row {
                    button("В дневник", weight = 1f) {
                        val date = Day.today()
                        sug.items.forEach { (f, g) ->
                            s.addDiary(DiaryEntry(System.nanoTime(), date, i, f.id, f.name, g, f.per100.kcal, f.per100.p, f.per100.f, f.per100.c, f.per100.fib))
                        }
                        Toast.makeText(a, "Добавлено в дневник: ${sl.name}", Toast.LENGTH_SHORT).show()
                    }
                    button("Другой вариант", outlined = true, weight = 1f) { bump(i); a.refresh() }
                }
            }
        }
        return Ui.scroll(a, col)
    }

    private fun seedFor(i: Int) = a.getSharedPreferences("menu", 0).getInt("m$i", 0)
    private fun bump(i: Int) = a.getSharedPreferences("menu", 0).edit().putInt("m$i", seedFor(i) + 1).apply()
}
