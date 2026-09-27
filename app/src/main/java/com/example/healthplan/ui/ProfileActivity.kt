package com.example.healthplan.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.example.healthplan.Defaults
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.core.*
import com.google.android.material.textfield.TextInputEditText as TIE

class ProfileActivity : BaseActivity() {
    private var p = Profile()
    private lateinit var age: TIE; private lateinit var h: TIE; private lateinit var w: TIE; private lateinit var tw: TIE
    private lateinit var waist: TIE; private lateinit var hip: TIE; private lateinit var neck: TIE; private lateinit var bf: TIE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = store.profile
        render()
    }

    private fun radios(parent: android.widget.LinearLayout, options: List<String>, selected: Int, onSel: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this)
        options.forEachIndexed { i, s -> g.addView(RadioButton(this).apply { id = 1000 + i; text = s; textSize = 17f; minHeight = Ui.dp(context, 48) }) }
        g.check(1000 + selected)
        g.setOnCheckedChangeListener { _, id -> onSel(id - 1000) }
        parent.addView(g, parent.lp(topDp = 4)); return g
    }

    private fun render(): Unit = screen {
        h1("Профиль")
        muted("Все данные хранятся только на этом телефоне.", 14f)
        card {
            text("Пол", 18f, true)
            radios(this, Sex.values().map { it.title }, p.sex.ordinal) { p = p.copy(sex = Sex.values()[it]) }
        }
        card {
            text("Тело", 18f, true)
            age = input("Возраст, лет", p.age.toString(), decimal = false)
            h = input("Рост, см", Ui.g(p.heightCm))
            w = input("Вес, кг", Ui.g(p.weightKg))
        }
        card {
            text("Уровень физической активности", 18f, true)
            muted("Группы по МР 2.3.1.0253-21 (Роспотребнадзор). КФА — во сколько раз суточные траты больше основного обмена.", 14f)
            val g = RadioGroup(this@ProfileActivity)
            Activity.values().forEachIndexed { i, act ->
                g.addView(RadioButton(context).apply { id = 2000 + i; text = "${act.title}\n${act.description}"; textSize = 16f
                    setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8)) })
            }
            g.check(2000 + p.activity.ordinal)
            g.setOnCheckedChangeListener { _, id -> p = p.copy(activity = Activity.values()[id - 2000]) }
            addView(g, lp(topDp = 4))
        }
        card {
            text("Цель", 18f, true)
            radios(this, Goal.values().map { it.title }, p.goal.ordinal) { p = p.copy(goal = Goal.values()[it]) }
            tw = input("Желаемый вес, кг", Ui.g(p.targetWeightKg))
            text("Темп снижения", 16f, true, topDp = 10)
            radios(this, LossRate.values().map { it.title }, p.lossRate.ordinal) { p = p.copy(lossRate = LossRate.values()[it]) }
            muted("Безопасно — 0,5–1 кг в неделю (NHS). Калорийность не опускается ниже 1500 ккал (муж.) / 1200 ккал (жен.).", 13f)
        }
        card {
            text("Обхваты (необязательно)", 18f, true)
            muted("Талия — посередине между нижним ребром и гребнем подвздошной кости; бёдра — по самой широкой части; шея — под кадыком.", 13f)
            waist = input("Талия, см", p.waistCm?.let { Ui.g(it) } ?: "")
            hip = input("Бёдра, см", p.hipCm?.let { Ui.g(it) } ?: "")
            neck = input("Шея, см", p.neckCm?.let { Ui.g(it) } ?: "")
            bf = input("% жира (если известен, напр. по биоимпедансу)", p.bodyFatPct?.let { Ui.g(it) } ?: "")
        }
        card {
            text("Режим дня", 18f, true)
            row {
                button("Подъём ${MealSchedule.fmt(p.wakeMin)}", outlined = true, weight = 1f) { Dialogs.time(this@ProfileActivity, p.wakeMin) { m -> p = p.copy(wakeMin = m); saveInputs(); render() } }
                button("Отбой ${MealSchedule.fmt(p.sleepMin)}", outlined = true, weight = 1f) { Dialogs.time(this@ProfileActivity, p.sleepMin) { m -> p = p.copy(sleepMin = m); saveInputs(); render() } }
            }
            text("Приёмов пищи в день", 16f, true, topDp = 10)
            radios(this, listOf("3", "4", "5", "6"), p.mealsCount - 3) { p = p.copy(mealsCount = it + 3) }.orientation = RadioGroup.HORIZONTAL
        }
        card {
            text("Ограничения в питании", 18f, true)
            check("Вегетарианство (без мяса и рыбы)", p.vegetarian) { p = p.copy(vegetarian = it) }
            check("Веганство (без продуктов животного происхождения)", p.vegan) { p = p.copy(vegan = it) }
            check("Без лактозы", p.lactoseFree) { p = p.copy(lactoseFree = it) }
            check("Без глютена", p.glutenFree) { p = p.copy(glutenFree = it) }
            check("Без рыбы и морепродуктов", p.noFish) { p = p.copy(noFish = it) }
            check("Без орехов", p.noNuts) { p = p.copy(noNuts = it) }
            check("Без яиц", p.noEggs) { p = p.copy(noEggs = it) }
        }
        button("Сохранить") {
            if (!saveInputs()) return@button
            val old = store.profile
            val first = !old.filled
            p = p.copy(filled = true)
            store.profile = p
            if (old.wakeMin != p.wakeMin || old.sleepMin != p.sleepMin || old.mealsCount != p.mealsCount) {
                store.mealTimeOverrides = emptyMap()
                Defaults.syncMeals(store)
                if (!first) Defaults.retimeFromProfile(store)
            }
            if (first) Defaults.retimeFromProfile(store)
            if (old.weightKg != p.weightKg) store.logWeight(java.time.LocalDate.now().toString(), p.weightKg)
            AlarmScheduler.scheduleAll(this@ProfileActivity)
            Toast.makeText(this@ProfileActivity, "Сохранено, нормы пересчитаны", Toast.LENGTH_SHORT).show()
            if (first && !store.permissionsShown) { store.permissionsShown = true; startActivity(android.content.Intent(this@ProfileActivity, PermissionsActivity::class.java)) }
            finish()
        }
    }

    private fun saveInputs(): Boolean {
        val a = age.num()?.toInt(); val hh = h.num(); val ww = w.num()
        if (a == null || a !in 10..110 || hh == null || hh !in 100.0..250.0 || ww == null || ww !in 25.0..350.0) {
            Toast.makeText(this, "Проверьте возраст (10–110), рост (100–250 см) и вес (25–350 кг)", Toast.LENGTH_LONG).show(); return false
        }
        p = p.copy(age = a, heightCm = hh, weightKg = ww, targetWeightKg = tw.num() ?: ww,
            waistCm = waist.num()?.takeIf { it in 40.0..250.0 }, hipCm = hip.num()?.takeIf { it in 50.0..250.0 },
            neckCm = neck.num()?.takeIf { it in 20.0..70.0 }, bodyFatPct = bf.num()?.takeIf { it in 3.0..70.0 })
        if (p.vegan) p = p.copy(vegetarian = true)
        return true
    }

}
