package com.example.healthplan.food

import kotlin.random.Random

/** Генератор примеров блюд на приём пищи из встроенной базы с учётом ограничений. */
object MealPlanner {
    enum class Kind { BREAKFAST, MAIN, SNACK }

    data class Suggestion(val items: List<Pair<Food, Double>>, val totals: N)

    fun kindOf(name: String): Kind = when {
        name == "Завтрак" -> Kind.BREAKFAST
        name == "Обед" || name == "Ужин" -> Kind.MAIN
        else -> Kind.SNACK
    }

    private fun pick(list: List<Food>, rnd: Random, exclude: Set<String> = emptySet()): Food? =
        list.filter { it.id !in exclude }.let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] }

    fun suggest(foods: List<Food>, kind: Kind, target: N, r: Restrictions, seed: Int): Suggestion {
        val rnd = Random(seed)
        val ok = foods.filter { r.allows(it) && !it.isCustom && !it.isDryOrRaw && !it.has('x') && !it.has('A') }
        val chosen = mutableListOf<Food>()
        when (kind) {
            Kind.BREAKFAST -> {
                val porridge = ok.filter { it.isCarb && it.isBreakfast }
                val prot = ok.filter { it.isProtein && it.isBreakfast }
                pick(prot, rnd)?.let { chosen += it }
                pick(porridge, rnd, chosen.map { it.id }.toSet())?.let { chosen += it }
                pick(ok.filter { it.isFruit }, rnd)?.let { chosen += it }
            }
            Kind.MAIN -> {
                val prot = ok.filter { it.isProtein && !it.isBreakfast || it.isProtein && it.category.startsWith("Бобовые") }
                pick(prot.ifEmpty { ok.filter { it.isProtein } }, rnd)?.let { chosen += it }
                pick(ok.filter { it.isCarb && !it.isBreakfast || it.isCarb && it.category.startsWith("Крупы") }, rnd, chosen.map { it.id }.toSet())?.let { chosen += it }
                pick(ok.filter { it.isVeg && it.category.startsWith("Овощи") }, rnd)?.let { chosen += it }
                pick(ok.filter { it.isFat && it.category.startsWith("Масла") }, rnd)?.let { chosen += it }
            }
            Kind.SNACK -> {
                pick(ok.filter { it.isSnack && it.isProtein }.ifEmpty { ok.filter { it.isSnack } }, rnd)?.let { chosen += it }
                pick(ok.filter { it.isFruit || it.isFat && it.category.startsWith("Орехи") }, rnd, chosen.map { it.id }.toSet())?.let { chosen += it }
            }
        }
        if (chosen.isEmpty()) return Suggestion(emptyList(), N.ZERO)
        val maxG = chosen.map { f -> if (f.isFruit) 250.0 else if (f.isVeg) 300.0 else f.maxGrams() }
        val minG = chosen.map { f -> if (f.isVeg) 100.0 else if (f.isFat && f.category.startsWith("Масла")) 5.0 else 0.0 }
        val res = GramSolver.solve(chosen, target, maxG, minG)
        val items = chosen.zip(res.grams).filter { it.second > 0 }
        return Suggestion(items, res.totals)
    }
}
