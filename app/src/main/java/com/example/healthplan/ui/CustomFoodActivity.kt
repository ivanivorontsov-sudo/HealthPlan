package com.example.healthplan.ui

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.healthplan.App
import com.example.healthplan.food.Food
import com.example.healthplan.food.Ingredient
import com.example.healthplan.food.N
import com.example.healthplan.food.NutritionMath
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

/** Свой продукт/блюдо: значения на 100 г, на порцию или рецепт из продуктов базы. */
class CustomFoodActivity : BaseActivity() {
    private var foodId: String = "c:" + UUID.randomUUID().toString()
    private var name = ""; private var mode = 0 // 0 — на 100 г, 1 — на порцию, 2 — рецепт
    private var vals = arrayOf("", "", "", "", "")   // ккал, Б, Ж, У, клетчатка
    private var portion = ""; private var cooked = ""
    private var ingredients = mutableListOf<Ingredient>()
    private var source = "Свой продукт"
    private var rawOcr: String? = null
    private var ets: List<TextInputEditText> = emptyList()
    private var nameEt: TextInputEditText? = null; private var portionEt: TextInputEditText? = null; private var cookedEt: TextInputEditText? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val fid = r.data?.getStringExtra("foodId") ?: return@registerForActivityResult
        val f = App.foods(this).byId(fid) ?: return@registerForActivityResult
        Dialogs.grams(this, f, null, { g -> ingredients += Ingredient(f.id, f.name, g, f.per100); render() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("id")?.let { eid -> store.customFoods.firstOrNull { it.id == eid }?.let { load(it) } }
        if (intent.hasExtra("ocr")) {
            source = "Этикетка (распознано с фото, проверено пользователем)"
            rawOcr = intent.getStringExtra("raw")
            if (name.isBlank()) name = com.example.healthplan.ocr.LabelParser.guessName(rawOcr ?: "")
            val per100 = intent.getBooleanExtra("per100", true)
            mode = if (per100) 0 else 1
            fun d(k: String) = if (intent.hasExtra(k)) Ui.g(Math.round(intent.getDoubleExtra(k, 0.0) * 10) / 10.0) else ""
            vals = arrayOf(d("kcal"), d("p"), d("f"), d("c"), d("fib"))
            if (intent.hasExtra("portion")) portion = d("portion")
        }
        render()
    }

    private fun load(f: Food) {
        foodId = f.id; name = f.name; source = f.source
        if (f.recipe.isNotEmpty()) { mode = 2; ingredients = f.recipe.toMutableList(); cooked = f.cookedWeightG?.let { Ui.g(it) } ?: ""; portion = f.portionG?.let { Ui.g(it) } ?: "" }
        else {
            mode = 0; val n = f.per100
            vals = arrayOf(Ui.g(n.kcal), Ui.g(r1(n.p)), Ui.g(r1(n.f)), Ui.g(r1(n.c)), Ui.g(r1(n.fib)))
            portion = f.portionG?.let { Ui.g(it) } ?: ""
        }
    }

    private fun r1(x: Double) = Math.round(x * 10) / 10.0
    private fun keep() {
        nameEt?.let { name = it.text.toString() }
        if (ets.size == 5) vals = Array(5) { ets[it].text.toString() }
        portionEt?.let { portion = it.text.toString() }; cookedEt?.let { cooked = it.text.toString() }
    }
    private fun d(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

    private fun render(): Unit = screen {
        h1(if (store.customFoods.any { it.id == foodId }) "Изменить" else "Новый продукт / блюдо")
        nameEt = input("Название", name, number = false)
        rawOcr?.let { raw -> card(stroke = Ui.YELLOW) { text("Распознано с фото — проверьте значения!", 16f, true); muted(raw.take(1200), 12f) } }
        card {
            text("Как задать состав", 18f, true)
            val g = RadioGroup(context)
            listOf("Значения на 100 г (как на этикетке)", "Значения на порцию (укажите вес порции)", "Рецепт из продуктов базы").forEachIndexed { i, s ->
                g.addView(RadioButton(context).apply { this.id = 10 + i; text = s; textSize = 17f; minHeight = Ui.dp(context, 48) })
            }
            g.check(10 + mode)
            g.setOnCheckedChangeListener { _, cid -> keep(); mode = cid - 10; render() }
            addView(g, lp(topDp = 4))
        }
        if (mode < 2) card {
            text(if (mode == 0) "На 100 г" else "На одну порцию", 18f, true)
            if (mode == 1) portionEt = input("Вес порции, г", portion)
            val labels = listOf("Калорийность, ккал", "Белки, г", "Жиры, г", "Углеводы, г", "Пищевые волокна (клетчатка), г — необязательно")
            ets = labels.mapIndexed { i, l -> input(l, vals[i]) }
            button("Посчитать ккал из БЖУ", outlined = true) {
                keep()
                val k = NutritionMath.kcalFromMacros(d(vals[1]) ?: 0.0, d(vals[2]) ?: 0.0, d(vals[3]) ?: 0.0, d(vals[4]) ?: 0.0)
                vals[0] = Ui.f0(k).replace(" ", ""); render()
            }
            muted("Коэффициенты МР 2.3.1.0253-21 (прил. 3): белки и углеводы 4 ккал/г, жиры 9, пищевые волокна 2.", 13f)
            if (mode == 0) { portionEt = input("Вес обычной порции, г (необязательно)", portion) }
        } else {
            ets = emptyList()
            card {
                text("Ингредиенты", 18f, true)
                if (ingredients.isEmpty()) muted("Добавьте продукты и их вес (в сыром виде или как взвешиваете).")
                ingredients.forEachIndexed { i, ing ->
                    row {
                        addView(android.widget.TextView(context).apply { text = ing.name; textSize = 17f }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                        addView(android.widget.TextView(context).apply { text = "${Ui.g(ing.grams)} г"; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                    }.setOnClickListener {
                        keep()
                        Dialogs.grams(this@CustomFoodActivity, Food(ing.foodId, ing.name, "", ing.per100), ing.grams,
                            { g -> ingredients[i] = ing.copy(grams = g); render() }, { ingredients.removeAt(i); render() })
                    }
                }
                button("+ Ингредиент") { keep(); picker.launch(Intent(this@CustomFoodActivity, FoodPickerActivity::class.java).putExtra("pick", true)) }
                cookedEt = input("Вес готового блюда, г (если взвешивали; иначе = сумме)", cooked)
                portionEt = input("Вес порции, г (необязательно)", portion)
                val per100 = Food.recipePer100(ingredients, d(cooked))
                divider()
                kv("Итого сырьё", "${Ui.g(ingredients.sumOf { it.grams })} г")
                kv("На 100 г готового", "${Ui.f0(per100.kcal)} ккал", "Б ${Ui.f1(per100.p)} · Ж ${Ui.f1(per100.f)} · У ${Ui.f1(per100.c)} · Кл ${Ui.f1(per100.fib)}")
                d(portion)?.let { pg -> val n = per100 * (pg / 100.0); kv("Порция ${Ui.g(pg)} г", "${Ui.f0(n.kcal)} ккал", "Б ${Ui.f1(n.p)} · Ж ${Ui.f1(n.f)} · У ${Ui.f1(n.c)}") }
                button("Пересчитать", outlined = true) { keep(); render() }
            }
        }
        button("Сохранить") {
            keep()
            if (name.isBlank()) { Toast.makeText(this@CustomFoodActivity, "Введите название", Toast.LENGTH_SHORT).show(); return@button }
            val pg = d(portion)?.takeIf { it > 0 }
            val food = when (mode) {
                2 -> {
                    if (ingredients.isEmpty()) { Toast.makeText(this@CustomFoodActivity, "Добавьте ингредиенты", Toast.LENGTH_SHORT).show(); return@button }
                    Food(foodId, name.trim(), "Мои блюда", Food.recipePer100(ingredients, d(cooked)), "", "Рецепт пользователя (ингредиенты из базы)", pg, ingredients.toList(), d(cooked))
                }
                else -> {
                    val n = N(d(vals[0]) ?: -1.0, d(vals[1]) ?: 0.0, d(vals[2]) ?: 0.0, d(vals[3]) ?: 0.0, d(vals[4]) ?: 0.0)
                    if (n.kcal < 0) { Toast.makeText(this@CustomFoodActivity, "Укажите калорийность (или нажмите «Посчитать ккал из БЖУ»)", Toast.LENGTH_LONG).show(); return@button }
                    if (mode == 1 && pg == null) { Toast.makeText(this@CustomFoodActivity, "Укажите вес порции", Toast.LENGTH_SHORT).show(); return@button }
                    val per100 = if (mode == 1) Food.fromPortion(n, pg!!) else n
                    Food(foodId, name.trim(), "Мои блюда", per100, "", source, pg)
                }
            }
            store.saveCustomFood(food); App.foods(this@CustomFoodActivity)
            Toast.makeText(this@CustomFoodActivity, "Сохранено: ${food.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
