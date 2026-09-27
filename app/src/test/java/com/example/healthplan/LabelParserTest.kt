package com.example.healthplan

import com.example.healthplan.ocr.LabelParser
import org.junit.Assert.*
import org.junit.Test

class LabelParserTest {
    private fun near(e: Double, a: Double?) { assertNotNull(a); assertEquals(e, a!!, 0.051) }

    @Test fun simpleRussianLine() {
        val p = LabelParser.parse("Пищевая ценность в 100 г: белки 3,2 г, жиры 2,5 г, углеводы 4,7 г. Энергетическая ценность 53 ккал / 222 кДж")
        near(3.2, p.protein); near(2.5, p.fat); near(4.7, p.carbs); near(53.0, p.kcal); near(222.0, p.kj)
        assertTrue(p.per100); assertNull(p.portionG); assertFalse(p.kcalFromKj)
    }

    @Test fun ocrNoiseLettersInNumbers() {
        val txt = """ПИЩЕВАЯ ЦЕННОCТЬ НА 100 г прoдукта:
            |Бeлки — З,0 г
            |Жиры — б,5 г
            |Углевoды — 72,О г
            |Энергетическая ценнoсть (калорийность) - 1590 кДж/ 38O ккал""".trimMargin()
        val p = LabelParser.parse(txt)
        near(3.0, p.protein); near(6.5, p.fat); near(72.0, p.carbs); near(380.0, p.kcal); near(1590.0, p.kj)
    }

    @Test fun tableLayout() {
        val p = LabelParser.parse("Пищевая ценность на 100 г\nБелки Жиры Углеводы Энергетическая ценность, ккал\n7,5 1,2 50,1 245\nХранить при t не выше 25 °C")
        near(7.5, p.protein); near(1.2, p.fat); near(50.1, p.carbs); near(245.0, p.kcal)
    }

    @Test fun englishKjOnlyAndSaturatesIgnored() {
        val p = LabelParser.parse("Nutrition facts per 100 g: Energy 1046 kJ, Fat 9.1 g, of which saturates 5.2 g, Carbohydrate 30.5 g, of which sugars 12 g, Fibre 2.1 g, Protein 6.3 g, Salt 0.8 g")
        near(250.0, p.kcal); assertTrue(p.kcalFromKj); near(9.1, p.fat); near(30.5, p.carbs); near(2.1, p.fiber); near(6.3, p.protein)
        assertTrue(p.warnings.any { it.contains("кДж") })
    }

    @Test fun perPortion() {
        val p = LabelParser.parse("Пищевая ценность одной порции (30 г): белки 2,1 г; жиры 4,5 г; углеводы 18 г; 120 ккал")
        assertFalse(p.per100); near(30.0, p.portionG); near(120.0, p.kcal); near(18.0, p.carbs)
    }

    @Test fun milkWithFatPercentPrefix() {
        val p = LabelParser.parse("Молоко питьевое ультрапастеризованное м.д.ж. 3,2%. Пищевая ценность в 100 г: жир 3,2 г, белок 3 г, углеводы 4,7 г. Энергетическая ценность 60 ккал/250 кДж. Состав: молоко нормализованное.")
        near(3.2, p.fat); near(3.0, p.protein); near(4.7, p.carbs); near(60.0, p.kcal)
    }

    @Test fun mixedLatinCyrillicAndFiber() {
        val p = LabelParser.parse("Хлебцы. Пищeвая цeннoсть 100 г: бeлки 11 г, жиpы 3 г, углeвoды 58 г, пищeвые вoлокна 14 г. Энepгетическая ценность 330 kcal")
        near(11.0, p.protein); near(3.0, p.fat); near(58.0, p.carbs); near(14.0, p.fiber); near(330.0, p.kcal)
    }

    @Test fun kcalBeforeNumberAndCommaSeparators() {
        val p = LabelParser.parse("В 100 г: Белки, г 12,6\nЖиры, г 5\nУглеводы, г 1,1\nЭнергетическая ценность, ккал: 105")
        near(12.6, p.protein); near(5.0, p.fat); near(1.1, p.carbs); near(105.0, p.kcal)
    }

    @Test fun lostDecimalWarning() {
        val p = LabelParser.parse("На 100 г: белки 32 г жиры 25 г углеводы 47 г 530 ккал")
        assertTrue(p.warnings.any { it.contains("запятая") })
    }

    @Test fun kcalMismatchWarning() {
        val p = LabelParser.parse("На 100 г: белки 10 г, жиры 10 г, углеводы 10 г, энергетическая ценность 500 ккал")
        assertTrue(p.warnings.any { it.contains("25%") })
    }

    @Test fun fixDigitsKeepsWords() {
        assertEquals("Молоко 3,0 г", LabelParser.fixDigits("Молоко З,0 г"))
        assertEquals("Белки 10 г", LabelParser.fixDigits("Белки 1O г"))
        assertEquals("200 ккал", LabelParser.fixDigits("2OO ккал"))
    }
}

class LabelNameTest {
    @Test fun guessesProductName() {
        assertEquals("Йогурт питьевой клубника 2,5%", LabelParser.guessName("ЙОГУРТ ПИТЬЕВОЙ КЛУБНИКА 2,5%\nСостав: молоко, сахар\nПищевая ценность в 100 г"))
        assertEquals("Хлебцы ржаные", LabelParser.guessName("  \n12\nХлебцы ржаные.\nПищевая ценность на 100 г"))
        assertEquals("", LabelParser.guessName("Пищевая ценность в 100 г: белки 3 г"))
    }
}
