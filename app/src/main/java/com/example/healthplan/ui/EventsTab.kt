package com.example.healthplan.ui

import android.content.Intent
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.healthplan.*
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.core.Days
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.EventItem
import com.example.healthplan.data.EventType

class EventsTab(a: MainActivity) : Tab(a) {
    override fun build(): View {
        val s = a.store
        val col = Ui.column(a)
        col.h1("События и напоминания")
        col.muted("Отметьте галочкой нужные события. «Звонок» — телефон звонит как будильник на весь экран (даже на заблокированном экране), «уведомление» — обычное уведомление.", 14f)
        val missing = PermissionsActivity.missing(a)
        col.card(stroke = if (missing.isEmpty()) Ui.GREEN else Ui.RED) {
            if (missing.isEmpty()) text("✓ Все разрешения для звонков выданы", 17f, true, Ui.GREEN)
            else { text("⚠ Не хватает разрешений:", 17f, true, Ui.RED); muted(missing.joinToString("\n") { "• $it" }) }
            button("Разрешения", outlined = true) { a.startActivity(Intent(a, PermissionsActivity::class.java)) }
        }
        val slots = s.mealSlots()
        s.events.forEach { e ->
            col.card(topDp = 10) {
                val r = row {
                    addView(CheckBox(context).apply {
                        isChecked = e.enabled; scaleX = 1.3f; scaleY = 1.3f
                        setOnCheckedChangeListener { _, b ->
                            s.events = s.events.map { if (it.id == e.id) it.copy(enabled = b) else it }
                            AlarmScheduler.scheduleAll(a); a.refresh()
                        }
                    }, LinearLayout.LayoutParams(Ui.dp(context, 56), Ui.dp(context, 56)))
                    val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    box.text("${e.type.emoji} ${e.title}", 19f, true, topDp = 0)
                    val times = s.resolvedTimes(e, slots).joinToString(", ") { MealSchedule.fmt(it) }
                    box.text(times.ifEmpty { "время не задано" }, 22f, true, Ui.PRIMARY, topDp = 2)
                    box.text("${Days.label(e.days)} · ${if (e.ring) "🔔 звонок" else "🔕 уведомление"}", 15f, color = Ui.MUTED, topDp = 0)
                    addView(box, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(TextView(context).apply { text = "›"; textSize = 32f; setTextColor(Ui.MUTED) })
                }
                r.setOnClickListener { a.startActivity(Intent(a, EventEditActivity::class.java).putExtra("id", e.id)) }
            }
        }
        col.button("+ Своё событие") {
            val id = s.nextEventId()
            s.events = s.events + EventItem(id, EventType.CUSTOM, "Своё событие", listOf(12 * 60))
            a.startActivity(Intent(a, EventEditActivity::class.java).putExtra("id", id))
        }
        col.button("Пересчитать время по подъёму и отбою", outlined = true) {
            Dialogs.confirm(a, "Пересчитать?", "Время чистки зубов, воды, зарядки, закаливания, взвешивания и отбоя будет рассчитано заново от времени подъёма и отбоя.") {
                Defaults.retimeFromProfile(s); AlarmScheduler.scheduleAll(a); a.refresh()
            }
        }
        col.button("Проверить звонок (через 10 секунд)", outlined = true) {
            val e = s.events.firstOrNull { it.ring && it.enabled } ?: s.events.firstOrNull()
            if (e != null) {
                AlarmScheduler.snooze(a, e.id, 0, 0, 10)
                Toast.makeText(a, "Звонок «${e.title}» через ~10 секунд. Можно заблокировать экран.", Toast.LENGTH_LONG).show()
            }
        }
        return Ui.scroll(a, col)
    }
}
