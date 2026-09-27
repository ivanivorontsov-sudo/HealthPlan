package com.example.healthplan

import com.example.healthplan.core.Days
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.core.NextTime
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    private fun times(w: String, s: String, n: Int) = MealSchedule.build(MealSchedule.parse(w)!!, MealSchedule.parse(s)!!, n).map { MealSchedule.fmt(it.timeMin) }

    @Test fun fourMealsDay() {
        assertEquals(listOf("07:45", "11:50", "15:55", "20:00"), times("07:00", "23:00", 4))
        val slots = MealSchedule.build(420, 1380, 4)
        assertEquals(listOf("Завтрак", "Обед", "Полдник", "Ужин"), slots.map { it.name })
        assertEquals(1.0, slots.sumOf { it.share }, 1e-9)
    }

    @Test fun sharesSumToOne() { for (n in 3..6) assertEquals(1.0, MealSchedule.shares(n).sum(), 1e-9) }

    @Test fun sixMealsAndRounding() {
        assertEquals(listOf("09:45", "12:10", "14:40", "17:05", "19:35", "22:00"), times("09:00", "01:00", 6))
    }

    @Test fun nightShiftWrapsMidnight() {
        assertEquals(listOf("14:45", "20:55", "03:00"), times("14:00", "06:00", 3))
    }

    @Test fun shortDayFallsBackToTwoHours() {
        // 07:00–13:00: окно 6 ч; за 3 ч до сна не помещается 6 приёмов с шагом 90 мин → за 2 ч
        val t = MealSchedule.build(420, 780, 6)
        assertEquals(780 - 120, t.last().timeMin)
        assertTrue(t.zipWithNext().all { (a, b) -> b.timeMin > a.timeMin })
    }

    @Test fun lastMealBeforeSleepAndFirstAfterWake() {
        for (n in 3..6) {
            val s = MealSchedule.build(360, 1320, n)
            assertEquals(360 + 45, s.first().timeMin)
            assertTrue(1320 - s.last().timeMin >= 120)
        }
    }

    @Test fun parseAndFormat() {
        assertEquals(7 * 60 + 5, MealSchedule.parse("7:05")); assertNull(MealSchedule.parse("25:00")); assertEquals("00:00", MealSchedule.fmt(1440))
    }

    // 2026-09-28 — понедельник
    private val mon10 = LocalDateTime.of(2026, 9, 28, 10, 0)

    @Test fun nextSameDayOrTomorrow() {
        assertEquals(LocalDateTime.of(2026, 9, 28, 11, 0), NextTime.next(mon10, 11 * 60, Days.ALL))
        assertEquals(LocalDateTime.of(2026, 9, 29, 9, 0), NextTime.next(mon10, 9 * 60, Days.ALL))
        assertEquals(LocalDateTime.of(2026, 9, 29, 10, 0), NextTime.next(mon10, 10 * 60, Days.ALL))  // строго после
    }

    @Test fun nextRespectsDaysOfWeek() {
        val fri2330 = LocalDateTime.of(2026, 10, 2, 23, 30)
        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), NextTime.next(fri2330, 7 * 60, Days.WEEKDAYS))
        val sundayOnly = 1 shl 6
        assertEquals(LocalDateTime.of(2026, 10, 4, 8, 0), NextTime.next(mon10, 8 * 60, sundayOnly))
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), NextTime.next(mon10, 9 * 60, 0b0000001)) // только Пн, 9:00 уже прошло → через неделю
        assertNull(NextTime.next(mon10, 600, 0))
    }

    @Test fun nextMidnightWrap() {
        val sun2359 = LocalDateTime.of(2026, 10, 4, 23, 59, 30)
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), NextTime.next(sun2359, 0, Days.ALL))
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), NextTime.next(sun2359, 0, 0b0000001))
        assertEquals(LocalDateTime.of(2026, 10, 11, 0, 0), NextTime.next(sun2359, 0, 1 shl 6))  // только Вс, 00:00 → следующее воскресенье
        // год меняется
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 30), NextTime.next(LocalDateTime.of(2026, 12, 31, 23, 45), 30, Days.ALL))
    }

    @Test fun dayLabels() {
        assertEquals("ежедневно", Days.label(Days.ALL)); assertEquals("по будням", Days.label(Days.WEEKDAYS)); assertEquals("Пн, Ср", Days.label(0b101))
    }
}
