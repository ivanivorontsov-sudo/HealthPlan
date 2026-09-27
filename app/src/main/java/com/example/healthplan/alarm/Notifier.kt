package com.example.healthplan.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.healthplan.MainActivity
import com.example.healthplan.R
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.EventItem
import com.example.healthplan.data.Store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Notifier {
    const val CH_RING = "ring_v1"
    const val CH_REMIND = "remind_v1"
    const val ACTION_DONE = "com.example.healthplan.DONE"
    const val ACTION_SNOOZE = "com.example.healthplan.SNOOZE"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ring = NotificationChannel(CH_RING, "Звонки-напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Полноэкранный звонок в момент события (звук воспроизводит само приложение)"
            setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val remind = NotificationChannel(CH_REMIND, "Уведомления о событиях", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Обычные уведомления и пропущенные звонки"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(ring, remind))
    }

    fun notifId(eventId: Int, idx: Int) = 10_000 + eventId * 10 + idx

    fun actionIntent(ctx: Context, action: String, e: EventItem, idx: Int, at: Long): PendingIntent =
        PendingIntent.getBroadcast(ctx, (action.hashCode() and 0xffff) * 1000 + e.id * 10 + idx,
            Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_EVENT, e.id)
                .putExtra(AlarmScheduler.EXTRA_INDEX, idx).putExtra(AlarmScheduler.EXTRA_AT, at),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun title(e: EventItem) = "${e.type.emoji} ${e.title}"

    fun text(ctx: Context, e: EventItem, idx: Int): String {
        val store = Store.get(ctx)
        val t = store.resolvedTimes(e).getOrNull(idx)?.let { MealSchedule.fmt(it) } ?: ""
        return listOf(t, e.note.lineSequence().firstOrNull() ?: "").filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun showReminder(ctx: Context, e: EventItem, idx: Int, missed: Boolean, at: Long = System.currentTimeMillis()) {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle((if (missed) "Пропущено: " else "") + title(e))
            .setContentText(text(ctx, e, idx))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text(ctx, e, idx) + if (e.note.isNotBlank()) "\n" + e.note else ""))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, "Сделано", actionIntent(ctx, ACTION_DONE, e, idx, at))
            .addAction(0, "Отложить 10 мин", actionIntent(ctx, ACTION_SNOOZE, e, idx, at))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(notifId(e.id, idx), n) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, eventId: Int, idx: Int) {
        NotificationManagerCompat.from(ctx).cancel(notifId(eventId, idx))
        NotificationManagerCompat.from(ctx).cancel(AlarmService.NOTIF_ID)
    }

    fun markDone(ctx: Context, eventId: Int, idx: Int, at: Long) {
        val date = (if (at > 0) Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now()).toString()
        Store.get(ctx).setDone(date, "$eventId:$idx", true)
    }

    fun canFullScreen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
