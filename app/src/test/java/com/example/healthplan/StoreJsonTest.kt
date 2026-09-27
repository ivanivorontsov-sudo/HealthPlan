package com.example.healthplan

import com.example.healthplan.core.*
import com.example.healthplan.data.*
import com.example.healthplan.food.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Формат хранения должен читаться после обновлений: круговой тест + чтение «старого» JSON без новых полей. */
class StoreJsonTest {
    @Test fun profileRoundTrip() {
        val p = Profile(sex = Sex.FEMALE, age = 41, heightCm = 165.0, weightKg = 72.5, goal = Goal.LOSE, waistCm = 84.0, filled = true, vegetarian = true)
        assertEquals(p, StoreJson.profileFromJson(JSONObject(StoreJson.profileToJson(p).toString())))
    }

    @Test fun oldProfileJsonGetsDefaults() {
        val p = StoreJson.profileFromJson(JSONObject("""{"sex":"MALE","age":50,"h":180,"w":90,"act":"UNKNOWN_FUTURE"}"""))
        assertEquals(50, p.age); assertEquals(90.0, p.weightKg, 0.0); assertEquals(Profile().activity, p.activity); assertNull(p.waistCm)
    }

    @Test fun customRecipeRoundTrip() {
        val f = Food("c:x", "Плов домашний", "Мои блюда", N(160.0, 6.0, 5.5, 21.0, 1.1), "", "Рецепт пользователя", 300.0,
            listOf(Ingredient("u:1", "Рис", 200.0, N(365.0, 7.1, 0.7, 78.7, 1.3))), 650.0)
        assertEquals(f, StoreJson.foodFromJson(JSONObject(StoreJson.foodToJson(f).toString())))
    }

    @Test fun diaryAndEventRoundTrip() {
        val d = DiaryEntry(5L, "2026-09-27", 2, "u:171077", "Куриная грудка", 150.0, 120.0, 22.5, 2.62, 0.0, 0.0)
        assertEquals(d, StoreJson.diaryFromJson(JSONObject(StoreJson.diaryToJson(d).toString())))
        val e = EventItem(3, EventType.TEETH, "Почистить зубы", listOf(480, 1350), Days.ALL, true, true, "2 минуты", -1)
        assertEquals(e, StoreJson.eventFromJson(JSONObject(StoreJson.eventToJson(e).toString())))
    }
}
