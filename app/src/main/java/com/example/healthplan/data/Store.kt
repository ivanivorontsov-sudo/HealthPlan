package com.example.healthplan.data

import android.content.Context
import android.content.SharedPreferences
import com.example.healthplan.core.*
import com.example.healthplan.food.Food
import com.example.healthplan.food.Ingredient
import com.example.healthplan.food.N
import com.example.healthplan.food.Restrictions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальное хранилище (SharedPreferences + JSON). Данные никогда не удаляются при обновлении:
 * добавление полей выполняется через значения по умолчанию, версия схемы — [SCHEMA].
 */
class Store(ctx: Context) {
    private val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("healthplan", Context.MODE_PRIVATE)

    companion object {
        const val SCHEMA = 1
        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store = inst ?: synchronized(this) { inst ?: Store(ctx).also { inst = it; it.migrate() } }
    }

    private fun migrate() {
        val v = sp.getInt("schema", 0)
        if (v < SCHEMA) sp.edit().putInt("schema", SCHEMA).apply()
    }

    // ---------- Профиль ----------
    var profile: Profile
        get() = sp.getString("profile", null)?.let { StoreJson.profileFromJson(JSONObject(it)) } ?: Profile()
        set(p) { sp.edit().putString("profile", StoreJson.profileToJson(p).toString()).apply() }

    fun restrictions(p: Profile = profile) = Restrictions(p.vegetarian, p.vegan, p.lactoseFree, p.glutenFree, p.noFish, p.noNuts, p.noEggs)

    // ---------- Режим питания (переопределения времени) ----------
    /** Пользовательские времена приёмов пищи (если редактировались); иначе считаются из подъёма/отбоя. */
    var mealTimeOverrides: Map<Int, Int>
        get() = sp.getString("mealOverrides", null)?.let { s -> val o = JSONObject(s); o.keys().asSequence().associate { it.toInt() to o.getInt(it) } } ?: emptyMap()
        set(m) { sp.edit().putString("mealOverrides", JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }.toString()).apply() }

    fun mealSlots(p: Profile = profile): List<MealSlot> {
        val base = MealSchedule.build(p.wakeMin, p.sleepMin, p.mealsCount)
        val ov = mealTimeOverrides
        return base.map { s -> ov[s.index]?.let { s.copy(timeMin = it) } ?: s }
    }

    var menuSeed: Int
        get() = sp.getInt("menuSeed", 1)
        set(v) { sp.edit().putInt("menuSeed", v).apply() }

    // ---------- События ----------
    var events: List<EventItem>
        get() = sp.getString("events", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { StoreJson.eventFromJson(a.getJSONObject(it)) } } } ?: emptyList()
        set(list) { sp.edit().putString("events", JSONArray().apply { list.forEach { put(StoreJson.eventToJson(it)) } }.toString()).apply() }

    val eventsInitialized get() = sp.contains("events")

    fun nextEventId(): Int { val n = sp.getInt("nextEventId", 100); sp.edit().putInt("nextEventId", n + 1).apply(); return n }

    /** Время события с учётом режима питания. */
    fun resolvedTimes(e: EventItem, slots: List<MealSlot> = mealSlots()): List<Int> =
        if (e.type == EventType.MEAL && e.mealIndex >= 0) slots.getOrNull(e.mealIndex)?.let { listOf(it.timeMin) } ?: emptyList() else e.times

    // ---------- Отметки «сделано» ----------
    fun doneSet(date: String): Set<String> = sp.getStringSet("done_$date", emptySet()) ?: emptySet()
    fun setDone(date: String, key: String, done: Boolean) {
        val s = doneSet(date).toMutableSet(); if (done) s += key else s -= key
        sp.edit().putStringSet("done_$date", s).apply()
    }

    // ---------- Вес ----------
    var weights: List<WeightEntry>
        get() = sp.getString("weights", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { val o = a.getJSONObject(it); WeightEntry(o.getString("d"), o.getDouble("kg")) } } } ?: emptyList()
        set(l) { sp.edit().putString("weights", JSONArray().apply { l.sortedBy { it.date }.forEach { put(JSONObject().put("d", it.date).put("kg", it.kg)) } }.toString()).apply() }

    fun logWeight(date: String, kg: Double) {
        weights = weights.filter { it.date != date } + WeightEntry(date, kg)
        val latest = weights.maxByOrNull { it.date }
        if (latest != null && latest.date == date) profile = profile.copy(weightKg = kg)
    }

    // ---------- Вода ----------
    fun waterMl(date: String) = sp.getInt("water_$date", 0)
    fun addWater(date: String, ml: Int) { sp.edit().putInt("water_$date", maxOf(0, waterMl(date) + ml)).apply() }

    // ---------- Дневник питания ----------
    var diary: List<DiaryEntry>
        get() = sp.getString("diary", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { StoreJson.diaryFromJson(a.getJSONObject(it)) } } } ?: emptyList()
        set(l) { sp.edit().putString("diary", JSONArray().apply { l.forEach { put(StoreJson.diaryToJson(it)) } }.toString()).apply() }

    fun diaryFor(date: String) = diary.filter { it.date == date }
    fun addDiary(e: DiaryEntry) { diary = diary + e; touchRecent(e.foodId) }
    fun updateDiary(e: DiaryEntry) { diary = diary.map { if (it.id == e.id) e else it } }
    fun deleteDiary(id: Long) { diary = diary.filter { it.id != id } }

    // ---------- Избранное / недавние ----------
    var favourites: Set<String>
        get() = sp.getStringSet("fav", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("fav", v).apply() }
    val recents: List<String> get() = sp.getString("recent", "")!!.split('|').filter { it.isNotBlank() }
    private fun touchRecent(id: String) { sp.edit().putString("recent", (listOf(id) + recents.filter { it != id }).take(30).joinToString("|")).apply() }

    // ---------- Свои продукты и блюда ----------
    var customFoods: List<Food>
        get() = sp.getString("customFoods", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { StoreJson.foodFromJson(a.getJSONObject(it)) } } } ?: emptyList()
        set(l) { sp.edit().putString("customFoods", JSONArray().apply { l.forEach { put(StoreJson.foodToJson(it)) } }.toString()).apply() }

    fun saveCustomFood(f: Food) { customFoods = customFoods.filter { it.id != f.id } + f }
    fun deleteCustomFood(id: String) { customFoods = customFoods.filter { it.id != id } }

    // ---------- Первый запуск / разрешения ----------
    var permissionsShown: Boolean
        get() = sp.getBoolean("permShown", false)
        set(v) { sp.edit().putBoolean("permShown", v).apply() }
}

/** Сериализация в JSON (отдельно — чтобы покрыть unit-тестами совместимость формата). */
object StoreJson {
    // ================= JSON =================
    private fun Double?.orNull(o: JSONObject, k: String) { if (this != null) o.put(k, this) }
    private fun JSONObject.optD(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

    fun profileToJson(p: Profile) = JSONObject().apply {
        put("sex", p.sex.name); put("age", p.age); put("h", p.heightCm); put("w", p.weightKg); put("act", p.activity.name)
        put("goal", p.goal.name); put("tw", p.targetWeightKg); put("rate", p.lossRate.name)
        p.waistCm.orNull(this, "waist"); p.hipCm.orNull(this, "hip"); p.neckCm.orNull(this, "neck"); p.bodyFatPct.orNull(this, "bf")
        put("wake", p.wakeMin); put("sleep", p.sleepMin); put("meals", p.mealsCount)
        put("veg", p.vegetarian); put("vegan", p.vegan); put("lf", p.lactoseFree); put("gf", p.glutenFree)
        put("nofish", p.noFish); put("nonuts", p.noNuts); put("noeggs", p.noEggs); put("filled", p.filled)
    }

    fun profileFromJson(o: JSONObject): Profile {
        val d = Profile()
        return Profile(
            runCatching { Sex.valueOf(o.getString("sex")) }.getOrDefault(d.sex), o.optInt("age", d.age), o.optDouble("h", d.heightCm),
            o.optDouble("w", d.weightKg), runCatching { Activity.valueOf(o.getString("act")) }.getOrDefault(d.activity),
            runCatching { Goal.valueOf(o.getString("goal")) }.getOrDefault(d.goal), o.optDouble("tw", d.targetWeightKg),
            runCatching { LossRate.valueOf(o.getString("rate")) }.getOrDefault(d.lossRate),
            o.optD("waist"), o.optD("hip"), o.optD("neck"), o.optD("bf"),
            o.optInt("wake", d.wakeMin), o.optInt("sleep", d.sleepMin), o.optInt("meals", d.mealsCount),
            o.optBoolean("veg"), o.optBoolean("vegan"), o.optBoolean("lf"), o.optBoolean("gf"),
            o.optBoolean("nofish"), o.optBoolean("nonuts"), o.optBoolean("noeggs"), o.optBoolean("filled")
        )
    }

    fun eventToJson(e: EventItem) = JSONObject().apply {
        put("id", e.id); put("type", e.type.name); put("title", e.title); put("times", JSONArray(e.times)); put("days", e.days)
        put("en", e.enabled); put("ring", e.ring); put("note", e.note); put("meal", e.mealIndex)
    }

    fun eventFromJson(o: JSONObject): EventItem {
        val t = o.optJSONArray("times") ?: JSONArray()
        return EventItem(o.getInt("id"), runCatching { EventType.valueOf(o.getString("type")) }.getOrDefault(EventType.CUSTOM),
            o.optString("title"), (0 until t.length()).map { t.getInt(it) }, o.optInt("days", Days.ALL), o.optBoolean("en", true),
            o.optBoolean("ring", true), o.optString("note"), o.optInt("meal", -1))
    }

    fun diaryToJson(e: DiaryEntry) = JSONObject().apply {
        put("id", e.id); put("d", e.date); put("m", e.meal); put("fid", e.foodId); put("n", e.name); put("g", e.grams)
        put("k", e.kcal100); put("p", e.p100); put("f", e.f100); put("c", e.c100); put("fb", e.fib100)
    }

    fun diaryFromJson(o: JSONObject) = DiaryEntry(o.getLong("id"), o.getString("d"), o.optInt("m"), o.optString("fid"), o.optString("n"),
        o.optDouble("g", 0.0), o.optDouble("k", 0.0), o.optDouble("p", 0.0), o.optDouble("f", 0.0), o.optDouble("c", 0.0), o.optDouble("fb", 0.0))

    fun foodToJson(f: Food) = JSONObject().apply {
        put("id", f.id); put("name", f.name); put("cat", f.category); put("k", f.per100.kcal); put("p", f.per100.p); put("f", f.per100.f)
        put("c", f.per100.c); put("fb", f.per100.fib); put("tags", f.tags); put("src", f.source); f.portionG.orNull(this, "portion")
        f.cookedWeightG.orNull(this, "cooked")
        put("recipe", JSONArray().apply { f.recipe.forEach { i -> put(JSONObject().put("id", i.foodId).put("n", i.name).put("g", i.grams)
            .put("k", i.per100.kcal).put("p", i.per100.p).put("f", i.per100.f).put("c", i.per100.c).put("fb", i.per100.fib)) } })
    }

    fun foodFromJson(o: JSONObject): Food {
        val r = o.optJSONArray("recipe") ?: JSONArray()
        return Food(o.getString("id"), o.getString("name"), o.optString("cat", "Мои блюда"),
            N(o.optDouble("k", 0.0), o.optDouble("p", 0.0), o.optDouble("f", 0.0), o.optDouble("c", 0.0), o.optDouble("fb", 0.0)),
            o.optString("tags"), o.optString("src"), o.optD("portion"),
            (0 until r.length()).map { val x = r.getJSONObject(it); Ingredient(x.getString("id"), x.optString("n"), x.optDouble("g", 0.0),
                N(x.optDouble("k", 0.0), x.optDouble("p", 0.0), x.optDouble("f", 0.0), x.optDouble("c", 0.0), x.optDouble("fb", 0.0))) },
            o.optD("cooked"))
    }
}
