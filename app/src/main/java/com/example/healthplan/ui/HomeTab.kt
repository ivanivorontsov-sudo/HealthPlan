package com.example.healthplan.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.healthplan.*
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.core.Calc
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.food.Norm
import com.example.healthplan.food.NormStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Сетка колец выполнения норм за день. */
fun LinearLayout.normRings(pcts: List<Double>, subs: List<String>) {
    val grid = GridLayout(context).apply { columnCount = 3 }
    Norm.values().forEachIndexed { i, n ->
        val pct = pcts.getOrElse(i) { 0.0 }
        val v = RingView(context, pct, Ui.color(NormStatus.of(n, pct)), n.title, subs.getOrElse(i) { "" })
        val p = GridLayout.LayoutParams(GridLayout.spec(i / 3), GridLayout.spec(i % 3, 1f)).apply {
            width = 0; val m = Ui.dp(context, 6); setMargins(m, m, m, m)
        }
        grid.addView(v, p)
    }
    addView(grid, lp(topDp = 8))
    muted("Зелёный — в норме, жёлтый — близко к границе, красный — сильный недобор или перебор (оценка за весь день).", 13f)
}

class HomeTab(a: MainActivity) : Tab(a) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null

    override fun build(): View {
        val s = a.store; val p = s.profile; val calc = Calc.of(p)
        val today = LocalDate.now(); val date = today.toString()
        val col = Ui.column(a)
        col.h1("Здоровье и режим")
        col.muted(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Ui.RU)).replaceFirstChar { it.uppercase() })

        if (!p.filled) col.card(stroke = Ui.YELLOW) {
            text("Заполните профиль, чтобы рассчитать нормы и режим", 18f, true)
            button("Заполнить профиль") { a.startActivity(Intent(a, ProfileActivity::class.java)) }
        }
        val missing = PermissionsActivity.missing(a)
        if (missing.isNotEmpty()) col.card(stroke = Ui.RED) {
            text("⚠ Напоминания могут не звонить", 18f, true, Ui.RED)
            muted(missing.joinToString("\n") { "• $it" })
            button("Выдать разрешения") { a.startActivity(Intent(a, PermissionsActivity::class.java)) }
        }

        // --- Обратный отсчёт
        val next = AlarmScheduler.upcoming(s).firstOrNull()
        col.card(stroke = Ui.PRIMARY) {
            if (next == null) { text("Нет запланированных событий", 18f, true); return@card }
            val e = next.occurrence.event
            text("до события «${e.title.lowercase()}»", 18f, color = Ui.MUTED, gravity = Gravity.CENTER)
            val big = text("--:--:--", 52f, true, Ui.PRIMARY, gravity = Gravity.CENTER)
            val whenTv = text("", 17f, gravity = Gravity.CENTER)
            val dayWord = if (next.at.toLocalDate() == today) "сегодня" else if (next.at.toLocalDate() == today.plusDays(1)) "завтра"
                else next.at.format(DateTimeFormatter.ofPattern("EEEE", Ui.RU))
            whenTv.text = "${e.type.emoji} ${e.title} · $dayWord в ${MealSchedule.fmt(next.occurrence.timeMin)} · ${if (e.ring) "звонок" else "уведомление"}"
            tick = object : Runnable {
                override fun run() {
                    val d = Duration.between(LocalDateTime.now(), next.at)
                    if (d.isNegative) { a.refresh(); return }
                    val sec = d.seconds
                    big.text = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
                    mainHandler.postDelayed(this, 1000)
                }
            }.also { mainHandler.post(it) }
        }

        // --- Ключевые цифры
        val tot = DayNutrition.totals(s, date)
        col.card {
            text("Сегодня", 20f, true)
            kv("Цель калорий", "${Ui.f0(calc.target.kcal)} ккал", "съедено ${Ui.f0(tot.kcal)} · осталось ${Ui.f0(calc.target.kcal - tot.kcal)}")
            kv("Белки / Жиры / Углеводы", "${Ui.f0(calc.macros.proteinG)} / ${Ui.f0(calc.macros.fatG)} / ${Ui.f0(calc.macros.carbsG)} г")
            kv("Вода (напитки)", "${Ui.f1(s.waterMl(date) / 1000.0)} из ${Ui.f1(calc.waterMl / 1000.0)} л")
            row {
                button("+250 мл", weight = 1f) { s.addWater(date, 250); a.refresh() }
                button("+500 мл", weight = 1f) { s.addWater(date, 500); a.refresh() }
                button("−250", outlined = true, weight = 1f) { s.addWater(date, -250); a.refresh() }
            }
        }
        col.card {
            text("Выполнение норм", 20f, true)
            val m = calc.macros
            normRings(DayNutrition.percents(s, calc, date), listOf(
                "${Ui.f0(tot.kcal)}/${Ui.f0(calc.target.kcal)}", "${Ui.f0(tot.p)}/${Ui.f0(m.proteinG)} г", "${Ui.f0(tot.f)}/${Ui.f0(m.fatG)} г",
                "${Ui.f0(tot.c)}/${Ui.f0(m.carbsG)} г", "${Ui.f0(tot.fib)}/${Ui.f0(calc.fiberG)} г", "${Ui.f1(s.waterMl(date) / 1000.0)}/${Ui.f1(calc.waterMl / 1000.0)} л"))
        }

        // --- Лента дня
        col.card {
            text("Расписание на сегодня", 20f, true)
            val occ = Day.occurrences(s, today)
            if (occ.isEmpty()) muted("Нет событий на сегодня")
            val done = s.doneSet(date); val nowMin = Day.nowMin()
            occ.forEach { o ->
                val isDone = o.key in done
                val status = when { isDone -> "✓"; o.timeMin < nowMin -> "✗"; else -> "○" }
                val color = when { isDone -> Ui.GREEN; o.timeMin < nowMin -> Ui.RED; else -> Ui.MUTED }
                val r = row {
                    addView(TextView(context).apply { text = MealSchedule.fmt(o.timeMin); textSize = 19f; setTypeface(typeface, android.graphics.Typeface.BOLD) },
                        LinearLayout.LayoutParams(Ui.dp(context, 70), -2))
                    addView(TextView(context).apply { text = "${o.event.type.emoji} ${o.event.title}"; textSize = 18f }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(TextView(context).apply { text = status; textSize = 26f; setTextColor(color); gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
                }
                r.minimumHeight = Ui.dp(a, 52)
                r.setOnClickListener { s.setDone(date, o.key, !isDone); a.refresh() }
            }
            muted("Нажмите на строку, чтобы отметить «сделано». ✗ — время прошло, не отмечено.", 13f)
        }

        // --- Вес
        col.card {
            text("Вес", 20f, true)
            kv("Текущий", "${Ui.f1(p.weightKg)} кг", "ИМТ ${Ui.f1(calc.bmi)} — ${calc.bmiClass}", Ui.color(calc.bmiLevel))
            if (p.goal != com.example.healthplan.core.Goal.KEEP) kv("Цель", "${Ui.f1(p.targetWeightKg)} кг",
                calc.weeks?.let { "≈ ${Ui.f0(it)} нед. при текущем плане" } ?: "при текущем плане цель не достигается")
            val et = input("Вес сегодня, кг", "")
            button("Записать вес") {
                val v = et.num(); if (v != null && v in 25.0..350.0) { s.logWeight(date, v); a.refresh() }
            }
            val w = s.weights.takeLast(60)
            if (w.size >= 1) {
                addView(LineChartView(context, w.map { it.date.substring(5).split("-").reversed().joinToString(".") }, w.map { it.kg },
                    if (p.goal != com.example.healthplan.core.Goal.KEEP) p.targetWeightKg else null), lp(topDp = 12))
                muted("Последние записи: " + w.takeLast(5).reversed().joinToString(" · ") { "${it.date.substring(8)}.${it.date.substring(5, 7)} — ${Ui.f1(it.kg)}" }, 13f)
            }
        }
        col.button("Мои параметры и нормы", outlined = true) { a.startActivity(Intent(a, ParamsActivity::class.java)) }
        return Ui.scroll(a, col)
    }

    override fun stop() { tick?.let { mainHandler.removeCallbacks(it) } }
}
