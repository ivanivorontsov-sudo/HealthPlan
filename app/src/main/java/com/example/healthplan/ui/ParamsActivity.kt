package com.example.healthplan.ui

import android.content.Intent
import android.os.Bundle
import com.example.healthplan.core.*

class ParamsActivity : BaseActivity() {
    override fun onResume() { super.onResume(); render() }

    private fun render(): Unit = screen {
        val p = store.profile; val c = Calc.of(p)
        h1("Мои параметры")
        muted("${p.sex.title}, ${p.age} лет, ${Ui.g(p.heightCm)} см, ${Ui.g(p.weightKg)} кг · ${p.activity.title}", 15f)
        if (c.warnings.isNotEmpty()) card(stroke = Ui.RED) {
            text("Обратите внимание", 18f, true, Ui.RED)
            c.warnings.forEach { muted("• $it") }
        }
        card {
            text("Масса тела", 20f, true)
            kv("ИМТ", Ui.f1(c.bmi), "ИМТ = вес / рост² (ВОЗ) · ${c.bmiClass}", Ui.color(c.bmiLevel))
            kv("Нормальный вес для роста", "${Ui.f1(c.healthyMin)}–${Ui.f1(c.healthyMax)} кг", "Диапазон при ИМТ 18,5–24,9 (ВОЗ)")
            if (p.goal != Goal.KEEP) kv("До цели ${Ui.g(p.targetWeightKg)} кг", c.weeks?.let { "≈ ${Ui.f0(it)} нед." } ?: "—",
                "Темп ≈ ${Ui.f1(kotlin.math.abs(c.target.weeklyChangeKg))} кг/нед (дефицит/профицит × 7 / 7700 ккал) — приближение")
        }
        card {
            text("Энергия", 20f, true)
            kv("Основной обмен", "${Ui.f0(c.bmrUsed)} ккал", "Используется: ${c.bmrUsedName}")
            kv("  Миффлин–Сан Жеор", "${Ui.f0(c.bmrMifflin)} ккал", "9,99×вес + 6,25×рост − 4,92×возраст ${if (p.sex == Sex.MALE) "+ 5" else "− 161"} (МР 2.3.1.0253-21)")
            kv("  Харрис–Бенедикт (пересм.)", "${Ui.f0(c.bmrHarris)} ккал", "Roza & Shizgal, 1984 — для сравнения")
            c.bmrKatch?.let { kv("  Кэтч–МакАрдл", "${Ui.f0(it)} ккал", "370 + 21,6 × тощая масса (при % жира ${Ui.f1(c.fatUsed ?: 0.0)})") }
            kv("Суточная потребность", "${Ui.f0(c.tdee)} ккал", "Основной обмен × КФА ${Ui.g(p.activity.pal)}")
            kv("Цель по калориям", "${Ui.f0(c.target.kcal)} ккал", when (p.goal) {
                Goal.KEEP -> "Поддержание веса"
                Goal.LOSE -> "Дефицит ${Ui.f0(c.target.deficit)} ккал/сут (${p.lossRate.title})" + if (c.target.floorApplied) " · ограничено минимумом" else ""
                Goal.GAIN -> "Профицит ${Ui.f0(-c.target.deficit)} ккал/сут (+10%, рекомендация)"
            }, Ui.PRIMARY)
            kv("Безопасный минимум", "${Ui.f0(Formulas.calorieFloor(p.sex))} ккал", "Ниже — только под наблюдением врача (AHA/ACC/TOS 2013, NICE NG246)")
        }
        card {
            text("БЖУ, клетчатка, вода", 20f, true)
            val m = c.macros
            kv("Белки", "${Ui.f0(m.proteinG)} г", "${m.proteinNote} · ${Ui.f1(m.proteinG / p.weightKg)} г/кг")
            kv("Жиры", "${Ui.f0(m.fatG)} г", "30% калорийности (МР; ВОЗ: ≤30%, насыщенные <10%, транс <1%)")
            kv("Углеводы", "${Ui.f0(m.carbsG)} г", "Остаток калорийности; свободные сахара <10% (≈${Ui.f0(c.target.kcal * 0.1 / 4)} г, ВОЗ)")
            kv("Клетчатка", "${Ui.f0(c.fiberG)} г", "МР: 20–25 г; ВОЗ 2023: не менее 25 г")
            kv("Вода (напитки)", "${Ui.f1(c.waterMl / 1000)} л", "МР 2.3.1.0253-21, табл. 7. Всего с пищей ≈ ${Ui.f1(c.totalWaterMl / 1000)} л (1 мл/ккал). EFSA: ${Ui.f1(Formulas.efsaTotalWaterMl(p.sex) / 1000)} л всей воды")
            kv("Соль", "< 5 г", "ВОЗ; фрукты и овощи — не менее 400 г в день")
        }
        card {
            text("Фигура и риски", 20f, true)
            if (p.waistCm == null) muted("Укажите обхват талии (и бёдер, шеи) в профиле, чтобы оценить риски и % жира.")
            p.waistCm?.let { w -> val (t, l) = Formulas.waistClass(p.sex, w); kv("Обхват талии", "${Ui.g(w)} см", "$t · ВОЗ 2008", Ui.color(l)) }
            c.whtr?.let { val (t, l) = Formulas.whtrClass(it); kv("Талия / рост", Formulas.fmt2(it), "$t · NICE NG246: держите талию < половины роста", Ui.color(l)) }
            c.whr?.let { val (t, l) = Formulas.whrClass(p.sex, it); kv("Талия / бёдра", Formulas.fmt2(it), "$t · ВОЗ 2008", Ui.color(l)) }
            c.navyFat?.let { kv("% жира (формула ВМС США)", "${Ui.f1(it)}%", "Hodgdon & Beckett, 1984: по шее, талии${if (p.sex == Sex.FEMALE) ", бёдрам" else ""} и росту; погрешность ±3–4%") }
            if (p.bodyFatPct != null) kv("% жира (ваш)", "${Ui.f1(p.bodyFatPct)}%", "Введён вручную — используется в формуле Кэтча–МакАрдла")
        }
        card {
            text("Не медицинская рекомендация", 18f, true)
            muted(SourcesActivity.DISCLAIMER, 14f)
            button("Источники", outlined = true) { startActivity(Intent(this@ParamsActivity, SourcesActivity::class.java)) }
        }
    }
}
