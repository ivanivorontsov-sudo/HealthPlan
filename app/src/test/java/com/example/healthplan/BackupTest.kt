package com.example.healthplan

import com.example.healthplan.core.*
import com.example.healthplan.data.*
import com.example.healthplan.food.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private fun store() = Store(FakePrefs(), FakePrefs())

    private fun filled(): Store = store().apply {
        profile = Profile(sex = Sex.FEMALE, age = 41, heightCm = 165.0, weightKg = 72.5, goal = Goal.LOSE, waistCm = 84.0, filled = true)
        mealTimeOverrides = mapOf(0 to 450, 2 to 800)
        menuSeed = 3
        events = listOf(
            EventItem(nextEventId(), EventType.MEAL, "Завтрак", emptyList(), mealIndex = 0),
            EventItem(nextEventId(), EventType.TEETH, "Чистка зубов", listOf(480, 1350), note = "2 минуты"),
            EventItem(nextEventId(), EventType.CUSTOM, "Таблетка", listOf(600), days = 0b0011111, ring = false, enabled = false))
        logWeight("2026-09-20", 73.4); logWeight("2026-09-27", 72.5)
        addDiary(DiaryEntry(1001L, "2026-09-27", 0, "u:171077", "Куриная грудка", 150.0, 120.0, 22.5, 2.62, 0.0, 0.0))
        addDiary(DiaryEntry(1002L, "2026-09-28", 1, "c:ocr-1", "Сливки 10%", 20.0, 118.0, 2.6, 10.0, 4.1, 0.0))
        saveCustomFood(Food("c:ocr-1", "Сливки 10%", "Мои блюда", N(118.0, 2.6, 10.0, 4.1, 0.0), "", "Этикетка (распознано с фото, проверено пользователем)"))
        saveCustomFood(Food("c:plov", "Плов домашний", "Мои блюда", N(160.0, 6.0, 5.5, 21.0, 1.1), "", "Рецепт пользователя", 300.0,
            listOf(Ingredient("u:1", "Рис", 200.0, N(365.0, 7.1, 0.7, 78.7, 1.3))), 650.0))
        favourites = setOf("u:171077", "c:plov")
        setDone("2026-09-27", "101:0", true); setDone("2026-09-27", "102:1", true)
        addWater("2026-09-27", 750)
        permissionsShown = true
        menuVariants = mapOf("m0" to 2, "m3" to 1)
    }

    private fun roundTrip(s: Store): Backup.Parsed =
        Backup.parse(Backup.toJson(Backup.snapshot(s), "1.0.1", 2, "2026-09-28T12:00+10:00").toString(1))

    @Test fun exportImportRoundTripIsEqual() {
        val src = filled()
        val snap = Backup.snapshot(src)
        val parsed = roundTrip(src)
        assertEquals(0, parsed.skipped)
        assertEquals(snap, parsed.data)

        val dst = store()
        Backup.replace(dst, parsed.data)
        assertEquals(snap, Backup.snapshot(dst))
        assertEquals(src.profile, dst.profile); assertEquals(src.diary, dst.diary); assertEquals(src.customFoods, dst.customFoods)
        assertEquals(src.events, dst.events); assertEquals(src.weights, dst.weights); assertEquals(750, dst.waterMl("2026-09-27"))
        assertEquals(setOf("101:0", "102:1"), dst.doneSet("2026-09-27")); assertEquals(src.menuVariants, dst.menuVariants)
        // новые события после импорта не конфликтуют по id
        assertTrue(dst.nextEventId() > dst.events.maxOf { it.id })
    }

    @Test fun fileHasFormatVersionAndName() {
        val o = Backup.toJson(Backup.snapshot(filled()), "1.0.1", 2, "2026-09-28T12:00+10:00")
        assertEquals("healthplan-backup", o.getString("format")); assertEquals(1, o.getInt("formatVersion"))
        assertEquals(2, o.getJSONObject("counts").getInt("customFoods"))
        assertEquals("healthplan-backup-2026-09-28-0905.json", Backup.fileName(java.time.LocalDateTime.of(2026, 9, 28, 9, 5)))
    }

    @Test fun mergeAddsOnlyMissingAndKeepsCurrent() {
        val cur = filled()
        val other = store().apply {
            profile = Profile(age = 60, filled = true)
            events = listOf(EventItem(100, EventType.MEAL, "Чужой завтрак", emptyList(), mealIndex = 0), EventItem(250, EventType.CUSTOM, "Витамины", listOf(540)))
            addDiary(DiaryEntry(1001L, "2026-09-27", 0, "x", "ДУБЛИКАТ", 1.0, 1.0, 1.0, 1.0, 1.0, 0.0))
            addDiary(DiaryEntry(2001L, "2026-09-26", 2, "u:1", "Гречка", 200.0, 110.0, 4.0, 1.0, 21.0, 2.0))
            saveCustomFood(Food("c:plov", "Плов ЧУЖОЙ", "Мои блюда", N(1.0)))
            saveCustomFood(Food("c:new", "Суп", "Мои блюда", N(40.0, 2.0, 1.0, 5.0, 1.0)))
            logWeight("2026-09-27", 99.0); logWeight("2026-09-10", 74.0)
            addWater("2026-09-27", 2000); addWater("2026-09-26", 500)
            setDone("2026-09-27", "250:0", true)
        }
        val before = Backup.snapshot(cur)
        val stats = Backup.merge(cur, roundTrip(other).data)
        assertEquals(1, stats.diaryAdded); assertEquals(1, stats.diarySkipped)
        assertEquals(1, stats.foodsAdded); assertEquals(1, stats.foodsSkipped)
        assertEquals(1, stats.eventsAdded); assertEquals(1, stats.eventsSkipped)
        assertEquals(1, stats.weightsAdded); assertEquals(1, stats.weightsSkipped)
        assertFalse(stats.profileTaken)
        // всё текущее на месте и не изменено
        assertEquals(before.profile, cur.profile)
        assertTrue(cur.diary.containsAll(before.diary!!)); assertEquals("Куриная грудка", cur.diary.first { it.id == 1001L }.name)
        assertEquals("Плов домашний", cur.customFoods.first { it.id == "c:plov" }.name)
        assertEquals("Завтрак", cur.events.first { it.id == 100 }.title)
        assertEquals(72.5, cur.weights.first { it.date == "2026-09-27" }.kg, 0.0)
        assertEquals(750, cur.waterMl("2026-09-27")); assertEquals(500, cur.waterMl("2026-09-26"))
        assertEquals(setOf("101:0", "102:1", "250:0"), cur.doneSet("2026-09-27"))
        // добавленное
        assertEquals(3, cur.diary.size); assertEquals(3, cur.customFoods.size); assertEquals(4, cur.events.size); assertEquals(3, cur.weights.size)
        assertTrue(cur.nextEventId() > 250)
        // повторный импорт того же файла ничего не добавляет (идемпотентность)
        val again = Backup.merge(cur, roundTrip(other).data)
        assertEquals(0, again.diaryAdded + again.foodsAdded + again.eventsAdded + again.weightsAdded)
    }

    @Test fun mergeIntoEmptyTakesProfile() {
        val cur = store()
        val st = Backup.merge(cur, roundTrip(filled()).data)
        assertTrue(st.profileTaken); assertEquals(41, cur.profile.age); assertEquals(mapOf(0 to 450, 2 to 800), cur.mealTimeOverrides)
    }

    @Test fun replaceKeepsSectionsMissingInFile() {
        val cur = filled()
        val file = """{"format":"healthplan-backup","formatVersion":1,"data":{"diary":[{"id":5,"d":"2026-01-01","m":0,"fid":"u:1","n":"Яблоко","g":150,"k":52,"p":0.3,"f":0.2,"c":12,"fb":2.4}]}}"""
        Backup.replace(cur, Backup.parse(file).data)
        assertEquals(1, cur.diary.size); assertEquals("Яблоко", cur.diary[0].name)
        assertEquals(2, cur.customFoods.size); assertEquals(41, cur.profile.age); assertEquals(3, cur.events.size)
    }

    @Test fun toleratesUnknownAndMissingFieldsAndNewerFormat() {
        val file = """{"format":"healthplan-backup","formatVersion":7,"futureTopLevel":{"x":1},
            "data":{"profile":{"age":33,"sex":"FEMALE","newField":"?"},"customFoods":[{"id":"c:1","name":"Сыр","k":350,"extra":[1,2]},{"broken":true}],
            "events":[{"id":7,"type":"FUTURE_TYPE","title":"Что-то","times":[600]}],"unknownSection":[1,2,3]}}"""
        val p = Backup.parse(file)
        assertEquals(1, p.skipped); assertTrue(p.warnings.any { "новее" in it || "более новой" in it })
        assertEquals(33, p.data.profile!!.age); assertEquals(Profile().heightCm, p.data.profile!!.heightCm, 0.0)
        assertEquals(350.0, p.data.customFoods!!.single().per100.kcal, 0.0)
        assertEquals(EventType.CUSTOM, p.data.events!!.single().type)
        assertNull(p.data.diary); assertNull(p.data.weights)
    }

    @Test fun rejectsForeignFiles() {
        assertThrows(Backup.BadFile::class.java) { Backup.parse("not json") }
        assertThrows(Backup.BadFile::class.java) { Backup.parse("""{"format":"other-app","data":{}}""") }
        assertThrows(Backup.BadFile::class.java) { Backup.parse("""{"format":"healthplan-backup"}""") }
    }

    @Test fun summaryListsSections() {
        val lines = Backup.summary(roundTrip(filled()).data)
        assertTrue(lines.any { it.startsWith("Дневник питания: 2 записей (2026-09-27 — 2026-09-28)") })
        assertTrue(lines.any { it.startsWith("Мои блюда и продукты: 2 (из них рецептов: 1)") })
    }
}

/** Данные в формате 1.0.0 читаются новой версией без потерь; миграция ничего не удаляет и идемпотентна. */
class UpgradeCompatTest {
    @Test fun storeFrom100IsReadIntactAndMigrationIsAddOnly() {
        val sp = FakePrefs()
        // ровно то, что записывает 1.0.0 (ключи и поля не менялись)
        sp.map["profile"] = """{"sex":"MALE","age":45,"h":180,"w":92.5,"act":"G2","goal":"LOSE","tw":85,"rate":"NORMAL","wake":420,"sleep":1380,"meals":4,"veg":false,"vegan":false,"lf":false,"gf":false,"nofish":false,"nonuts":false,"noeggs":false,"filled":true}"""
        sp.map["events"] = """[{"id":100,"type":"MEAL","title":"Завтрак","times":[],"days":127,"en":true,"ring":true,"note":"","meal":0},{"id":105,"type":"CUSTOM","title":"Лекарство","times":[540],"days":127,"en":true,"ring":true,"note":"","meal":-1}]"""
        sp.map["nextEventId"] = 106
        sp.map["diary"] = """[{"id":123456789,"d":"2026-09-27","m":1,"fid":"c:abc","n":"Мой суп","g":300,"k":45,"p":2,"f":1.5,"c":6,"fb":1}]"""
        sp.map["customFoods"] = """[{"id":"c:abc","name":"Мой суп","cat":"Мои блюда","k":45,"p":2,"f":1.5,"c":6,"fb":1,"tags":"","src":"Свой продукт","recipe":[]}]"""
        sp.map["weights"] = """[{"d":"2026-09-27","kg":92.5}]"""
        sp.map["schema"] = 1; sp.map["done_2026-09-27"] = setOf("100:0"); sp.map["water_2026-09-27"] = 500
        val keysBefore = sp.map.keys.toSet(); val valuesBefore = HashMap(sp.map)
        val s = Store(sp, FakePrefs())
        s.migrate(); s.migrate()
        assertTrue(sp.map.keys.containsAll(keysBefore))
        keysBefore.forEach { assertEquals(it, valuesBefore[it], sp.map[it]) }
        assertEquals(45, s.profile.age); assertEquals(Activity.G2, s.profile.activity)
        assertEquals("Лекарство", s.events[1].title); assertEquals("Мой суп", s.diary.single().name); assertEquals(300.0, s.diary.single().grams, 0.0)
        assertEquals(1, s.customFoods.size); assertEquals(92.5, s.weights.single().kg, 0.0); assertEquals(500, s.waterMl("2026-09-27"))
        assertTrue(s.eventsInitialized)   // значит, набор событий по умолчанию повторно создан не будет
        assertTrue(s.hasUserData)
    }
}
