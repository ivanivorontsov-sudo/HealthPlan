package com.example.healthplan.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.core.Days
import com.example.healthplan.core.MealSchedule
import com.example.healthplan.data.EventItem
import com.example.healthplan.data.EventType
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class EventEditActivity : BaseActivity() {
    private lateinit var e: EventItem
    private var titleText = ""; private var noteText = ""
    private var noteEt: com.google.android.material.textfield.TextInputEditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", -1)
        e = store.events.firstOrNull { it.id == id } ?: run { finish(); return }
        titleText = e.title; noteText = e.note
        render()
    }

    private fun save(n: EventItem) {
        e = n
        store.events = store.events.map { if (it.id == n.id) n else it }
        AlarmScheduler.scheduleAll(this)
    }

    private fun render(): Unit = screen {
        h1("${e.type.emoji} ${e.type.title}")
        val titleEt = if (e.type != EventType.MEAL) input("Название", titleText, number = false) else null
        if (e.type == EventType.MEAL) text(e.title, 20f, true)

        card {
            text("Время", 18f, true)
            val times = store.resolvedTimes(e)
            if (e.type == EventType.MEAL) {
                muted("Время приёма пищи берётся из режима питания; изменение здесь меняет и режим.", 14f)
                button(times.firstOrNull()?.let { MealSchedule.fmt(it) } ?: "--:--", outlined = true) {
                    Dialogs.time(this@EventEditActivity, times.firstOrNull() ?: 720) { m ->
                        store.mealTimeOverrides = store.mealTimeOverrides + (e.mealIndex to m); save(e); keep(titleEt); render()
                    }
                }
            } else {
                times.forEachIndexed { i, t ->
                    row {
                        button(MealSchedule.fmt(t), outlined = true, weight = 2f) {
                            Dialogs.time(this@EventEditActivity, t) { m -> keep(titleEt); save(e.copy(times = e.times.toMutableList().also { it[i] = m }.sorted())); render() }
                        }
                        button("Удалить", outlined = true, weight = 1f) { keep(titleEt); save(e.copy(times = e.times.filterIndexed { j, _ -> j != i })); render() }
                    }
                }
                button("+ Добавить время") {
                    Dialogs.time(this@EventEditActivity, times.lastOrNull()?.plus(60)?.mod(1440) ?: 720) { m -> keep(titleEt); save(e.copy(times = (e.times + m).distinct().sorted())); render() }
                }
            }
        }
        card {
            text("Дни недели", 18f, true)
            val cg = ChipGroup(context)
            Days.SHORT.forEachIndexed { i, d ->
                cg.addView(Chip(context).apply {
                    text = d; isCheckable = true; isChecked = (e.days shr i) and 1 == 1; textSize = 17f
                    setOnCheckedChangeListener { _, b -> keep(titleEt); save(e.copy(days = if (b) e.days or (1 shl i) else e.days and (1 shl i).inv())) }
                })
            }
            addView(cg, lp(topDp = 6))
            row {
                button("Каждый день", outlined = true, weight = 1f) { keep(titleEt); save(e.copy(days = Days.ALL)); render() }
                button("Будни", outlined = true, weight = 1f) { keep(titleEt); save(e.copy(days = Days.WEEKDAYS)); render() }
            }
        }
        card {
            text("Как напоминать", 18f, true)
            val g = RadioGroup(context)
            g.addView(RadioButton(context).apply { id = 1; text = "🔔 Звонок — на весь экран, мелодия будильника и вибрация (до 1 мин)"; textSize = 17f })
            g.addView(RadioButton(context).apply { id = 2; text = "🔕 Просто уведомление"; textSize = 17f })
            g.check(if (e.ring) 1 else 2)
            g.setOnCheckedChangeListener { _, id -> keep(titleEt); save(e.copy(ring = id == 1)) }
            addView(g, lp(topDp = 4))
            check("Событие включено", e.enabled) { keep(titleEt); save(e.copy(enabled = it)) }
        }
        card {
            text("Заметка / подсказка", 18f, true)
            noteEt = input("Текст на экране звонка", noteText, number = false).apply { isSingleLine = false; minLines = 3 }
        }
        button("Сохранить") {
            keep(titleEt)
            save(e.copy(title = if (e.type == EventType.MEAL) e.title else titleText.ifBlank { e.type.title }, note = noteText))
            finish()
        }
        if (e.type == EventType.CUSTOM) button("Удалить событие", outlined = true) {
            Dialogs.confirm(this@EventEditActivity, "Удалить событие?", e.title) {
                store.events = store.events.filter { it.id != e.id }; AlarmScheduler.scheduleAll(this@EventEditActivity); finish()
            }
        }
    }

    private fun keep(et: com.google.android.material.textfield.TextInputEditText?) {
        if (et != null) { titleText = et.text.toString(); e = e.copy(title = titleText.ifBlank { e.type.title }) }
        noteEt?.let { noteText = it.text.toString(); e = e.copy(note = noteText) }
    }
}
