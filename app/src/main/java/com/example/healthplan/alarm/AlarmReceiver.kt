package com.example.healthplan.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.healthplan.data.Store

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_EVENT, -1)
        val idx = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0)
        val e = Store.get(ctx).events.firstOrNull { it.id == id }
        if (e != null && e.enabled) {
            if (e.ring) {
                val s = Intent(ctx, AlarmService::class.java).putExtras(intent)
                try { ContextCompat.startForegroundService(ctx, s) }
                catch (t: Throwable) { Notifier.showReminder(ctx, e, idx, missed = false) }
            } else Notifier.showReminder(ctx, e, idx, missed = false)
        }
        AlarmScheduler.scheduleAll(ctx)   // перепланировать следующее срабатывание
    }
}

/** Перепланирование после перезагрузки, смены времени/часового пояса, обновления приложения. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { AlarmScheduler.scheduleAll(ctx) }
}

/** Кнопки уведомления: «Сделано», «Отложить 10 мин». */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_EVENT, -1)
        val idx = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0)
        when (intent.action) {
            Notifier.ACTION_DONE -> Notifier.markDone(ctx, id, idx, intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0))
            Notifier.ACTION_SNOOZE -> AlarmScheduler.snooze(ctx, id, idx)
        }
        ctx.stopService(Intent(ctx, AlarmService::class.java))
        Notifier.cancel(ctx, id, idx)
    }
}
