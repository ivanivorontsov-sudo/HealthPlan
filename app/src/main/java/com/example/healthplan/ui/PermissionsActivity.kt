package com.example.healthplan.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.alarm.Notifier

class PermissionsActivity : BaseActivity() {
    companion object {
        fun notifOk(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        fun batteryOk(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

        fun missing(c: Context): List<String> = buildList {
            if (!notifOk(c)) add("Уведомления выключены")
            if (!AlarmScheduler.canExact(c)) add("Нет разрешения на точные будильники")
            if (!Notifier.canFullScreen(c)) add("Нет разрешения на полноэкранные оповещения")
            if (!batteryOk(c)) add("Включена оптимизация батареи (звонок может опаздывать)")
        }
    }

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onResume() { super.onResume(); AlarmScheduler.scheduleAll(this); render() }

    private fun render(): Unit = screen {
        h1("Разрешения")
        muted("Чтобы телефон звонил в момент события даже на заблокированном экране, выдайте все разрешения ниже. Если строка зелёная — всё в порядке.", 15f)
        fun item(title: String, ok: Boolean, desc: String, action: () -> Unit) = card(stroke = if (ok) Ui.GREEN else Ui.RED, topDp = 10) {
            text((if (ok) "✓ " else "✗ ") + title, 18f, true, if (ok) Ui.GREEN else Ui.RED)
            muted(desc, 14f)
            if (!ok) button("Выдать") { action() }
        }
        item("Уведомления", notifOk(this@PermissionsActivity), "Нужны для звонка и напоминаний.") {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        item("Точные будильники", AlarmScheduler.canExact(this@PermissionsActivity), "Чтобы звонок срабатывал минута в минуту, даже в режиме энергосбережения (Doze).") {
            if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        item("Полноэкранный звонок", Notifier.canFullScreen(this@PermissionsActivity), "Android 14+: разрешите «Полноэкранные уведомления», иначе вместо звонка будет только всплывающее уведомление.") {
            if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
        item("Без ограничений батареи", batteryOk(this@PermissionsActivity), "Исключение из оптимизации батареи — звонки не будут опаздывать. На Xiaomi/Huawei/Samsung также включите «Автозапуск» и уберите ограничения фоновой работы в настройках приложения.") {
            try {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        button("Открыть настройки приложения", outlined = true) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        muted("Громкость звонка — это громкость будильника телефона. Режим «Не беспокоить» может заглушать звонок — добавьте приложение в исключения.", 13f)
    }
}
