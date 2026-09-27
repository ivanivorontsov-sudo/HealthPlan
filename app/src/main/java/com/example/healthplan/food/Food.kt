package com.example.healthplan.food

import kotlin.math.max

/** Пищевая ценность (ккал, белки, жиры, усвояемые углеводы, клетчатка). */
data class N(val kcal: Double = 0.0, val p: Double = 0.0, val f: Double = 0.0, val c: Double = 0.0, val fib: Double = 0.0) {
    operator fun plus(o: N) = N(kcal + o.kcal, p + o.p, f + o.f, c + o.c, fib + o.fib)
    operator fun times(k: Double) = N(kcal * k, p * k, f * k, c * k, fib * k)
    fun asList() = listOf(kcal, p, f, c, fib)
    companion object { val ZERO = N() }
}

data class Ingredient(val foodId: String, val name: String, val grams: Double, val per100: N)

/**
 * Продукт или блюдо. Значения всегда на 100 г. [portionG] — вес порции (если задан).
 * id: "u:<fdc_id>" — USDA, "r:<n>" — блюдо по рецептуре, "c:<uuid>" — свой продукт/блюдо.
 */
data class Food(
    val id: String,
    val name: String,
    val category: String,
    val per100: N,
    val tags: String = "",
    val source: String = "",
    val portionG: Double? = null,
    val recipe: List<Ingredient> = emptyList(),
    val cookedWeightG: Double? = null,
) {
    val isCustom get() = id.startsWith("c:")
    fun has(tag: Char) = tags.indexOf(tag) >= 0
    fun nutrients(grams: Double) = per100 * (grams / 100.0)

    /** Роли для генератора меню. */
    val isProtein get() = has('p'); val isCarb get() = has('c'); val isVeg get() = has('v')
    val isFruit get() = has('f'); val isFat get() = has('o'); val isBreakfast get() = has('b'); val isSnack get() = has('s')
    val isDryOrRaw get() = DRY_RAW.containsMatchIn(name.lowercase())

    /** Разумный максимум граммов на один приём (для подбора граммов). */
    fun maxGrams(): Double = when {
        category.startsWith("Масла") -> 30.0
        category.startsWith("Орехи") -> 40.0
        category.startsWith("Сладости") -> 50.0
        category.startsWith("Напитки") -> 500.0
        name.contains("сух", true) -> 120.0
        category.startsWith("Хлеб") -> 120.0
        category.startsWith("Молочные") && per100.f > 15 -> 60.0
        category.startsWith("Овощи") || category.startsWith("Фрукты") -> 400.0
        category.startsWith("Готовые") && per100.kcal < 60 -> 500.0
        else -> 350.0
    }

    companion object {
        private val DRY_RAW = Regex("сух(ой|ая|ое|ие)|сыр(ой|ая|ое|ые)")
        /** Состав блюда-рецепта на 100 г готового веса. */
        fun recipePer100(ings: List<Ingredient>, cookedWeightG: Double?): N {
            val total = ings.fold(N.ZERO) { a, i -> a + i.per100 * (i.grams / 100.0) }
            val raw = ings.sumOf { it.grams }
            val w = cookedWeightG?.takeIf { it > 0 } ?: raw
            return if (w <= 0) N.ZERO else total * (100.0 / w)
        }
        fun fromPortion(portion: N, portionG: Double): N = if (portionG <= 0) N.ZERO else portion * (100.0 / portionG)
    }
}

data class Restrictions(
    val vegetarian: Boolean = false, val vegan: Boolean = false, val lactoseFree: Boolean = false,
    val glutenFree: Boolean = false, val noFish: Boolean = false, val noNuts: Boolean = false, val noEggs: Boolean = false,
) {
    fun allows(f: Food): Boolean {
        if ((vegetarian || vegan) && (f.has('M') || f.has('F'))) return false
        if (vegan && (f.has('D') || f.has('E') || f.has('H') || f.has('Z'))) return false
        if (vegan && f.category.startsWith("Молочные") && !(f.name.contains("Соев") || f.name.contains("Миндал"))) return false
        if (lactoseFree && f.has('D')) return false
        if (glutenFree && f.has('G')) return false
        if (noFish && f.has('F')) return false
        if (noNuts && f.has('N')) return false
        if (noEggs && f.has('E')) return false
        return true
    }
}

object NutritionMath {
    fun sum(lines: List<Pair<Food, Double>>): N = lines.fold(N.ZERO) { a, (f, g) -> a + f.nutrients(g) }
    /** Энергия из БЖУ по коэффициентам МР (прил. 3): 4/9/4, клетчатка 2 ккал/г. */
    fun kcalFromMacros(p: Double, f: Double, c: Double, fib: Double = 0.0) = p * 4 + f * 9 + c * 4 + fib * 2
    fun percent(value: Double, target: Double): Double = if (target <= 0) 0.0 else value / target * 100.0
    fun safeRound(x: Double) = max(0.0, x)
}

enum class Norm(val title: String) { KCAL("Калории"), PROTEIN("Белки"), FAT("Жиры"), CARBS("Углеводы"), FIBER("Клетчатка"), WATER("Вода") }
enum class Status { GREEN, YELLOW, RED }

/**
 * Цветовая оценка выполнения нормы за день («рекомендация» приложения):
 * зелёный — в целевом коридоре, жёлтый — близко к границе, красный — сильный недобор/перебор.
 */
object NormStatus {
    fun of(norm: Norm, pct: Double): Status {
        val (g1, g2, y1, y2) = when (norm) {
            Norm.KCAL -> listOf(90.0, 110.0, 80.0, 120.0)
            Norm.PROTEIN -> listOf(90.0, 130.0, 75.0, 160.0)
            Norm.FAT -> listOf(80.0, 110.0, 60.0, 125.0)
            Norm.CARBS -> listOf(85.0, 115.0, 70.0, 130.0)
            Norm.FIBER -> listOf(90.0, 250.0, 60.0, 300.0)
            Norm.WATER -> listOf(90.0, 200.0, 60.0, 250.0)
        }
        return when {
            pct in g1..g2 -> Status.GREEN
            pct in y1..y2 -> Status.YELLOW
            else -> Status.RED
        }
    }
}
