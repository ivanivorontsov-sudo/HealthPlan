package com.example.healthplan.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.healthplan.App
import com.example.healthplan.Day
import com.example.healthplan.DayNutrition
import com.example.healthplan.core.Calc
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.DiaryEntry
import com.example.healthplan.food.Food
import com.example.healthplan.food.GramSolver
import com.example.healthplan.food.N

/** «Рассчитать граммы»: пользователь выбирает продукты, приложение подбирает граммы под цель приёма пищи. */
class MealBuilderActivity : BaseActivity() {
    private var meal = 0
    private var date = ""
    private val chosen = mutableListOf<Food>()
    private var result: GramSolver.Result? = null
    private var subtractEaten = true

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringExtra("foodId")?.let { id -> App.foods(this).byId(id)?.let { if (chosen.none { c -> c.id == it.id }) chosen += it } }
        result = null; render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meal = intent.getIntExtra("meal", 0); date = intent.getStringExtra("date") ?: Day.today()
        render()
    }

    private fun target(): N {
        val calc = Calc.of(store.profile)
        val t = Day.mealTargets(calc, store).getOrElse(meal) { N.ZERO }
        if (!subtractEaten) return t
        val eaten = store.diaryFor(date).filter { it.meal == meal }.fold(N.ZERO) { a, e -> a + N(e.kcal100, e.p100, e.f100, e.c100, e.fib100) * (e.grams / 100.0) }
        return N(maxOf(0.0, t.kcal - eaten.kcal), maxOf(0.0, t.p - eaten.p), maxOf(0.0, t.f - eaten.f), maxOf(0.0, t.c - eaten.c), maxOf(0.0, t.fib - eaten.fib))
    }

    private fun render(): Unit = screen {
        val slots = store.mealSlots()
        h1("Рассчитать граммы")
        card {
            text("Приём пищи", 18f, true)
            val cg = com.google.android.material.chip.ChipGroup(context).apply { isSingleSelection = true }
            slots.forEach { s -> cg.addView(com.google.android.material.chip.Chip(context).apply {
                text = "${s.name} ${MealSchedule.fmt(s.timeMin)}"; isCheckable = true; isChecked = s.index == meal; textSize = 15f
                setOnClickListener { meal = s.index; result = null; render() } }) }
            addView(cg, lp(topDp = 4))
            check("Учесть уже съеденное в этот приём", subtractEaten) { subtractEaten = it; result = null; render() }
            val t = target()
            kv("Цель", "${Ui.f0(t.kcal)} ккал", "Б ${Ui.f0(t.p)} · Ж ${Ui.f0(t.f)} · У ${Ui.f0(t.c)} г")
        }
        card {
            text("Продукты (2–5 шт.)", 18f, true)
            if (chosen.isEmpty()) muted("Добавьте продукты, из которых хотите собрать приём пищи, например: гречка варёная, куриная грудка, огурец, масло.")
            chosen.forEachIndexed { i, f ->
                row {
                    addView(android.widget.TextView(context).apply { text = f.name; textSize = 17f }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                    result?.let { r -> addView(android.widget.TextView(context).apply { text = "${Ui.g(r.grams[i])} г"; textSize = 21f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(Ui.PRIMARY) }) }
                    button("✕", outlined = true, topDp = 0) { chosen.removeAt(i); result = null; render() }.layoutParams = android.widget.LinearLayout.LayoutParams(Ui.dp(context, 64), -2)
                }
            }
            button("+ Добавить продукт", outlined = true) { picker.launch(Intent(this@MealBuilderActivity, FoodPickerActivity::class.java).putExtra("pick", true)) }
        }
        if (chosen.isNotEmpty()) button("Рассчитать граммы") { result = GramSolver.solve(chosen, target()); render() }
        result?.let { r ->
            val t = target()
            card(stroke = Ui.PRIMARY) {
                text("Результат", 18f, true)
                kv("Калории", "${Ui.f0(r.totals.kcal)} / ${Ui.f0(t.kcal)}", dev(r.errorPct["kcal"]))
                kv("Белки", "${Ui.f0(r.totals.p)} / ${Ui.f0(t.p)} г", dev(r.errorPct["p"]))
                kv("Жиры", "${Ui.f0(r.totals.f)} / ${Ui.f0(t.f)} г", dev(r.errorPct["f"]))
                kv("Углеводы", "${Ui.f0(r.totals.c)} / ${Ui.f0(t.c)} г", dev(r.errorPct["c"]))
                muted("Точно попасть во все цели удаётся не всегда — приоритет у калорий и белка. Добавьте продукт с недостающим нутриентом для лучшего совпадения.", 13f)
                button("Добавить в дневник") {
                    chosen.zip(r.grams).filter { it.second > 0 }.forEach { (f, g) ->
                        store.addDiary(DiaryEntry(System.nanoTime(), date, meal, f.id, f.name, g, f.per100.kcal, f.per100.p, f.per100.f, f.per100.c, f.per100.fib))
                    }
                    Toast.makeText(this@MealBuilderActivity, "Добавлено в дневник", Toast.LENGTH_SHORT).show(); finish()
                }
            }
        }
    }

    private fun dev(p: Double?) = p?.let { (if (it >= 0) "+" else "") + Ui.f0(it) + "% от цели" }
}
