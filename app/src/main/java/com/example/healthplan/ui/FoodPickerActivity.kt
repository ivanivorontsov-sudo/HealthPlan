package com.example.healthplan.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.example.healthplan.App
import com.example.healthplan.data.DiaryEntry
import com.example.healthplan.food.Food
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Поиск продукта: встроенная база + свои продукты. Режимы:
 *  — с extras meal/date: ввод граммов и добавление в дневник;
 *  — pick=true: вернуть id выбранного продукта (для рецептов и конструктора).
 */
class FoodPickerActivity : BaseActivity() {
    private var mode = 0 // 0 все, 1 избранное, 2 недавние, 3 мои
    private var query = ""
    private lateinit var adapter: FoodAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val pick = intent.getBooleanExtra("pick", false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(context, 12); setPadding(p, p, p, 0) }
        val et = root.input("Поиск: гречка, курица, творог…", "", number = false, topDp = 0)
        val chips = ChipGroup(this).apply { isSingleSelection = true }
        listOf("Все", "★ Избранное", "Недавние", "Мои").forEachIndexed { i, s ->
            chips.addView(Chip(this).apply { text = s; isCheckable = true; isChecked = i == 0; textSize = 15f; setOnClickListener { mode = i; reload() } })
        }
        root.addView(chips, root.lp(topDp = 4))
        root.row {
            button("+ Свой продукт", outlined = true, weight = 1f, topDp = 0) { startActivity(Intent(this@FoodPickerActivity, CustomFoodActivity::class.java)) }
            button("📷 Этикетка", outlined = true, weight = 1f, topDp = 0) { startActivity(Intent(this@FoodPickerActivity, OcrActivity::class.java)) }
        }
        val lv = ListView(this)
        adapter = FoodAdapter()
        lv.adapter = adapter
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s.toString(); reload() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        lv.setOnItemClickListener { _, _, pos, _ ->
            val f = adapter.items[pos]
            if (pick) { setResult(RESULT_OK, Intent().putExtra("foodId", f.id)); finish(); return@setOnItemClickListener }
            val meal = intent.getIntExtra("meal", 0); val date = intent.getStringExtra("date") ?: java.time.LocalDate.now().toString()
            Dialogs.grams(this, f, null, { g ->
                store.addDiary(DiaryEntry(System.nanoTime(), date, meal, f.id, f.name, g, f.per100.kcal, f.per100.p, f.per100.f, f.per100.c, f.per100.fib))
                Toast.makeText(this, "Добавлено: ${f.name}, ${Ui.g(g)} г", Toast.LENGTH_SHORT).show()
                reload()
            })
        }
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            val f = adapter.items[pos]
            store.favourites = if (f.id in store.favourites) store.favourites - f.id else store.favourites + f.id
            Toast.makeText(this, if (f.id in store.favourites) "В избранном ★" else "Убрано из избранного", Toast.LENGTH_SHORT).show()
            reload(); true
        }
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        val db = App.foods(this)
        val found = db.search(query)
        adapter.items = when (mode) {
            1 -> found.filter { it.id in store.favourites }
            2 -> store.recents.mapNotNull { id -> found.firstOrNull { it.id == id } }
            3 -> found.filter { it.isCustom }
            else -> found
        }
        adapter.notifyDataSetChanged()
    }

    inner class FoodAdapter : BaseAdapter() {
        var items: List<Food> = emptyList()
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            val f = items[p]
            val box = (convert as? LinearLayout) ?: LinearLayout(this@FoodPickerActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10))
                addView(TextView(context).apply { textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                addView(TextView(context).apply { textSize = 14f; setTextColor(Ui.MUTED) })
            }
            val fav = if (f.id in store.favourites) "★ " else ""
            (box.getChildAt(0) as TextView).text = fav + f.name
            (box.getChildAt(1) as TextView).text = "${f.category} · ${Ui.f0(f.per100.kcal)} ккал · Б ${Ui.f1(f.per100.p)} · Ж ${Ui.f1(f.per100.f)} · У ${Ui.f1(f.per100.c)} /100 г"
            return box
        }
    }
}
