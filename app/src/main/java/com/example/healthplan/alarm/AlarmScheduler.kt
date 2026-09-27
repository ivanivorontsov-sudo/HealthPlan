package com.example.healthplan.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.healthplan.MainActivity
import com.example.healthplan.core.NextTime
import com.example.healthplan.data.EventItem
import com.example.healthplan.data.Occurrence
import com.example.healthplan.data.Store
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планирование «будильников» через AlarmManager.setAlarmClock (точно, срабатывает в Doze).
 * Для каждого включённого события и каждого его времени — своя PendingIntent (requestCode = id*10 + индекс).
 * Часовой пояс — текущий пояс устройства (ZoneId.systemDefault()).
 */
object AlarmScheduler {
    const val EXTRA_EVENT = "event_id"
    const val EXTRA_INDEX = "time_index"
    const val EXTRA_AT = "at_millis"
    const val EXTRA_SNOOZE = "snooze"
    private const val SNOOZE_BASE = 500_000

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    data class Planned(val occurrence: Occurrence, val at: LocalDateTime)

    /** Все ближайшие срабатывания (по одному на каждое время каждого включённого события). */
    fun upcoming(store: Store, now: LocalDateTime = LocalDateTime.now()): List<Planned> {
        val slots = store.mealSlots()
        return store.events.filter { it.enabled }.flatMap { e ->
            store.resolvedTimes(e, slots).mapIndexedNotNull { i, t ->
                NextTime.next(now, t, e.days)?.let { Planned(Occurrence(e, i, t), it) }
            }
        }.sortedBy { it.at }
    }

    fun scheduleAll(ctx: Context) {
        val store = Store.get(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        val old = prefs.getStringSet("codes", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        val planned = upcoming(store)
        val codes = mutableSetOf<Int>()
        for (p in planned) {
            val code = p.occurrence.event.id * 10 + p.occurrence.timeIndex
            codes += code
            val millis = p.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setClock(ctx, am, code, millis, p.occurrence.event, p.occurrence.timeIndex, false)
        }
        for (c in old - codes) am.cancel(pending(ctx, c, null, 0, 0, false, PendingIntent.FLAG_NO_CREATE) ?: continue)
        prefs.edit().putStringSet("codes", codes.map { it.toString() }.toSet()).apply()
    }

    fun snooze(ctx: Context, eventId: Int, index: Int, minutes: Int = 10, seconds: Int = 0) {
        val e = Store.get(ctx).events.firstOrNull { it.id == eventId } ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + minutes * 60_000L + seconds * 1000L
        setClock(ctx, am, SNOOZE_BASE + eventId * 10 + index, at, e, index, true)
    }

    private fun setClock(ctx: Context, am: AlarmManager, code: Int, millis: Long, e: EventItem, idx: Int, snooze: Boolean) {
        val op = pending(ctx, code, e, idx, millis, snooze, 0)!!
        val show = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (canExact(ctx)) am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), op)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, op)   // запасной вариант без точности
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, op)
        }
    }

    private fun pending(ctx: Context, code: Int, e: EventItem?, idx: Int, at: Long, snooze: Boolean, extraFlags: Int): PendingIntent? {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction("com.example.healthplan.ALARM_$code")
        if (e != null) i.putExtra(EXTRA_EVENT, e.id).putExtra(EXTRA_INDEX, idx).putExtra(EXTRA_AT, at).putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or extraFlags)
    }
}
