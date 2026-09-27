package com.example.healthplan.data

import com.example.healthplan.core.Days

enum class EventType(val title: String, val emoji: String) {
    MEAL("Приём пищи", "🍽"), TEETH("Чистка зубов", "🪥"), COLD("Закаливание", "🚿"), WATER("Вода", "💧"),
    WEIGH("Взвешивание", "⚖"), EXERCISE("Зарядка", "🤸"), SLEEP("Отбой", "🌙"), CUSTOM("Своё событие", "🔔")
}

/**
 * Событие расписания. [times] — минуты от полуночи; [days] — маска дней (бит0=Пн).
 * Для MEAL время берётся из режима питания по [mealIndex]. [ring] = true — «звонок» на весь экран, иначе уведомление.
 */
data class EventItem(
    val id: Int,
    val type: EventType,
    val title: String,
    val times: List<Int>,
    val days: Int = Days.ALL,
    val enabled: Boolean = true,
    val ring: Boolean = true,
    val note: String = "",
    val mealIndex: Int = -1,
)

data class DiaryEntry(
    val id: Long, val date: String, val meal: Int, val foodId: String, val name: String, val grams: Double,
    val kcal100: Double, val p100: Double, val f100: Double, val c100: Double, val fib100: Double,
)

data class WeightEntry(val date: String, val kg: Double)

/** Один срабатывающий момент: событие + индекс времени. Ключ используется для отметок «сделано». */
data class Occurrence(val event: EventItem, val timeIndex: Int, val timeMin: Int) {
    val key get() = "${event.id}:$timeIndex"
}
