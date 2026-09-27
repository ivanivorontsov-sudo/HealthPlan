package com.example.healthplan.core

enum class Sex(val title: String) { MALE("Мужской"), FEMALE("Женский") }

enum class Goal(val title: String) { LOSE("Снизить вес"), KEEP("Удержать вес"), GAIN("Набрать вес") }

/**
 * Группы физической активности по МР 2.3.1.0253-21 (п. 3.3), КФА = коэффициент физической активности
 * (отношение суточных энерготрат к основному обмену). Доля белка в калорийности — п. 1.7 МР.
 */
enum class Activity(val pal: Double, val title: String, val description: String, val proteinShare: Double) {
    G1(1.4, "I группа — очень низкая (КФА 1,4)",
        "Преимущественно умственный/сидячий труд: офис, программисты, учителя, студенты, операторы. Мало ходьбы, нет регулярных тренировок.", 0.14),
    G2(1.6, "II группа — низкая (КФА 1,6)",
        "Лёгкий труд на ногах: продавцы, водители городского транспорта, медсёстры, парикмахеры, конвейер. Или сидячая работа + регулярные тренировки 2–3 раза в неделю.", 0.13),
    G3(1.9, "III группа — средняя (КФА 1,9)",
        "Труд средней тяжести: слесари, станочники, садовники, водители тяжёлой техники. Или ежедневные интенсивные тренировки.", 0.125),
    G4(2.2, "IV группа — высокая (КФА 2,2)",
        "Тяжёлый физический труд: строители, грузчики, лесное и сельское хозяйство, металлурги.", 0.12);
}

enum class LossRate(val kgPerWeek: Double, val title: String) {
    SLOW(0.25, "Мягко — ~0,25 кг/нед"),
    NORMAL(0.5, "Умеренно — ~0,5 кг/нед"),
    FAST(0.75, "Быстрее — ~0,75 кг/нед")
}

data class Profile(
    val sex: Sex = Sex.MALE,
    val age: Int = 35,
    val heightCm: Double = 178.0,
    val weightKg: Double = 85.0,
    val activity: Activity = Activity.G1,
    val goal: Goal = Goal.KEEP,
    val targetWeightKg: Double = 80.0,
    val lossRate: LossRate = LossRate.NORMAL,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val neckCm: Double? = null,
    val bodyFatPct: Double? = null,
    val wakeMin: Int = 7 * 60,
    val sleepMin: Int = 23 * 60,
    val mealsCount: Int = 4,
    val vegetarian: Boolean = false,
    val vegan: Boolean = false,
    val lactoseFree: Boolean = false,
    val glutenFree: Boolean = false,
    val noFish: Boolean = false,
    val noNuts: Boolean = false,
    val noEggs: Boolean = false,
    val filled: Boolean = false
)
