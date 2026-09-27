package com.example.healthplan.core

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Все формулы с источниками — см. RESEARCH.md. */
object Formulas {

    // ---------- ИМТ (ВОЗ) ----------
    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    /** Классы ВОЗ (WHO, «A healthy lifestyle – WHO recommendations» / TRS 894). */
    fun bmiClass(bmi: Double): String = when {
        bmi < 16.0 -> "Выраженный дефицит массы (<16)"
        bmi < 17.0 -> "Умеренный дефицит массы (16–16,9)"
        bmi < 18.5 -> "Лёгкий дефицит массы (17–18,4)"
        bmi < 25.0 -> "Норма (18,5–24,9)"
        bmi < 30.0 -> "Избыточная масса / предожирение (25–29,9)"
        bmi < 35.0 -> "Ожирение I степени (30–34,9)"
        bmi < 40.0 -> "Ожирение II степени (35–39,9)"
        else -> "Ожирение III степени (≥40)"
    }

    fun bmiLevel(bmi: Double): Level = when {
        bmi < 17.0 || bmi >= 35.0 -> Level.BAD
        bmi < 18.5 || bmi >= 25.0 -> Level.WARN
        else -> Level.OK
    }

    /** Диапазон нормального веса для роста при ИМТ 18,5–24,9. */
    fun healthyWeightRange(heightCm: Double): Pair<Double, Double> {
        val m = heightCm / 100.0
        return Pair(18.5 * m * m, 24.9 * m * m)
    }

    // ---------- Основной обмен ----------
    /** Миффлин–Сан Жеор (Mifflin et al., AJCN 1990; в той же форме — МР 2.3.1.0253-21, формулы 2–3). */
    fun mifflin(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double =
        9.99 * weightKg + 6.25 * heightCm - 4.92 * age + if (sex == Sex.MALE) 5.0 else -161.0

    /** Харрис–Бенедикт, пересмотр Roza & Shizgal (AJCN 1984). Для сравнения. */
    fun harrisBenedict(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double =
        if (sex == Sex.MALE) 88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age
        else 447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.330 * age

    /** Кэтч–МакАрдл: 370 + 21,6 × тощая масса (кг). */
    fun katchMcArdle(weightKg: Double, bodyFatPct: Double): Double =
        370.0 + 21.6 * weightKg * (1.0 - bodyFatPct / 100.0)

    // ---------- % жира: формула ВМС США (Hodgdon & Beckett, NHRC 1984), дюймы ----------
    private const val IN = 2.54
    fun navyBodyFat(sex: Sex, heightCm: Double, neckCm: Double, waistCm: Double, hipCm: Double?): Double? {
        val h = heightCm / IN; val n = neckCm / IN; val w = waistCm / IN
        return if (sex == Sex.MALE) {
            if (w - n <= 0) null else 86.010 * log10(w - n) - 70.041 * log10(h) + 36.76
        } else {
            val hip = (hipCm ?: return null) / IN
            if (w + hip - n <= 0) null else 163.205 * log10(w + hip - n) - 97.684 * log10(h) - 78.387
        }
    }

    // ---------- Абдоминальное ожирение ----------
    /** Отношение талии к росту (NICE NG246): 0,4–0,49 норма; 0,5–0,59 повышенный риск; ≥0,6 высокий. */
    fun whtr(waistCm: Double, heightCm: Double) = waistCm / heightCm
    fun whtrClass(r: Double): Pair<String, Level> = when {
        r < 0.4 -> "Ниже 0,4 — очень низкое (проверьте замер)" to Level.WARN
        r < 0.5 -> "Норма (0,40–0,49)" to Level.OK
        r < 0.6 -> "Повышенный риск (0,50–0,59)" to Level.WARN
        else -> "Высокий риск (≥0,60)" to Level.BAD
    }

    /** Талия/бёдра (ВОЗ 2008/2011): абдоминальное ожирение при ≥0,90 (м) и ≥0,85 (ж). */
    fun whr(waistCm: Double, hipCm: Double) = waistCm / hipCm
    fun whrClass(sex: Sex, r: Double): Pair<String, Level> {
        val cut = if (sex == Sex.MALE) 0.90 else 0.85
        return if (r >= cut) "Повышенный риск (≥${fmt2(cut)})" to Level.BAD else "Норма (<${fmt2(cut)})" to Level.OK
    }

    /** Окружность талии (ВОЗ 2008/2011, европеоиды): м >94 повышен, >102 значительно; ж >80 / >88. */
    fun waistClass(sex: Sex, waistCm: Double): Pair<String, Level> {
        val (a, b) = if (sex == Sex.MALE) 94.0 to 102.0 else 80.0 to 88.0
        return when {
            waistCm > b -> "Значительно повышенный риск (>${b.toInt()} см)" to Level.BAD
            waistCm > a -> "Повышенный риск (>${a.toInt()} см)" to Level.WARN
            else -> "Норма (≤${a.toInt()} см)" to Level.OK
        }
    }

    // ---------- Энергия ----------
    fun tdee(bmr: Double, pal: Double) = bmr * pal

    /** Энергетический эквивалент 1 кг массы тела — классическое приближение ≈7700 ккал (3500 ккал/фунт, Wishnofsky 1958). */
    const val KCAL_PER_KG = 7700.0

    /** Нижняя граница калорийности без наблюдения врача (AHA/ACC/TOS 2013: 1200–1500 ж / 1500–1800 м — берём нижние). */
    fun calorieFloor(sex: Sex) = if (sex == Sex.MALE) 1500.0 else 1200.0

    /** Профицит при наборе — «рекомендация»: +10% (не менее 200, не более 500 ккал). */
    fun gainSurplus(tdee: Double) = min(500.0, max(200.0, tdee * 0.10))

    data class Target(val kcal: Double, val deficit: Double, val floorApplied: Boolean, val weeklyChangeKg: Double)

    fun targetCalories(sex: Sex, tdee: Double, goal: Goal, rate: LossRate): Target = when (goal) {
        Goal.KEEP -> Target(tdee, 0.0, false, 0.0)
        Goal.GAIN -> {
            val s = gainSurplus(tdee)
            Target(tdee + s, -s, false, s * 7 / KCAL_PER_KG)
        }
        Goal.LOSE -> {
            val wanted = rate.kgPerWeek * KCAL_PER_KG / 7.0          // 0,5 кг/нед ≈ 550 ккал/сут
            val floor = calorieFloor(sex)
            var kcal = tdee - wanted
            var applied = false
            if (kcal < floor) { kcal = min(tdee, floor); applied = true }
            val deficit = tdee - kcal
            Target(kcal, deficit, applied, -deficit * 7 / KCAL_PER_KG)
        }
    }

    /** Недель до цели при заданном недельном изменении; null если цель недостижима при таком режиме. */
    fun weeksToGoal(currentKg: Double, targetKg: Double, weeklyChangeKg: Double): Double? {
        val diff = targetKg - currentKg
        if (kotlin.math.abs(diff) < 0.05) return 0.0
        if (weeklyChangeKg == 0.0 || diff * weeklyChangeKg < 0) return null
        return diff / weeklyChangeKg
    }

    // ---------- Макронутриенты ----------
    data class Macros(val proteinG: Double, val fatG: Double, val carbsG: Double, val proteinNote: String)

    /**
     * Белок: доля калорийности по группе КФА (МР п.1.7, 12–14%), но не менее 0,83 г/кг (МР, примечание к табл. 13)
     * и при снижении веса — не менее 1,2 г/кг («рекомендация», ISSN 2017 / сохранение мышечной массы).
     * Жиры: 30% калорийности (МР; ВОЗ ≤30%). Углеводы — остаток (МР: 56–58%).
     */
    fun macros(kcal: Double, weightKg: Double, activity: Activity, goal: Goal, age: Int): Macros {
        val share = if (age >= 65) 0.14 else activity.proteinShare
        var protein = kcal * share / 4.0
        var note = "${(share * 100).let { if (it % 1.0 == 0.0) it.toInt().toString() else fmt1(it) }}% калорийности (МР 2.3.1.0253-21)"
        val minPerKg = if (goal == Goal.LOSE) 1.2 else 0.83
        if (protein < minPerKg * weightKg) {
            protein = minPerKg * weightKg
            note = if (goal == Goal.LOSE) "1,2 г/кг — рекомендация при снижении веса" else "0,83 г/кг — минимум по МР"
        }
        val fat = kcal * 0.30 / 9.0
        val carbs = max(0.0, (kcal - protein * 4.0 - fat * 9.0) / 4.0)
        return Macros(protein, fat, carbs, note)
    }

    /** Клетчатка: МР 20–25 г/сут; ВОЗ 2023 — не менее 25 г. Цель = 25 г; для >2500 ккал — 14 г/1000 ккал (рекомендация, IOM). */
    fun fiberTarget(kcal: Double): Double = max(25.0, if (kcal > 2500) kcal / 1000.0 * 14.0 else 25.0).let { min(it, 40.0) }

    /**
     * Вода в виде напитков — МР 2.3.1.0253-21, табл. 7 (верхняя граница диапазона для группы КФА; ИМТ 20–25).
     * Общая потребность (с пищей) — 1 мл/ккал (МР п.4.3, умеренный климат). EFSA 2010: 2,0 л ж / 2,5 л м (вся вода).
     */
    fun drinksMl(sex: Sex, activity: Activity, age: Int): Double {
        if (age >= 65) return if (sex == Sex.MALE) 1500.0 else 1200.0
        return if (sex == Sex.MALE) when (activity) {
            Activity.G1 -> 1400.0; Activity.G2 -> 1600.0; Activity.G3 -> 1900.0; Activity.G4 -> 2200.0
        } else when (activity) {
            Activity.G1 -> 1100.0; Activity.G2 -> 1300.0; Activity.G3 -> 1500.0; Activity.G4 -> 1800.0
        }
    }

    fun totalWaterMl(tdeeKcal: Double): Double = tdeeKcal * 1.0

    fun efsaTotalWaterMl(sex: Sex) = if (sex == Sex.MALE) 2500.0 else 2000.0

    // ---------- helpers ----------
    fun fmt1(x: Double): String = String.format(java.util.Locale("ru"), "%.1f", x)
    fun fmt2(x: Double): String = String.format(java.util.Locale("ru"), "%.2f", x)
    fun r(x: Double) = x.roundToInt()
}

enum class Level { OK, WARN, BAD }

/** Полный набор рассчитанных параметров для профиля. */
data class Calc(
    val bmi: Double, val bmiClass: String, val bmiLevel: Level,
    val healthyMin: Double, val healthyMax: Double,
    val bmrMifflin: Double, val bmrHarris: Double, val bmrKatch: Double?, val bmrUsed: Double, val bmrUsedName: String,
    val tdee: Double, val target: Formulas.Target, val macros: Formulas.Macros,
    val fiberG: Double, val waterMl: Double, val totalWaterMl: Double,
    val whtr: Double?, val whr: Double?, val navyFat: Double?, val fatUsed: Double?,
    val weeks: Double?, val warnings: List<String>
) {
    companion object {
        fun of(p: Profile): Calc {
            val bmi = Formulas.bmi(p.weightKg, p.heightCm)
            val (hMin, hMax) = Formulas.healthyWeightRange(p.heightCm)
            val mif = Formulas.mifflin(p.sex, p.weightKg, p.heightCm, p.age)
            val hb = Formulas.harrisBenedict(p.sex, p.weightKg, p.heightCm, p.age)
            val navy = if (p.neckCm != null && p.waistCm != null) Formulas.navyBodyFat(p.sex, p.heightCm, p.neckCm, p.waistCm, p.hipCm)
                ?.takeIf { it in 2.0..70.0 } else null
            val fat = p.bodyFatPct ?: navy
            val katch = fat?.let { Formulas.katchMcArdle(p.weightKg, it) }
            val useKatch = p.bodyFatPct != null
            val bmr = if (useKatch && katch != null) katch else mif
            val bmrName = if (useKatch && katch != null) "Кэтч–МакАрдл (по вашему % жира)" else "Миффлин–Сан Жеор"
            val tdee = Formulas.tdee(bmr, p.activity.pal)
            val target = Formulas.targetCalories(p.sex, tdee, p.goal, p.lossRate)
            val macros = Formulas.macros(target.kcal, p.weightKg, p.activity, p.goal, p.age)
            val weeks = if (p.goal == Goal.KEEP) null else Formulas.weeksToGoal(p.weightKg, p.targetWeightKg, target.weeklyChangeKg)
            val w = mutableListOf<String>()
            if (p.age < 18) w += "Возраст до 18 лет: формулы для взрослых неприменимы — обратитесь к педиатру."
            if (bmi < 18.5) w += "ИМТ ниже 18,5: снижать вес не следует, нужна консультация врача."
            if (bmi >= 35) w += "ИМТ 35 и выше: программу снижения веса стоит согласовать с врачом."
            if (p.goal == Goal.LOSE && p.targetWeightKg < hMin) w += "Желаемый вес ниже нижней границы нормы (ИМТ 18,5)."
            if (target.floorApplied) w += "Калорийность ограничена безопасным минимумом ${Formulas.r(Formulas.calorieFloor(p.sex))} ккал — темп снижения будет медленнее."
            return Calc(bmi, Formulas.bmiClass(bmi), Formulas.bmiLevel(bmi), hMin, hMax, mif, hb, katch, bmr, bmrName,
                tdee, target, macros, Formulas.fiberTarget(target.kcal), Formulas.drinksMl(p.sex, p.activity, p.age), Formulas.totalWaterMl(tdee),
                p.waistCm?.let { Formulas.whtr(it, p.heightCm) },
                if (p.waistCm != null && p.hipCm != null) Formulas.whr(p.waistCm, p.hipCm) else null,
                navy, fat, weeks, w)
        }
    }
}
