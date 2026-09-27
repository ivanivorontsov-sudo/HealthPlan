package com.example.healthplan.ui

import android.os.Bundle

class SourcesActivity : BaseActivity() {
    companion object {
        const val DISCLAIMER = "Приложение даёт справочные расчёты по общепринятым формулам и нормам для здоровых взрослых. Это не медицинская рекомендация и не замена врачу. Обязательно проконсультируйтесь с врачом, если ИМТ ниже 18,5 или выше 35, при беременности и кормлении грудью, хронических заболеваниях (сердце, почки, диабет, ЖКТ и др.), приёме лекарств, расстройствах пищевого поведения, а также если вам меньше 18 лет."

        val SOURCES = listOf(
            "Нормы потребностей (КФА, БЖУ, клетчатка, вода, формула основного обмена)" to "МР 2.3.1.0253-21 «Нормы физиологических потребностей в энергии и пищевых веществах для различных групп населения РФ», Роспотребнадзор, 22.07.2021. base.garant.ru/402816140",
            "Формула Миффлина–Сан Жеора" to "Mifflin MD, St Jeor ST et al. A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr 1990;51(2):241–247. PMID 2305711",
            "Формула Харриса–Бенедикта (пересмотр)" to "Roza AM, Shizgal HM. The Harris Benedict equation reevaluated. Am J Clin Nutr 1984;40(1):168–182. PMID 6741850",
            "Формула Кэтча–МакАрдла" to "Katch F, McArdle W. Nutrition, Weight Control and Exercise (1977/1983); BMR = 370 + 21,6 × тощая масса",
            "ИМТ и классы" to "ВОЗ: «A healthy lifestyle – WHO recommendations»; WHO TRS 894 (2000). who.int",
            "Обхват талии, талия/бёдра" to "WHO. Waist circumference and waist–hip ratio: report of a WHO expert consultation, 2008 (опубл. 2011). iris.who.int/handle/10665/44583",
            "Талия/рост 0,5" to "NICE NG246 «Overweight and obesity management» (2025), п. 1.9.14–1.9.15. nice.org.uk/guidance/ng246",
            "% жира по обхватам" to "Hodgdon JA, Beckett MB. Prediction of percent body fat for U.S. Navy men/women from body circumferences and height. NHRC reports 84-11, 84-29 (1984)",
            "Здоровое питание (жиры, сахар, соль, овощи)" to "ВОЗ, информационный бюллетень «Здоровое питание» (Healthy diet). who.int/news-room/fact-sheets/detail/healthy-diet",
            "Клетчатка ≥25 г" to "WHO guideline: Carbohydrate intake for adults and children, 2023",
            "Вода (EFSA)" to "EFSA NDA Panel. Scientific Opinion on Dietary Reference Values for water. EFSA Journal 2010;8(3):1459",
            "Безопасный темп снижения веса" to "NHS: 0,5–1 кг в неделю, ≈ −600 ккал/сут; NICE NG246: диеты 800–1200 ккал — только в специализированной службе; AHA/ACC/TOS 2013: 1200–1500 ккал (ж) / 1500–1800 (м)",
            "Белок при снижении веса 1,2 г/кг" to "Рекомендация: ISSN Position Stand: protein and exercise (Jäger et al., 2017) — 1,4–2,0 г/кг для тренирующихся; МР — минимум 0,83 г/кг",
            "Чистка зубов" to "NHS «How to keep your teeth clean»: 2 раза в день по 2 минуты, паста 1350–1500 ppm фтора, сплюнуть, не полоскать",
            "Закаливание" to "Роспотребнадзор (Упр. по Респ. Алтай) «Закаливание — вред или польза?», 2026; Минздрав Забайкальского края «Общие принципы закаливания», 2025",
            "Физическая активность" to "ВОЗ. Руководство по физической активности и малоподвижному образу жизни, 2020: 150–300 мин/нед умеренной активности",
            "Состав продуктов" to "USDA FoodData Central, SR Legacy (апрель 2018), public domain. fdc.nal.usda.gov. Углеводы в приложении = «углеводы по разности» − пищевые волокна (усвояемые, как на российских этикетках). Блюда «расчёт по рецептуре» — сумма ингредиентов USDA по примерной рецептуре",
            "Энергия 1 кг массы тела ≈ 7700 ккал" to "Классическое приближение (Wishnofsky, 1958: 3500 ккал/фунт). Реальная скорость снижения со временем замедляется (Hall et al., Lancet 2011)",
            "Режим питания, распределение калорий по приёмам" to "Рекомендация (традиционное распределение отечественной диетологии; завтрак в течение часа после подъёма, последний приём за 2–3 ч до сна)",
            "Цветовые зоны выполнения норм" to "Рекомендация приложения (коридоры ±10–30% от нормы)"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen {
            h1("Источники")
            card(stroke = Ui.RED) { text("Не медицинская рекомендация", 18f, true, Ui.RED); muted(DISCLAIMER) }
            SOURCES.forEach { (what, src) -> card(topDp = 10) { text(what, 17f, true); muted(src, 14f) } }
            muted("Значения без надёжного источника помечены в приложении словом «рекомендация».", 13f)
        }
    }
}
