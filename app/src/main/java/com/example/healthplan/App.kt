package com.example.healthplan

import android.app.Application
import android.content.Context
import com.example.healthplan.alarm.Notifier
import com.example.healthplan.core.Calc
import com.example.healthplan.core.Days
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.EventItem
import com.example.healthplan.data.EventType
import com.example.healthplan.data.Occurrence
import com.example.healthplan.data.Store
import com.example.healthplan.food.FoodDb
import com.example.healthplan.food.N
import java.time.LocalDate
import java.time.LocalDateTime

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    companion object {
        @Volatile private var db: FoodDb? = null
        fun foods(ctx: Context): FoodDb {
            val d = db ?: synchronized(this) {
                db ?: FoodDb(ctx.assets.open("foods.csv").use { FoodDb.parseCsv(it) }).also { db = it }
            }
            d.custom = Store.get(ctx).customFoods
            return d
        }
    }
}

object Texts {
    const val TEETH = "2 раза в день по 2 минуты, паста с фтором 1350–1500 ppm. После чистки сплюнуть, не полоскать (NHS). Вечером — последнее, что делаете перед сном."
    const val COLD = """Закаливание — только постепенно и без перерывов (Роспотребнадзор, Минздрав):
1–2 нед.: умывание прохладной водой, снижая t на ~1 °C в день.
3–4 нед.: обтирания влажным полотенцем.
Затем: контрастный душ — 30–60 с прохладной воды в конце, постепенно дольше.
При простуде, температуре, обострениях — пауза, после перерыва начинать сначала.
Противопоказания/осторожно: гипертония, болезни сердца и сосудов, астма, аллергия на холод — сначала к врачу."""
    const val WATER = "Стакан воды (~250 мл). Норма напитков — на экране «Параметры»."
    const val WEIGH = "Утром, натощак, после туалета, в одной и той же одежде."
    const val EXERCISE = "10–15 минут лёгкой зарядки. ВОЗ: взрослым 150–300 мин умеренной активности в неделю + силовые 2 раза в неделю."
    const val SLEEP = "Пора готовиться ко сну. Взрослым рекомендуется 7–9 часов сна (рекомендация)."
}

/** Создание набора событий по умолчанию и синхронизация приёмов пищи с режимом. */
object Defaults {
    fun ensureEvents(store: Store) {
        val p = store.profile
        val slots = store.mealSlots(p)
        if (!store.eventsInitialized) {
            val list = mutableListOf<EventItem>()
            slots.forEach { s -> list += EventItem(store.nextEventId(), EventType.MEAL, s.name, emptyList(), mealIndex = s.index) }
            val breakfast = slots.first().timeMin
            list += EventItem(store.nextEventId(), EventType.TEETH, "Чистка зубов", listOf((breakfast + 30).mod(1440), (p.sleepMin - 30).mod(1440)), note = Texts.TEETH)
            list += EventItem(store.nextEventId(), EventType.COLD, "Закаливание", listOf((p.wakeMin + 20).mod(1440)), enabled = false, note = Texts.COLD)
            list += EventItem(store.nextEventId(), EventType.WATER, "Вода", waterTimes(p.wakeMin, p.sleepMin), ring = false, note = Texts.WATER)
            list += EventItem(store.nextEventId(), EventType.WEIGH, "Взвешивание", listOf((p.wakeMin + 10).mod(1440)), days = 0b0000001, ring = false, note = Texts.WEIGH)
            list += EventItem(store.nextEventId(), EventType.EXERCISE, "Зарядка", listOf((p.wakeMin + 15).mod(1440)), note = Texts.EXERCISE)
            list += EventItem(store.nextEventId(), EventType.SLEEP, "Отбой", listOf((p.sleepMin - 15).mod(1440)), note = Texts.SLEEP)
            store.events = list
        } else syncMeals(store)
    }

    /** Приёмы пищи: число событий = числу приёмов; названия из режима. */
    fun syncMeals(store: Store) {
        val slots = store.mealSlots()
        val ev = store.events.toMutableList()
        val meals = ev.filter { it.type == EventType.MEAL }
        val keep = mutableListOf<EventItem>()
        slots.forEach { s ->
            val existing = meals.firstOrNull { it.mealIndex == s.index }
            keep += existing?.copy(title = s.name) ?: EventItem(store.nextEventId(), EventType.MEAL, s.name, emptyList(), mealIndex = s.index)
        }
        val others = ev.filter { it.type != EventType.MEAL }
        store.events = keep + others
    }

    fun waterTimes(wake: Int, sleep: Int): List<Int> {
        val awake = MealSchedule.awakeMinutes(wake, sleep)
        val n = 6
        return (0 until n).map { i -> (wake + 30 + (awake - 90) * i / (n - 1)).let { ((it + 2) / 5 * 5).mod(1440) } }
    }

    fun retimeFromProfile(store: Store) {
        val p = store.profile; val slots = store.mealSlots(p)
        store.events = store.events.map { e ->
            when (e.type) {
                EventType.TEETH -> e.copy(times = listOf((slots.first().timeMin + 30).mod(1440), (p.sleepMin - 30).mod(1440)))
                EventType.COLD -> e.copy(times = listOf((p.wakeMin + 20).mod(1440)))
                EventType.WATER -> e.copy(times = waterTimes(p.wakeMin, p.sleepMin))
                EventType.WEIGH -> e.copy(times = listOf((p.wakeMin + 10).mod(1440)))
                EventType.EXERCISE -> e.copy(times = listOf((p.wakeMin + 15).mod(1440)))
                EventType.SLEEP -> e.copy(times = listOf((p.sleepMin - 15).mod(1440)))
                else -> e
            }
        }
    }
}

object Day {
    fun today() = LocalDate.now().toString()

    /** Срабатывания на дату, отсортированные по времени. */
    fun occurrences(store: Store, date: LocalDate): List<Occurrence> {
        val slots = store.mealSlots()
        return store.events.filter { it.enabled && Days.has(it.days, date.dayOfWeek) }.flatMap { e ->
            store.resolvedTimes(e, slots).mapIndexed { i, t -> Occurrence(e, i, t) }
        }.sortedBy { it.timeMin }
    }

    fun mealTargets(calc: Calc, store: Store): List<N> {
        val m = calc.macros
        return store.mealSlots().map { s -> N(calc.target.kcal * s.share, m.proteinG * s.share, m.fatG * s.share, m.carbsG * s.share, calc.fiberG * s.share) }
    }

    fun nowMin(now: LocalDateTime = LocalDateTime.now()) = now.hour * 60 + now.minute
}

object DayNutrition {
    fun totals(store: Store, date: String): N = store.diaryFor(date).fold(N.ZERO) { acc, e ->
        acc + N(e.kcal100, e.p100, e.f100, e.c100, e.fib100) * (e.grams / 100.0)
    }

    /** Проценты нормы: ккал, Б, Ж, У, клетчатка, вода. */
    fun percents(store: Store, calc: Calc, date: String): List<Double> {
        val t = totals(store, date)
        val m = calc.macros
        val pct = { v: Double, tg: Double -> if (tg <= 0) 0.0 else v / tg * 100 }
        return listOf(pct(t.kcal, calc.target.kcal), pct(t.p, m.proteinG), pct(t.f, m.fatG), pct(t.c, m.carbsG),
            pct(t.fib, calc.fiberG), pct(store.waterMl(date).toDouble(), calc.waterMl))
    }
}
