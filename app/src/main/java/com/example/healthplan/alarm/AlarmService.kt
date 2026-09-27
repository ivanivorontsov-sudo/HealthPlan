package com.example.healthplan.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.healthplan.R
import com.example.healthplan.data.Store

/**
 * «Звонок»: зацикленная мелодия будильника (USAGE_ALARM) + вибрация, полноэкранный интент на RingActivity.
 * Через [RING_MS] автоматически замолкает и оставляет уведомление «Пропущено».
 */
class AlarmService : Service() {
    companion object {
        const val NOTIF_ID = 4242
        const val RING_MS = 60_000L
        const val ACTION_STOP = "com.example.healthplan.STOP_RING"
        @Volatile var current: Pair<Int, Int>? = null
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var eventId = -1; private var idx = 0; private var at = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRinging(); stopSelf(); return START_NOT_STICKY }
        eventId = intent?.getIntExtra(AlarmScheduler.EXTRA_EVENT, -1) ?: -1
        idx = intent?.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0) ?: 0
        at = intent?.getLongExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis()) ?: System.currentTimeMillis()
        val e = Store.get(this).events.firstOrNull { it.id == eventId }
        if (e == null) { stopSelf(); return START_NOT_STICKY }
        Notifier.ensureChannels(this)
        current = eventId to idx

        val full = Intent(this, RingActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_EVENT, eventId).putExtra(AlarmScheduler.EXTRA_INDEX, idx).putExtra(AlarmScheduler.EXTRA_AT, at)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val fullPi = PendingIntent.getActivity(this, 7, full, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, Notifier.CH_RING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Notifier.title(e))
            .setContentText(Notifier.text(this, e, idx))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setOngoing(true)
            .addAction(0, "Сделано", Notifier.actionIntent(this, Notifier.ACTION_DONE, e, idx, at))
            .addAction(0, "Отложить 10 мин", Notifier.actionIntent(this, Notifier.ACTION_SNOOZE, e, idx, at))
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)

        startRinging()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            stopRinging()
            Notifier.showReminder(this, e, idx, missed = true, at = at)
            sendBroadcast(Intent(RingActivity.ACTION_FINISH).setPackage(packageName))
            stopSelf()
        }, RING_MS)
        return START_NOT_STICKY
    }

    private fun startRinging() {
        stopRinging()
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "healthplan:ring").apply { acquire(RING_MS + 5_000) }
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare(); start()
            }
        } catch (t: Throwable) { player = null }
        vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    private fun stopRinging() {
        try { player?.stop() } catch (_: Throwable) {}
        player?.release(); player = null
        vibrator?.cancel()
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        current = null
        sendBroadcast(Intent(RingActivity.ACTION_FINISH).setPackage(packageName))
        super.onDestroy()
    }
}
