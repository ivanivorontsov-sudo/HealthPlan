package com.example.healthplan.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Дни недели — битовая маска: бит 0 = понедельник … бит 6 = воскресенье. */
object Days {
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111
    val SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    fun has(mask: Int, d: DayOfWeek) = (mask shr (d.value - 1)) and 1 == 1
    fun label(mask: Int): String = when (mask and ALL) {
        ALL -> "ежедневно"; WEEKDAYS -> "по будням"; 0b1100000 -> "по выходным"; 0 -> "никогда"
        else -> SHORT.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(", ")
    }
}

object NextTime {
    /** Ближайший момент строго после [now] в [timeMin] (минуты от полуночи) в разрешённый день недели. */
    fun next(now: LocalDateTime, timeMin: Int, daysMask: Int): LocalDateTime? {
        if (daysMask and Days.ALL == 0) return null
        val t = LocalTime.of(timeMin.mod(1440) / 60, timeMin.mod(1440) % 60)
        for (add in 0..7) {
            val date = now.toLocalDate().plusDays(add.toLong())
            val cand = LocalDateTime.of(date, t)
            if (cand.isAfter(now) && Days.has(daysMask, date.dayOfWeek)) return cand
        }
        return null
    }
}
