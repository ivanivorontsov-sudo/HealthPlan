package com.example.healthplan.ui

import android.content.Intent
import com.example.healthplan.App

class MyFoodsActivity : BaseActivity() {
    override fun onResume() { super.onResume(); render() }

    private fun render(): Unit = screen {
        h1("Мои блюда и продукты")
        muted("Свои продукты (по этикетке), блюда с известным составом на порцию и рецепты из продуктов базы. Они ищутся вместе со встроенной базой и доступны в дневнике и в «Рассчитать граммы».", 14f)
        row {
            button("+ Добавить", weight = 1f) { startActivity(Intent(this@MyFoodsActivity, CustomFoodActivity::class.java)) }
            button("📷 По фото этикетки", outlined = true, weight = 1f) { startActivity(Intent(this@MyFoodsActivity, OcrActivity::class.java)) }
        }
        val list = store.customFoods.sortedBy { it.name.lowercase() }
        if (list.isEmpty()) card { muted("Пока пусто.") }
        list.forEach { f ->
            card(topDp = 10) {
                text(f.name, 18f, true)
                muted("На 100 г: ${Ui.f0(f.per100.kcal)} ккал · Б ${Ui.f1(f.per100.p)} · Ж ${Ui.f1(f.per100.f)} · У ${Ui.f1(f.per100.c)} · Кл ${Ui.f1(f.per100.fib)}", 14f)
                f.portionG?.let { pg -> val n = f.nutrients(pg); muted("Порция ${Ui.g(pg)} г: ${Ui.f0(n.kcal)} ккал · Б ${Ui.f1(n.p)} · Ж ${Ui.f1(n.f)} · У ${Ui.f1(n.c)}", 14f) }
                if (f.recipe.isNotEmpty()) muted("Рецепт: " + f.recipe.joinToString(", ") { "${it.name} ${Ui.g(it.grams)} г" }, 13f)
                row {
                    button("Изменить", outlined = true, weight = 1f) { startActivity(Intent(this@MyFoodsActivity, CustomFoodActivity::class.java).putExtra("id", f.id)) }
                    button("Удалить", outlined = true, weight = 1f) {
                        Dialogs.confirm(this@MyFoodsActivity, "Удалить «${f.name}»?", "Записи в дневнике сохранятся.") { store.deleteCustomFood(f.id); App.foods(this@MyFoodsActivity); render() }
                    }
                }
            }
        }
    }
}
