package com.example.healthplan.food

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Подбор граммов выбранных продуктов под цель приёма пищи (ккал, Б, Ж, У).
 * Минимизирует взвешенную сумму квадратов относительных отклонений при ограничениях 0 ≤ x ≤ max
 * (метод покоординатного спуска для выпуклой квадратичной задачи с ограничениями-«коробкой»).
 */
object GramSolver {
    data class Result(val grams: List<Double>, val totals: N, val errorPct: Map<String, Double>)

    /** Веса: калории важнее всего, затем белок. */
    var wKcal = 4.0; var wP = 2.0; var wF = 1.0; var wC = 1.0

    fun solve(foods: List<Food>, target: N, maxGrams: List<Double>? = null, minGrams: List<Double>? = null,
              roundTo: Double = 5.0, iterations: Int = 400): Result {
        val n = foods.size
        if (n == 0) return Result(emptyList(), N.ZERO, emptyMap())
        val hi = maxGrams ?: foods.map { it.maxGrams() }
        val lo = minGrams ?: List(n) { 0.0 }
        // Матрица: строки — нутриенты (нормированные на цель), столбцы — продукты, на 1 г.
        val tv = doubleArrayOf(target.kcal, target.p, target.f, target.c)
        val w = doubleArrayOf(wKcal, wP, wF, wC)
        val rows = 4
        val a = Array(rows) { DoubleArray(n) }
        for (j in 0 until n) {
            val v = foods[j].per100.asList()
            for (k in 0 until rows) a[k][j] = if (tv[k] > 0.1) v[k] / 100.0 / tv[k] else 0.0
        }
        val b = DoubleArray(rows) { if (tv[it] > 0.1) 1.0 else 0.0 }
        // Старт: равное распределение калорий
        val x = DoubleArray(n) { j ->
            val kcal1 = foods[j].per100.kcal / 100.0
            if (kcal1 <= 0) lo[j] else (target.kcal / n / kcal1).coerceIn(lo[j], hi[j])
        }
        val ridge = 1e-7
        for (it in 0 until iterations) {
            var maxStep = 0.0
            for (j in 0 until n) {
                // r = A x - b
                var g = 0.0; var h = ridge
                for (k in 0 until rows) {
                    var r = -b[k]
                    for (m in 0 until n) r += a[k][m] * x[m]
                    g += w[k] * a[k][j] * r
                    h += w[k] * a[k][j] * a[k][j]
                }
                val nx = (x[j] - g / h).coerceIn(lo[j], hi[j])
                maxStep = max(maxStep, abs(nx - x[j]))
                x[j] = nx
            }
            if (maxStep < 1e-4) break
        }
        val grams = x.mapIndexed { j, v -> (max(lo[j], min(hi[j], (v / roundTo).roundToInt() * roundTo))) }
        val totals = foods.zip(grams).fold(N.ZERO) { acc, (f, g) -> acc + f.nutrients(g) }
        fun err(v: Double, t: Double) = if (t <= 0) 0.0 else (v - t) / t * 100.0
        return Result(grams, totals, mapOf("kcal" to err(totals.kcal, target.kcal), "p" to err(totals.p, target.p),
            "f" to err(totals.f, target.f), "c" to err(totals.c, target.c)))
    }
}
