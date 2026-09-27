package com.example.healthplan.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.Store
import com.example.healthplan.ui.Ui
import com.google.android.material.button.MaterialButton

/** Полноэкранный «звонок» поверх экрана блокировки. */
class RingActivity : ComponentActivity() {
    companion object { const val ACTION_FINISH = "com.example.healthplan.FINISH_RING" }

    private val finisher = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { finish() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getIntExtra(AlarmScheduler.EXTRA_EVENT, -1)
        val idx = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0)
        val at = intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0)
        val store = Store.get(this)
        val e = store.events.firstOrNull { it.id == id }
        if (e == null) { finish(); return }
        val time = store.resolvedTimes(e).getOrNull(idx)?.let { MealSchedule.fmt(it) } ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B3D2E")); setPadding(Ui.dp(this@RingActivity, 24), Ui.dp(this@RingActivity, 48), Ui.dp(this@RingActivity, 24), Ui.dp(this@RingActivity, 48))
        }
        fun tv(s: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = sp; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, Ui.dp(this@RingActivity, 8), 0, Ui.dp(this@RingActivity, 8))
        }
        root.addView(tv(e.type.emoji, 72f))
        root.addView(tv(e.title, 34f, true))
        root.addView(tv(time, 56f, true))
        if (e.note.isNotBlank()) root.addView(tv(e.note, 18f))
        fun big(label: String, color: String, onClick: () -> Unit) = MaterialButton(this).apply {
            text = label; textSize = 24f; setBackgroundColor(Color.parseColor(color)); setTextColor(Color.WHITE)
            cornerRadius = Ui.dp(this@RingActivity, 20)
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@RingActivity, 88)).apply { topMargin = Ui.dp(this@RingActivity, 20) }
            setOnClickListener { onClick() }
        }
        root.addView(big("✓  Сделано", "#2E7D32") {
            Notifier.markDone(this, id, idx, at); stopRing(id, idx); finish()
        })
        root.addView(big("⏰  Отложить 10 мин", "#F57C00") {
            AlarmScheduler.snooze(this, id, idx); stopRing(id, idx); finish()
        })
        setContentView(root)
        ContextCompat.registerReceiver(this, finisher, IntentFilter(ACTION_FINISH), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(KeyguardManager::class.java)?.let { /* экран блокировки остаётся, окно поверх него */ }
    }

    private fun stopRing(id: Int, idx: Int) {
        stopService(Intent(this, AlarmService::class.java))
        Notifier.cancel(this, id, idx)
    }

    override fun onDestroy() { runCatching { unregisterReceiver(finisher) }; super.onDestroy() }
}
