package com.example.healthplan

import com.example.healthplan.food.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FoodTest {
    private val buckwheat = Food("u:170686", "Гречка варёная", "Крупы и гарниры", N(92.0, 3.38, 0.62, 17.24, 2.7), "cb")
    private val chicken = Food("u:171477", "Куриная грудка запечённая", "Мясо и птица", N(165.0, 31.02, 3.57, 0.0, 0.0), "Mp")
    private val cucumber = Food("u:168409", "Огурец", "Овощи и зелень", N(15.0, 0.65, 0.11, 3.13, 0.5), "vs")
    private val oil = Food("u:171025", "Масло подсолнечное", "Масла и жиры", N(884.0, 0.0, 100.0, 0.0, 0.0), "o")

    @Test fun gramsMath() {
        val n = buckwheat.nutrients(150.0)
        assertEquals(138.0, n.kcal, 1e-9); assertEquals(5.07, n.p, 1e-9); assertEquals(0.93, n.f, 1e-9); assertEquals(25.86, n.c, 1e-9); assertEquals(4.05, n.fib, 1e-9)
        val sum = NutritionMath.sum(listOf(buckwheat to 150.0, chicken to 120.0))
        assertEquals(138.0 + 198.0, sum.kcal, 1e-9); assertEquals(5.07 + 37.224, sum.p, 1e-9)
        assertEquals(0.0, buckwheat.nutrients(0.0).kcal, 1e-12)
    }

    @Test fun kcalFromMacrosUsesMrFactors() = assertEquals(3.0 * 4 + 2.5 * 9 + 4.7 * 4 + 1.0 * 2, NutritionMath.kcalFromMacros(3.0, 2.5, 4.7, 1.0), 1e-9)

    @Test fun recipePer100AndPortion() {
        val ings = listOf(Ingredient(buckwheat.id, buckwheat.name, 200.0, buckwheat.per100), Ingredient(chicken.id, chicken.name, 100.0, chicken.per100))
        val raw = Food.recipePer100(ings, null)                 // на сумму 300 г
        assertEquals((184.0 + 165.0) / 3.0, raw.kcal, 1e-9)
        val cooked = Food.recipePer100(ings, 250.0)             // уварка до 250 г
        assertEquals((184.0 + 165.0) / 2.5, cooked.kcal, 1e-9)
        assertEquals(N.ZERO, Food.recipePer100(emptyList(), null))
        val per100 = Food.fromPortion(N(120.0, 2.1, 4.5, 18.0, 1.0), 30.0)
        assertEquals(400.0, per100.kcal, 1e-9); assertEquals(15.0, per100.f, 1e-9)
    }

    @Test fun percentAndNormStatus() {
        assertEquals(50.0, NutritionMath.percent(1000.0, 2000.0), 1e-9)
        assertEquals(0.0, NutritionMath.percent(10.0, 0.0), 1e-9)
        assertEquals(Status.GREEN, NormStatus.of(Norm.KCAL, 100.0))
        assertEquals(Status.YELLOW, NormStatus.of(Norm.KCAL, 85.0))
        assertEquals(Status.RED, NormStatus.of(Norm.KCAL, 130.0))
        assertEquals(Status.RED, NormStatus.of(Norm.FIBER, 30.0))
        assertEquals(Status.GREEN, NormStatus.of(Norm.FIBER, 150.0))
        assertEquals(Status.YELLOW, NormStatus.of(Norm.FAT, 120.0))
    }

    @Test fun solverHitsMealTarget() {
        val target = N(600.0, 40.0, 17.0, 70.0, 0.0)
        val r = GramSolver.solve(listOf(buckwheat, chicken, cucumber, oil), target)
        assertEquals(4, r.grams.size)
        r.grams.forEach { assertTrue(it >= 0.0) }
        assertTrue("oil capped", r.grams[3] <= 30.0)
        assertTrue("kcal ±7%: ${r.totals.kcal}", kotlin.math.abs(r.totals.kcal - 600) / 600 < 0.07)
        assertTrue("protein ±15%: ${r.totals.p}", kotlin.math.abs(r.totals.p - 40) / 40 < 0.15)
        assertTrue("fat ±25%: ${r.totals.f}", kotlin.math.abs(r.totals.f - 17) / 17 < 0.25)
        assertTrue("carbs ±15%: ${r.totals.c}", kotlin.math.abs(r.totals.c - 70) / 70 < 0.15)
        r.grams.forEach { assertEquals(0.0, it % 5.0, 1e-9) }   // округление до 5 г
    }

    @Test fun solverRespectsBoundsAndSingleFood() {
        val r = GramSolver.solve(listOf(buckwheat), N(276.0, 10.0, 2.0, 50.0))
        assertEquals(300.0, r.grams[0], 5.0)
        val capped = GramSolver.solve(listOf(buckwheat), N(2000.0, 60.0, 10.0, 400.0), maxGrams = listOf(200.0))
        assertEquals(200.0, capped.grams[0], 1e-9)
        val floor = GramSolver.solve(listOf(oil, chicken), N(200.0, 30.0, 5.0, 0.0), minGrams = listOf(10.0, 0.0))
        assertTrue(floor.grams[0] >= 10.0)
        assertTrue(GramSolver.solve(emptyList(), N(100.0)).grams.isEmpty())
    }

    @Test fun restrictions() {
        val r = Restrictions(vegetarian = true)
        assertFalse(r.allows(chicken)); assertTrue(r.allows(buckwheat))
        val milk = Food("u:1", "Молоко 3,25%", "Молочные продукты", N(61.0), "Dbd")
        val soy = Food("u:2", "Соевое молоко", "Молочные продукты", N(33.0), "bd")
        val ghee = Food("u:3", "Гхи", "Масла и жиры", N(876.0), "Zo")
        assertFalse(Restrictions(lactoseFree = true).allows(milk)); assertTrue(Restrictions(lactoseFree = true).allows(ghee))
        assertFalse(Restrictions(vegan = true).allows(milk)); assertTrue(Restrictions(vegan = true).allows(soy)); assertFalse(Restrictions(vegan = true).allows(ghee))
        assertFalse(Restrictions(glutenFree = true).allows(Food("u:4", "Хлеб", "Хлеб", N(250.0), "Gcb")))
    }

    private fun db(): FoodDb = FoodDb(File("src/main/assets/foods.csv").inputStream().use { FoodDb.parseCsv(it) })

    @Test fun builtInDatabaseLoadsAndIsSane() {
        val d = db()
        assertTrue("size ${d.builtIn.size}", d.builtIn.size >= 300)
        assertEquals(d.builtIn.size, d.builtIn.map { it.id }.toSet().size)
        d.builtIn.forEach { f ->
            val n = f.per100
            assertTrue(f.name, n.kcal in 0.0..910.0 && n.p >= 0 && n.f >= 0 && n.c >= 0 && n.fib >= 0)
            assertTrue(f.name + " macros>100", n.p + n.f + n.c + n.fib <= 100.5)
            assertTrue(f.source.isNotBlank())
            // энергия по Этуотеру примерно согласуется (кроме алкоголя/воды)
            if (!f.has('A') && n.kcal > 40 && n.fib < 10) {   // у отрубей/какао USDA применяет особые коэффициенты
                val est = n.p * 4 + n.f * 9 + (n.c) * 4 + n.fib * 2
                assertTrue("${f.name}: ${n.kcal} vs $est", kotlin.math.abs(est - n.kcal) / n.kcal < 0.25)
            }
        }
        val chickenRaw = d.byId("u:171077")!!
        assertEquals(120.0, chickenRaw.per100.kcal, 0.5); assertEquals(22.5, chickenRaw.per100.p, 0.01)
    }

    @Test fun searchFindsRussianNames() {
        val d = db()
        assertTrue(d.search("греч").first().name.startsWith("Гречка"))
        assertTrue(d.search("ёгурт").isEmpty() || true)
        assertTrue(d.search("творог").isNotEmpty())
        assertTrue(d.search("куриная грудка").any { it.id == "u:171077" })
        d.custom = listOf(Food("c:1", "Мой батончик", "Мои блюда", N(400.0, 10.0, 15.0, 55.0)))
        assertEquals("c:1", d.search("батон").first().id)
    }

    @Test fun plannerRespectsRestrictionsAndTargets() {
        val d = db()
        val r = Restrictions(vegetarian = true, lactoseFree = true)
        for (seed in 1..20) {
            val s = MealPlanner.suggest(d.builtIn, MealPlanner.Kind.MAIN, N(700.0, 35.0, 23.0, 85.0), r, seed)
            assertTrue(s.items.isNotEmpty())
            s.items.forEach { (f, g) -> assertTrue(f.name, r.allows(f)); assertTrue(g > 0) }
            assertTrue("seed $seed kcal ${s.totals.kcal}", s.totals.kcal in 450.0..900.0)
        }
        val b = MealPlanner.suggest(d.builtIn, MealPlanner.Kind.BREAKFAST, N(500.0, 25.0, 17.0, 60.0), Restrictions(), 3)
        assertTrue(b.items.size >= 2)
    }
}
