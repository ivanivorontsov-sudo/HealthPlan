package com.example.healthplan.core

import kotlin.math.roundToInt

data class MealSlot(val index: Int, val name: String, val timeMin: Int, val share: Double)

/**
 * Режим питания («рекомендация»): первый приём ≈ через 45 мин после подъёма (в пределах часа),
 * последний — за 3 ч до сна (минимум за 2 ч, если окно короткое), остальные — равномерно.
 * Доли калорийности — традиционное распределение отечественной диетологии.
 */
object MealSchedule {
    const val FIRST_AFTER_WAKE = 45
    const val LAST_BEFORE_SLEEP = 180
    const val LAST_BEFORE_SLEEP_MIN = 120

    fun names(n: Int): List<String> = when (n) {
        3 -> listOf("Завтрак", "Обед", "Ужин")
        4 -> listOf("Завтрак", "Обед", "Полдник", "Ужин")
        5 -> listOf("Завтрак", "Второй завтрак", "Обед", "Полдник", "Ужин")
        else -> listOf("Завтрак", "Второй завтрак", "Обед", "Полдник", "Ужин", "Второй ужин")
    }

    fun shares(n: Int): List<Double> = when (n) {
        3 -> listOf(0.30, 0.40, 0.30)
        4 -> listOf(0.25, 0.35, 0.15, 0.25)
        5 -> listOf(0.25, 0.10, 0.35, 0.10, 0.20)
        else -> listOf(0.20, 0.10, 0.30, 0.10, 0.20, 0.10)
    }

    /** Длительность бодрствования (мин), учитывая переход через полночь. */
    fun awakeMinutes(wakeMin: Int, sleepMin: Int): Int {
        var d = sleepMin - wakeMin
        if (d <= 0) d += 1440
        return d
    }

    fun build(wakeMin: Int, sleepMin: Int, count: Int): List<MealSlot> {
        val n = count.coerceIn(3, 6)
        val awake = awakeMinutes(wakeMin, sleepMin)
        val first = FIRST_AFTER_WAKE.coerceAtMost(awake / 4)
        var last = awake - LAST_BEFORE_SLEEP
        val minGap = 90
        if (last - first < (n - 1) * minGap) last = awake - LAST_BEFORE_SLEEP_MIN
        if (last <= first) last = first + (n - 1) * 60
        val step = (last - first).toDouble() / (n - 1)
        val nm = names(n); val sh = shares(n)
        return (0 until n).map { i ->
            val rel = first + (step * i).roundToInt()
            val rounded = ((rel + 2) / 5) * 5          // округление до 5 минут
            MealSlot(i, nm[i], (wakeMin + rounded).mod(1440), sh[i])
        }
    }

    fun fmt(min: Int): String = "%02d:%02d".format(min.mod(1440) / 60, min.mod(1440) % 60)
    fun parse(s: String): Int? {
        val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(s) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        return h * 60 + mi
    }
}
