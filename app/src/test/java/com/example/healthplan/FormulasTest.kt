package com.example.healthplan

import com.example.healthplan.core.*
import org.junit.Assert.*
import org.junit.Test

class FormulasTest {
    private val eps = 0.01

    @Test fun mifflinMaleAndFemale() {
        assertEquals(1794.45, Formulas.mifflin(Sex.MALE, 85.0, 178.0, 35), eps)
        assertEquals(1322.05, Formulas.mifflin(Sex.FEMALE, 60.0, 165.0, 30), eps)
    }

    @Test fun harrisBenedictRevised() {
        assertEquals(1882.634, Formulas.harrisBenedict(Sex.MALE, 85.0, 178.0, 35), eps)
        assertEquals(447.593 + 9.247 * 60 + 3.098 * 165 - 4.330 * 30, Formulas.harrisBenedict(Sex.FEMALE, 60.0, 165.0, 30), eps)
    }

    @Test fun katchMcArdle() = assertEquals(1752.4, Formulas.katchMcArdle(80.0, 20.0), eps)

    @Test fun bmiAndClasses() {
        assertEquals(26.827, Formulas.bmi(85.0, 178.0), 0.001)
        assertTrue(Formulas.bmiClass(26.8).startsWith("Избыточная"))
        assertTrue(Formulas.bmiClass(18.5).startsWith("Норма"))
        assertTrue(Formulas.bmiClass(24.99).startsWith("Норма"))
        assertTrue(Formulas.bmiClass(18.4).contains("дефицит"))
        assertTrue(Formulas.bmiClass(30.0).contains("I степени"))
        assertTrue(Formulas.bmiClass(35.0).contains("II степени"))
        assertTrue(Formulas.bmiClass(40.0).contains("III степени"))
        assertEquals(Level.OK, Formulas.bmiLevel(22.0))
        assertEquals(Level.BAD, Formulas.bmiLevel(36.0))
    }

    @Test fun healthyRange() {
        val (lo, hi) = Formulas.healthyWeightRange(178.0)
        assertEquals(58.615, lo, 0.01); assertEquals(78.893, hi, 0.01)
    }

    @Test fun navyBodyFat() {
        assertEquals(18.80, Formulas.navyBodyFat(Sex.MALE, 178.0, 40.0, 90.0, null)!!, 0.01)
        assertEquals(29.74, Formulas.navyBodyFat(Sex.FEMALE, 165.0, 33.0, 75.0, 100.0)!!, 0.01)
        assertNull(Formulas.navyBodyFat(Sex.FEMALE, 165.0, 33.0, 75.0, null))
        assertNull(Formulas.navyBodyFat(Sex.MALE, 178.0, 40.0, 38.0, null))   // талия < шеи
    }

    @Test fun waistRatios() {
        assertEquals(Level.OK, Formulas.whtrClass(0.45).second)
        assertEquals(Level.WARN, Formulas.whtrClass(0.5).second)
        assertEquals(Level.BAD, Formulas.whtrClass(0.6).second)
        assertEquals(Level.BAD, Formulas.whrClass(Sex.MALE, 0.90).second)
        assertEquals(Level.OK, Formulas.whrClass(Sex.MALE, 0.89).second)
        assertEquals(Level.BAD, Formulas.whrClass(Sex.FEMALE, 0.85).second)
        assertEquals(Level.WARN, Formulas.waistClass(Sex.MALE, 95.0).second)
        assertEquals(Level.BAD, Formulas.waistClass(Sex.FEMALE, 89.0).second)
    }

    @Test fun targetCaloriesLoseKeepGainAndFloor() {
        val tdee = 1794.45 * 1.4
        val lose = Formulas.targetCalories(Sex.MALE, tdee, Goal.LOSE, LossRate.NORMAL)
        assertEquals(tdee - 550.0, lose.kcal, eps)
        assertEquals(-0.5, lose.weeklyChangeKg, 0.001)
        assertFalse(lose.floorApplied)
        assertEquals(tdee, Formulas.targetCalories(Sex.MALE, tdee, Goal.KEEP, LossRate.NORMAL).kcal, eps)
        val gain = Formulas.targetCalories(Sex.MALE, tdee, Goal.GAIN, LossRate.NORMAL)
        assertEquals(tdee * 1.1, gain.kcal, eps)
        val floor = Formulas.targetCalories(Sex.FEMALE, 1500.0, Goal.LOSE, LossRate.FAST)
        assertEquals(1200.0, floor.kcal, eps); assertTrue(floor.floorApplied); assertEquals(300.0, floor.deficit, eps)
        // ниже минимума даже при поддержании — не повышаем выше TDEE
        val tiny = Formulas.targetCalories(Sex.FEMALE, 1100.0, Goal.LOSE, LossRate.SLOW)
        assertEquals(1100.0, tiny.kcal, eps)
    }

    @Test fun weeksToGoal() {
        assertEquals(10.0, Formulas.weeksToGoal(85.0, 80.0, -0.5)!!, eps)
        assertNull(Formulas.weeksToGoal(85.0, 90.0, -0.5))
        assertEquals(0.0, Formulas.weeksToGoal(80.0, 80.0, -0.5)!!, eps)
    }

    @Test fun macrosByMrShareAndMinimums() {
        val keep = Formulas.macros(2000.0, 80.0, Activity.G1, Goal.KEEP, 35)
        assertEquals(70.0, keep.proteinG, eps); assertEquals(66.667, keep.fatG, 0.01); assertEquals(280.0, keep.carbsG, eps)
        val lose = Formulas.macros(2000.0, 80.0, Activity.G1, Goal.LOSE, 35)
        assertEquals(96.0, lose.proteinG, eps); assertEquals(254.0, lose.carbsG, eps)
        val g4 = Formulas.macros(3800.0, 70.0, Activity.G4, Goal.KEEP, 25)
        assertEquals(3800 * 0.12 / 4, g4.proteinG, eps)
        // energy balance: 4/9/4
        assertEquals(2000.0, keep.proteinG * 4 + keep.fatG * 9 + keep.carbsG * 4, 0.01)
    }

    @Test fun waterAndFiber() {
        assertEquals(1400.0, Formulas.drinksMl(Sex.MALE, Activity.G1, 35), eps)
        assertEquals(1800.0, Formulas.drinksMl(Sex.FEMALE, Activity.G4, 35), eps)
        assertEquals(1500.0, Formulas.drinksMl(Sex.MALE, Activity.G1, 70), eps)
        assertEquals(25.0, Formulas.fiberTarget(2000.0), eps)
        assertEquals(42.0.coerceAtMost(40.0), Formulas.fiberTarget(3000.0), eps)
    }

    @Test fun calcIntegrates() {
        val c = Calc.of(Profile(sex = Sex.MALE, age = 35, heightCm = 178.0, weightKg = 85.0, activity = Activity.G1, goal = Goal.LOSE,
            targetWeightKg = 80.0, waistCm = 90.0, neckCm = 40.0, hipCm = 100.0))
        assertEquals("Миффлин–Сан Жеор", c.bmrUsedName)
        assertEquals(18.80, c.navyFat!!, 0.01)
        assertEquals(90.0 / 178.0, c.whtr!!, 1e-9)
        assertEquals(10.0, c.weeks!!, 0.01)
        val k = Calc.of(Profile(weightKg = 80.0, bodyFatPct = 20.0))
        assertEquals(1752.4, k.bmrUsed, eps)
    }
}
