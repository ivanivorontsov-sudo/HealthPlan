package com.example.healthplan

import com.example.healthplan.ocr.LabelParser
import org.junit.Assert.*
import org.junit.Test

/** Реальные форматы этикеток и реальный вывод OCR (включая снимок пользователя: сливки, Брянский молочный комбинат). */
class LabelParserRealWorldTest {
    private fun near(e: Double, a: Double?) { assertNotNull("ожидалось $e", a); assertEquals(e, a!!, 0.051) }

    /** Ровно та строка, что видна на упаковке пользователя. */
    @Test fun userCreamInlineLineExact() {
        val p = LabelParser.parse("белки – 2,6 г; жиры -- 10,0 г; углеводы – 4,1 г\nЭнергетическая ценность (калорийность) – 494 кДж/118 ккал")
        near(2.6, p.protein); near(10.0, p.fat); near(4.1, p.carbs); near(118.0, p.kcal); near(494.0, p.kj)
        assertFalse(p.kcalFromMacros); assertFalse(p.kcalFromKj)
    }

    /** Фактический вывод Tesseract (PSM 6) по обрезанному снимку пользователя: строки обрезаны справа. */
    @Test fun userCreamActualOcrOutput() {
        val txt = """RU) ЛИВКИ ПИТЬЕВЬ
            |Ww УЛЬТРАПАСТЕРИЗ
            |Состав: Сливки нормализованные, Г
            |белки —2.6 Г. жиры -- 10,0 г: углеводь
            |Энергетическая ценность (калорийнс
            |Хранить при температуре от +2°C до
            |темпера (442) Не более 3-х су
            |месяц, ran) нанесены на верхнюю чаи
            |Изготовитель: ОАО «Брянский Mano
            |эрянская область, город Брянск, Упиц""".trimMargin()
        val p = LabelParser.parse(txt)
        near(2.6, p.protein); near(10.0, p.fat)
        assertNull("углеводы обрезаны на снимке — не выдумывать", p.carbs)
        assertNull("калорийность обрезана, а без углеводов её не посчитать", p.kcal)
    }

    /** Только нижние строки (то, что показала версия 1.0.0) — ничего не находится, и это честно. */
    @Test fun onlyBottomLinesGiveNothing() {
        val p = LabelParser.parse("месяц, год нанесены на верхнюю ча‹\nИзготовитель: ОАО «Брянский\nМОЛОЧ,\nорянская область, город Брянск, улиц\n-Вразийского экономического союза\nс\nорячая линия 8-800-100-73-77. Info@\nFat ae TT")
        assertNull(p.protein); assertNull(p.carbs); assertNull(p.kcal)
    }

    @Test fun dashVariantsAndColon() {
        for (sep in listOf("-", "–", "—", "--", ":", " ", "‒", "−", " ~")) {
            val p = LabelParser.parse("белки$sep 2,6 г; жиры$sep 10,0 г; углеводы$sep 4,1 г")
            near(2.6, p.protein); near(10.0, p.fat); near(4.1, p.carbs)
        }
    }

    @Test fun kcalCalculatedFromMacrosWhenMissing() {
        val p = LabelParser.parse("Пищевая ценность в 100 г: белки – 2,6 г; жиры — 10,0 г; углеводы: 4,1 г.")
        assertTrue(p.kcalFromMacros); near(2.6 * 4 + 10.0 * 9 + 4.1 * 4, p.kcal)
        assertTrue(p.warnings.any { "рассчитана по БЖУ" in it })
        assertEquals(3, p.foundDirect)
    }

    @Test fun genitiveFormsAndKcalBeforeKj() {
        val p = LabelParser.parse("Пищевая ценность в 100 г продукта: белков — 3,0 г, жиров — 3,2 г, углеводов — 4,7 г; энергетическая ценность — 58 ккал/243 кДж")
        near(3.0, p.protein); near(3.2, p.fat); near(4.7, p.carbs); near(58.0, p.kcal); near(243.0, p.kj)
    }

    @Test fun kjSlashKcalTableHeader() {
        val p = LabelParser.parse("Пищевая ценность на 100 г:\nЭнергетическая ценность, кДж/ккал 1650/395\nБелки, г 8,1\nЖиры, г 17,9\nУглеводы, г 49,6")
        near(395.0, p.kcal); near(1650.0, p.kj); near(8.1, p.protein); near(17.9, p.fat); near(49.6, p.carbs)
    }

    @Test fun usNutritionFactsPerServing() {
        val p = LabelParser.parse("Nutrition Facts\n8 servings per container\nServing size 2/3 cup (55g)\nCalories 230\nTotal Fat 8g 10%\nSaturated Fat 1g 5%\nTrans Fat 0g\nCholesterol 0mg 0%\nSodium 160mg 7%\nTotal Carbohydrate 37g 13%\nDietary Fiber 4g 14%\nTotal Sugars 12g\nProtein 3g")
        near(230.0, p.kcal); near(8.0, p.fat); near(37.0, p.carbs); near(4.0, p.fiber); near(3.0, p.protein)
        near(55.0, p.portionG); assertFalse(p.per100)
    }

    @Test fun euLabelEnglish() {
        val p = LabelParser.parse("Typical values per 100g\nEnergy 1569kJ/375kcal\nFat 14.2g\nof which saturates 8.1g\nCarbohydrate 55.0g\nof which sugars 31.0g\nFibre 2.5g\nProtein 5.8g\nSalt 0.6g")
        near(375.0, p.kcal); near(1569.0, p.kj); near(14.2, p.fat); near(55.0, p.carbs); near(2.5, p.fiber); near(5.8, p.protein)
    }

    @Test fun ocrConfusionsInWordsAndNumbers() {
        val p = LabelParser.parse("6елки 2,б г, жиpы 1O,O г, углевoдь 4,l г, энергетичесая ценнoсть 118 ккап")
        near(2.6, p.protein); near(10.0, p.fat); near(4.1, p.carbs); near(118.0, p.kcal)
    }

    @Test fun lostCommaResolvedByDecimalTwin() {
        val p = LabelParser.parse("белки —26 Г, жиры -- 10,0 г\n\n— другой вариант распознавания —\nбелки — 2.6 г, жиры 10,0 г")
        near(2.6, p.protein); near(10.0, p.fat)
    }

    @Test fun bzhuSlashFormat() {
        val p = LabelParser.parse("Сливки 10%. Б/Ж/У на 100 г: 2,6/10/4,1")
        near(2.6, p.protein); near(10.0, p.fat); near(4.1, p.carbs); assertTrue(p.kcalFromMacros)
    }

    @Test fun splitDecimalsJoined() {
        val p = LabelParser.parse("Белки 2, 6 г Жиры 1 0,0 г Углеводы 4 ,1 г")
        near(2.6, p.protein); near(10.0, p.fat); near(4.1, p.carbs)
    }

    @Test fun valuesOnNextLines() {
        val p = LabelParser.parse("Пищевая ценность на 100 г\nБелки\n2,6 г\nЖиры\n10,0 г\nУглеводы\n4,1 г\nЭнергетическая ценность\n118 ккал")
        near(2.6, p.protein); near(10.0, p.fat); near(4.1, p.carbs); near(118.0, p.kcal)
    }

    @Test fun doesNotStealNumbersFromUnrelatedNextLine() {
        val p = LabelParser.parse("белки 2,6 г, жиры 10,0 г, углеводы\nХранить при температуре от +2 до +6 °C")
        assertNull(p.carbs)
    }

    @Test fun saturatedAndSkimmedIgnored() {
        val p = LabelParser.parse("Молоко обезжиренное 0,5%. На 100 г: жиры 10 г, в т.ч. насыщенные жиры 6,5 г; белки 3 г; углеводы 4,7 г; 136 ккал")
        near(10.0, p.fat); near(3.0, p.protein); near(136.0, p.kcal)
    }

    @Test fun energyInParenthesesPer100() {
        val p = LabelParser.parse("Энергетическая ценность (калорийность) на 100 г продукта: 494 кДж / 118 ккал. Белки 2,6 г, жиры 10 г, углеводы 4,1 г")
        near(118.0, p.kcal); near(494.0, p.kj); near(2.6, p.protein)
    }

    @Test fun fiberRussianVariants() {
        near(3.5, LabelParser.parse("Пищевые волокна – 3,5 г").fiber)
        near(7.0, LabelParser.parse("клетчатка: 7 г").fiber)
        near(2.1, LabelParser.parse("пищ. волокна 2,1 г").fiber)
    }

    @Test fun kjOnlyGivesKcal() {
        val p = LabelParser.parse("Энергетическая ценность 1046 кДж; белки 6,3 г; жиры 9,1 г; углеводы 30,5 г")
        near(250.0, p.kcal); assertTrue(p.kcalFromKj); assertFalse(p.kcalFromMacros)
    }
}
