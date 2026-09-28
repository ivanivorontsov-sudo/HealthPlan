package com.example.healthplan.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.healthplan.App
import com.example.healthplan.Defaults
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.data.Backup
import com.example.healthplan.data.DataSafety
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * «Данные»: экспорт всех данных в один JSON-файл и импорт из него (Storage Access Framework — без разрешений на память),
 * страховочные копии во внутреннем хранилище.
 */
class DataActivity : BaseActivity() {
    private var pending: Backup.Parsed? = null
    private var pendingName: String = ""
    private var copyFrom: File? = null

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { doExport(it) } }
    private val copyDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val src = copyFrom ?: return@registerForActivityResult
        if (uri != null) try {
            contentResolver.openOutputStream(uri, "wt")!!.use { o -> src.inputStream().use { it.copyTo(o) } }
            Toast.makeText(this, "Копия сохранена", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Dialogs.info(this, "Не удалось сохранить", e.message ?: "") }
    }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { readImport(it) } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun doExport(uri: Uri) {
        try {
            val json = DataSafety.exportJson(this, store)
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val d = Backup.snapshot(store)
            Dialogs.info(this, "Экспорт готов", "Сохранено: записей дневника ${d.diary?.size ?: 0}, своих блюд и продуктов ${d.customFoods?.size ?: 0}, событий ${d.events?.size ?: 0}, записей веса ${d.weights?.size ?: 0}.\n\nХраните файл в надёжном месте (облако, компьютер) — по нему можно восстановить данные на этом или новом телефоне.")
        } catch (e: Exception) { Dialogs.info(this, "Не удалось сохранить", e.message ?: e.toString()) }
    }

    private fun readImport(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (bytes.size > 50 * 1024 * 1024) throw Backup.BadFile("Файл слишком большой (${bytes.size / 1024 / 1024} МБ).")
            pending = Backup.parse(String(bytes, Charsets.UTF_8)); pendingName = uri.lastPathSegment?.substringAfterLast('/') ?: "файл"
            render()
        } catch (e: Backup.BadFile) { Dialogs.info(this, "Файл не подходит", e.message ?: "") }
        catch (e: Exception) { Dialogs.info(this, "Не удалось прочитать файл", e.message ?: e.toString()) }
    }

    private fun loadInternal(f: File) {
        try { pending = Backup.parse(f.readText()); pendingName = f.name; render() }
        catch (e: Exception) { Dialogs.info(this, "Не удалось прочитать копию", e.message ?: e.toString()) }
    }

    /** После импорта: синхронизация приёмов пищи, обновление базы своих продуктов, перепланирование будильников. */
    private fun afterImport() {
        Defaults.syncMeals(store)
        App.foods(this)
        AlarmScheduler.scheduleAll(this)
    }

    private fun doMerge(p: Backup.Parsed) {
        val st = Backup.merge(store, p.data)
        afterImport(); pending = null; render()
        Dialogs.info(this, "Данные добавлены",
            "Дневник: добавлено ${st.diaryAdded}, пропущено (уже есть) ${st.diarySkipped}\n" +
            "Мои блюда и продукты: добавлено ${st.foodsAdded}, пропущено ${st.foodsSkipped}\n" +
            "События: добавлено ${st.eventsAdded}, пропущено ${st.eventsSkipped}\n" +
            "Вес: добавлено ${st.weightsAdded}, пропущено ${st.weightsSkipped}\n" +
            (if (st.profileTaken) "Профиль взят из файла (текущий не был заполнен).\n" else "Профиль оставлен текущий.\n") +
            "Будильники перепланированы.")
    }

    private fun doReplace(p: Backup.Parsed) {
        val safety = try { DataSafety.save(this, store, "before-import") } catch (e: Exception) {
            Dialogs.info(this, "Импорт отменён", "Не удалось сохранить страховочную копию текущих данных: ${e.message}. Ничего не изменено."); return
        }
        Backup.replace(store, p.data)
        afterImport(); pending = null; render()
        Dialogs.info(this, "Данные заменены",
            "Перед заменой текущие данные сохранены в страховочную копию:\n${safety.name}\n\nЕё можно восстановить или сохранить в файл внизу этого экрана («Страховочные копии»). Будильники перепланированы.")
    }

    private fun render(): Unit = screen {
        h1("Данные")
        muted("Все данные хранятся только на этом телефоне. Обновление приложения их не удаляет. Для переноса и страховки сохраняйте копию в файл.", 14f)

        card {
            text("💾 Экспорт данных", 18f, true)
            muted("Один JSON-файл: профиль и настройки, дневник питания, «Мои блюда», рецепты и свои продукты (в том числе распознанные по этикетке), события и напоминания, вес, вода, отметки «сделано».", 14f)
            button("Экспорт данных") { createDoc.launch(Backup.fileName(LocalDateTime.now())) }
        }

        card {
            text("📂 Импорт данных", 18f, true)
            muted("Выберите файл healthplan-backup-….json. Перед загрузкой покажем, что в нём есть.", 14f)
            button("Импорт данных", outlined = true) { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
        }

        pending?.let { p ->
            card(stroke = Ui.YELLOW) {
                text("Содержимое файла", 18f, true)
                muted(pendingName, 13f)
                val m = p.meta
                muted("Создан: ${m.exportedAt?.take(16)?.replace('T', ' ') ?: "—"} · версия приложения ${m.appVersion ?: "—"} · формат ${m.formatVersion}", 13f)
                val lines = Backup.summary(p.data)
                if (lines.isEmpty()) text("В файле нет данных.", 16f)
                lines.forEach { text("• $it", 16f, topDp = 2) }
                p.warnings.forEach { muted("⚠ $it", 14f) }
                if (lines.isNotEmpty()) {
                    button("Добавить к текущим") { doMerge(p) }
                    muted("Текущие данные не меняются; из файла добавляется только то, чего ещё нет (дубликаты по id пропускаются).", 13f)
                    button("⚠ Заменить всё", outlined = true) {
                        Dialogs.confirm(this@DataActivity, "Заменить все данные?",
                            "Текущие данные будут заменены данными из файла (разделы, которых в файле нет, останутся как есть). Перед заменой приложение автоматически сохранит страховочную копию текущих данных.",
                            yes = "Заменить") { doReplace(p) }
                    }.setTextColor(Ui.RED)
                }
                button("Отмена", outlined = true) { pending = null; render() }
            }
        }

        val files = DataSafety.list(this@DataActivity)
        card {
            text("🛟 Страховочные копии", 18f, true)
            muted("Создаются автоматически перед обновлением приложения и перед «Заменить всё». Хранятся внутри приложения (последние 10 каждого вида).", 14f)
            if (files.isEmpty()) muted("Пока нет.", 14f)
            val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            files.take(20).forEach { f ->
                val kind = when { f.name.startsWith("before-import") -> "перед импортом"; f.name.startsWith("auto-before-update") -> "перед обновлением"; else -> "копия" }
                val at = java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                text("$at — $kind (${(f.length() + 1023) / 1024} КБ)", 15f, topDp = 10)
                row {
                    button("Восстановить…", outlined = true, weight = 1f, topDp = 2) { loadInternal(f) }
                    button("В файл", outlined = true, weight = 1f, topDp = 2) { copyFrom = f; copyDoc.launch(f.name) }
                }
            }
        }

        card {
            text("☁ Резервное копирование Android", 18f, true)
            muted("Данные приложения (без модели распознавания) включены в резервную копию Android и перенос на новый телефон. В облако копия уходит, только если на телефоне включено шифрование резервных копий (блокировка экрана).", 14f)
        }
    }
}
