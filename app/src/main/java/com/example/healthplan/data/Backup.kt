package com.example.healthplan.data

import com.example.healthplan.core.Profile
import com.example.healthplan.food.Food
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия всех пользовательских данных в одном JSON-файле.
 *
 * Формат (formatVersion = 1):
 * ```
 * { "format": "healthplan-backup", "formatVersion": 1, "app": "com.example.healthplan",
 *   "appVersion": "1.0.1", "appVersionCode": 2, "schema": 1, "exportedAt": "2026-09-28T12:00:00+10:00",
 *   "counts": {...},                        // справочно
 *   "data": { "profile": {...}, "mealOverrides": {"0": 480}, "menuSeed": 1, "events": [...], "nextEventId": 111,
 *             "weights": [{"d","kg"}], "diary": [...], "customFoods": [...], "favourites": [...], "recents": [...],
 *             "done": {"2026-09-28": ["101:0"]}, "water": {"2026-09-28": 750}, "permShown": true, "menuVariants": {"m0": 2} } }
 * ```
 * Объекты внутри data сериализуются теми же функциями [StoreJson], что и хранилище, поэтому поля совпадают.
 * Совместимость: отсутствующие разделы/поля → значения по умолчанию (раздел не трогается); неизвестные поля игнорируются;
 * файл более новой версии формата читается «как есть» с предупреждением. Битые элементы пропускаются и считаются.
 */
object Backup {
    const val FORMAT = "healthplan-backup"
    const val FORMAT_VERSION = 1

    /** null в поле = раздела нет в файле (при импорте такой раздел не изменяется). */
    data class Data(
        val profile: Profile? = null,
        val mealOverrides: Map<Int, Int>? = null,
        val menuSeed: Int? = null,
        val events: List<EventItem>? = null,
        val nextEventId: Int? = null,
        val weights: List<WeightEntry>? = null,
        val diary: List<DiaryEntry>? = null,
        val customFoods: List<Food>? = null,
        val favourites: Set<String>? = null,
        val recents: List<String>? = null,
        val done: Map<String, Set<String>>? = null,
        val water: Map<String, Int>? = null,
        val permShown: Boolean? = null,
        val menuVariants: Map<String, Int>? = null,
    )

    data class Meta(val formatVersion: Int, val appVersion: String?, val appVersionCode: Int?, val exportedAt: String?, val schema: Int?)
    data class Parsed(val meta: Meta, val data: Data, val skipped: Int, val warnings: List<String>)
    data class MergeStats(val diaryAdded: Int, val diarySkipped: Int, val foodsAdded: Int, val foodsSkipped: Int,
                          val eventsAdded: Int, val eventsSkipped: Int, val weightsAdded: Int, val weightsSkipped: Int, val profileTaken: Boolean)

    class BadFile(msg: String) : Exception(msg)

    // ---------------- Снимок / запись в хранилище ----------------

    fun snapshot(s: Store): Data = Data(
        profile = s.profile, mealOverrides = s.mealTimeOverrides, menuSeed = s.menuSeed, events = s.events, nextEventId = s.nextEventIdRaw,
        weights = s.weights, diary = s.diary, customFoods = s.customFoods, favourites = s.favourites, recents = s.recents,
        done = s.allDone, water = s.allWater, permShown = s.permissionsShown, menuVariants = s.menuVariants,
    )

    /** Записывает в хранилище только присутствующие (не null) разделы. */
    fun write(s: Store, d: Data) {
        d.profile?.let { s.profile = it }
        d.mealOverrides?.let { s.mealTimeOverrides = it }
        d.menuSeed?.let { s.menuSeed = it }
        d.events?.let { s.events = it }
        d.nextEventId?.let { s.nextEventIdRaw = it }
        d.weights?.let { s.weights = it }
        d.diary?.let { s.diary = it }
        d.customFoods?.let { s.customFoods = it }
        d.favourites?.let { s.favourites = it }
        d.recents?.let { s.setRecents(it) }
        d.done?.let { s.allDone = it }
        d.water?.let { s.allWater = it }
        d.permShown?.let { s.permissionsShown = it }
        d.menuVariants?.let { s.menuVariants = it }
        s.ensureEventIdCounter()
    }

    /** «Заменить всё»: разделы из файла заменяют текущие; разделы, которых в файле нет, остаются как были. */
    fun replace(s: Store, incoming: Data) = write(s, incoming)

    /** «Добавить к текущим»: текущие данные не изменяются, из файла добавляется только то, чего нет (по id / дате). */
    fun merge(s: Store, incoming: Data): MergeStats {
        val (merged, stats) = merge(snapshot(s), incoming)
        write(s, merged)
        return stats
    }

    fun merge(cur: Data, inc: Data): Pair<Data, MergeStats> {
        val curDiary = cur.diary.orEmpty(); val incDiary = inc.diary.orEmpty()
        val diaryIds = curDiary.map { it.id }.toHashSet()
        val newDiary = incDiary.filter { diaryIds.add(it.id) }

        val curFoods = cur.customFoods.orEmpty(); val foodIds = curFoods.map { it.id }.toHashSet()
        val newFoods = inc.customFoods.orEmpty().filter { foodIds.add(it.id) }

        val curEvents = cur.events.orEmpty(); val evIds = curEvents.map { it.id }.toHashSet()
        val newEvents = inc.events.orEmpty().filter { evIds.add(it.id) }

        val curW = cur.weights.orEmpty(); val wDates = curW.map { it.date }.toHashSet()
        val newW = inc.weights.orEmpty().filter { wDates.add(it.date) }

        val takeProfile = cur.profile?.filled != true && inc.profile?.filled == true
        val done = (cur.done.orEmpty().keys + inc.done.orEmpty().keys).associateWith { d -> cur.done.orEmpty()[d].orEmpty() + inc.done.orEmpty()[d].orEmpty() }
        val nextId = listOfNotNull(cur.nextEventId, inc.nextEventId, (curEvents + newEvents).maxOfOrNull { it.id + 1 }).maxOrNull()

        val merged = Data(
            profile = if (takeProfile) inc.profile else cur.profile,
            mealOverrides = if (takeProfile) inc.mealOverrides ?: cur.mealOverrides else inc.mealOverrides.orEmpty() + cur.mealOverrides.orEmpty(),
            menuSeed = cur.menuSeed ?: inc.menuSeed,
            events = curEvents + newEvents,
            nextEventId = nextId,
            weights = curW + newW,
            diary = curDiary + newDiary,
            customFoods = curFoods + newFoods,
            favourites = cur.favourites.orEmpty() + inc.favourites.orEmpty(),
            recents = (cur.recents.orEmpty() + inc.recents.orEmpty()).distinct().take(30),
            done = done,
            water = inc.water.orEmpty() + cur.water.orEmpty(),   // при совпадении даты остаётся текущее значение
            permShown = cur.permShown ?: inc.permShown,
            menuVariants = inc.menuVariants.orEmpty() + cur.menuVariants.orEmpty(),
        )
        return merged to MergeStats(newDiary.size, incDiary.size - newDiary.size, newFoods.size, inc.customFoods.orEmpty().size - newFoods.size,
            newEvents.size, inc.events.orEmpty().size - newEvents.size, newW.size, inc.weights.orEmpty().size - newW.size, takeProfile)
    }

    // ---------------- JSON ----------------

    fun toJson(d: Data, appVersion: String, appVersionCode: Int, exportedAt: String, schema: Int = Store.SCHEMA): JSONObject {
        val data = JSONObject()
        d.profile?.let { data.put("profile", StoreJson.profileToJson(it)) }
        d.mealOverrides?.let { m -> data.put("mealOverrides", JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }) }
        d.menuSeed?.let { data.put("menuSeed", it) }
        d.events?.let { l -> data.put("events", JSONArray().apply { l.forEach { put(StoreJson.eventToJson(it)) } }) }
        d.nextEventId?.let { data.put("nextEventId", it) }
        d.weights?.let { l -> data.put("weights", JSONArray().apply { l.forEach { put(JSONObject().put("d", it.date).put("kg", it.kg)) } }) }
        d.diary?.let { l -> data.put("diary", JSONArray().apply { l.forEach { put(StoreJson.diaryToJson(it)) } }) }
        d.customFoods?.let { l -> data.put("customFoods", JSONArray().apply { l.forEach { put(StoreJson.foodToJson(it)) } }) }
        d.favourites?.let { data.put("favourites", JSONArray(it.sorted())) }
        d.recents?.let { data.put("recents", JSONArray(it)) }
        d.done?.let { m -> data.put("done", JSONObject().apply { m.toSortedMap().forEach { (k, v) -> put(k, JSONArray(v.sorted())) } }) }
        d.water?.let { m -> data.put("water", JSONObject().apply { m.toSortedMap().forEach { (k, v) -> put(k, v) } }) }
        d.permShown?.let { data.put("permShown", it) }
        d.menuVariants?.let { m -> data.put("menuVariants", JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }) }
        return JSONObject().apply {
            put("format", FORMAT); put("formatVersion", FORMAT_VERSION); put("app", "com.example.healthplan")
            put("appVersion", appVersion); put("appVersionCode", appVersionCode); put("schema", schema); put("exportedAt", exportedAt)
            put("counts", JSONObject().put("diary", d.diary?.size ?: 0).put("customFoods", d.customFoods?.size ?: 0)
                .put("events", d.events?.size ?: 0).put("weights", d.weights?.size ?: 0))
            put("data", data)
        }
    }

    /** Разбор и проверка файла. Бросает [BadFile] с понятным текстом, если это не резервная копия приложения. */
    fun parse(text: String): Parsed {
        val root = try { JSONObject(text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')) } catch (e: Exception) { throw BadFile("Файл не является JSON: ${e.message?.take(80)}") }
        val fmt = root.optString("format", "")
        if (fmt != FORMAT && !(fmt.isEmpty() && root.has("data"))) throw BadFile("Это не резервная копия «Здоровье и режим» (format = «$fmt»).")
        val data = root.optJSONObject("data") ?: throw BadFile("В файле нет раздела data.")
        val warnings = mutableListOf<String>()
        val fv = root.optInt("formatVersion", 1)
        if (fv > FORMAT_VERSION) warnings += "Файл создан более новой версией приложения (формат $fv). Известные разделы будут загружены, неизвестные — пропущены."
        var skipped = 0
        fun <T> arr(key: String, f: (JSONObject) -> T): List<T>? {
            val a = data.optJSONArray(key) ?: return null
            val out = ArrayList<T>(a.length())
            for (i in 0 until a.length()) { val o = a.optJSONObject(i); val v = o?.let { runCatching { f(it) }.getOrNull() }; if (v != null) out += v else skipped++ }
            return out
        }
        fun strings(key: String): List<String>? = data.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } }
        fun intMap(key: String): Map<String, Int>? = data.optJSONObject(key)?.let { o -> o.keys().asSequence().mapNotNull { k -> (o.opt(k) as? Number)?.let { k to it.toInt() } }.toMap() }
        fun optInt(key: String): Int? = (data.opt(key) as? Number)?.toInt()

        val profile = data.optJSONObject("profile")?.let { runCatching { StoreJson.profileFromJson(it) }.getOrElse { skipped++; null } }
        val d = Data(
            profile = profile,
            mealOverrides = intMap("mealOverrides")?.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }?.toMap(),
            menuSeed = optInt("menuSeed"),
            events = arr("events") { StoreJson.eventFromJson(it) },
            nextEventId = optInt("nextEventId"),
            weights = arr("weights") { o -> WeightEntry(o.getString("d"), o.getDouble("kg")) },
            diary = arr("diary") { StoreJson.diaryFromJson(it) },
            customFoods = arr("customFoods") { StoreJson.foodFromJson(it) },
            favourites = strings("favourites")?.toSet(),
            recents = strings("recents"),
            done = data.optJSONObject("done")?.let { o -> o.keys().asSequence().mapNotNull { k -> o.optJSONArray(k)?.let { a -> k to (0 until a.length()).mapNotNull { a.opt(it) as? String }.toSet() } }.toMap() },
            water = intMap("water"),
            permShown = data.opt("permShown") as? Boolean,
            menuVariants = intMap("menuVariants"),
        )
        if (skipped > 0) warnings += "Пропущено повреждённых записей: $skipped."
        val meta = Meta(fv, root.optString("appVersion").ifEmpty { null }, (root.opt("appVersionCode") as? Number)?.toInt(),
            root.optString("exportedAt").ifEmpty { null }, (root.opt("schema") as? Number)?.toInt())
        return Parsed(meta, d, skipped, warnings)
    }

    /** Краткое содержание для экрана подтверждения импорта. */
    fun summary(d: Data): List<String> {
        val out = mutableListOf<String>()
        d.profile?.let { out += "Профиль: ${if (it.filled) "${it.age} лет, ${fmt(it.heightCm)} см, ${fmt(it.weightKg)} кг" else "не заполнен"}" }
        d.diary?.let { l ->
            val dates = l.map { it.date }.sorted()
            out += "Дневник питания: ${l.size} записей" + if (dates.isNotEmpty()) " (${dates.first()} — ${dates.last()})" else ""
        }
        d.customFoods?.let { l -> out += "Мои блюда и продукты: ${l.size} (из них рецептов: ${l.count { it.recipe.isNotEmpty() }})" }
        d.events?.let { l -> out += "События и напоминания: ${l.size} (включено: ${l.count { it.enabled }})" }
        d.weights?.let { out += "Записи веса: ${it.size}" }
        d.water?.let { out += "Дни с учётом воды: ${it.size}" }
        d.done?.let { out += "Дни с отметками «сделано»: ${it.size}" }
        d.favourites?.let { out += "Избранное: ${it.size}" }
        return out
    }

    private fun fmt(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", x).replace('.', ',')

    fun fileName(now: java.time.LocalDateTime): String =
        "healthplan-backup-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".json"
}
