package com.example.healthplan.ocr

/**
 * Разбор текста этикетки (результат OCR) → пищевая ценность.
 * Устойчив к: запятым как десятичному разделителю, смешению латиницы/кириллицы в словах,
 * типичным ошибкам OCR в числах (О/o→0, З→3, б→6, l/I/|→1), табличной вёрстке «заголовки строкой, числа ниже»,
 * английским этикеткам (energy/protein/fat/carbohydrate/fibre), кДж без ккал (ккал = кДж / 4,184).
 */
object LabelParser {

    data class Parsed(
        val kcal: Double?, val kj: Double?, val protein: Double?, val fat: Double?, val carbs: Double?, val fiber: Double?,
        val per100: Boolean, val portionG: Double?, val kcalFromKj: Boolean, val warnings: List<String>
    ) {
        val found get() = listOfNotNull(kcal, protein, fat, carbs).size
    }

    // Латиница → кириллица по внешнему сходству (для поиска ключевых слов независимо от раскладки OCR)
    private val SKELETON = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к', 'm' to 'м', 'o' to 'о',
        'p' to 'р', 't' to 'т', 'x' to 'х', 'y' to 'у', 'ё' to 'е', 'u' to 'и', 'r' to 'г', '6' to 'б'
    )

    fun skeleton(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) sb.append(SKELETON[ch] ?: ch)
        return sb.toString()
    }

    private val NUM_CONFUSION = mapOf('o' to '0', 'O' to '0', 'о' to '0', 'О' to '0', 'З' to '3', 'з' to '3', 'б' to '6',
        'l' to '1', 'I' to '1', '|' to '1', 'і' to '1')

    /** Исправление типичных ошибок OCR внутри «числовых» токенов; длина строки сохраняется. */
    private val NOT_NAME = listOf("состав", "ценност", "белк", "жир", "углевод", "энерг", "ккал", "кдж", "хранить", "масса", "изготов",
        "срок", "годн", "гост", "сто ", "ту ", "адрес", "nutrition", "ingredients", "energy", "protein", "per 100", "в 100", "на 100")

    /** Предполагаемое название продукта — первая «словесная» строка, не похожая на состав/пищевую ценность. */
    fun guessName(text: String): String {
        for (line in text.lines()) {
            val t = line.trim().trim('.', ',', ':', ';', '-', '—', '*').trim()
            if (t.length !in 3..60 || t.count { it.isLetter() } < 3) continue
            val low = t.lowercase()
            if (NOT_NAME.any { low.contains(it) }) continue
            val letters = t.filter { it.isLetter() }
            val caps = letters.count { it.isUpperCase() } > letters.length * 0.6
            return if (caps) low.replaceFirstChar { it.uppercase() } else t
        }
        return ""
    }

    fun fixDigits(s: String): String {
        val chars = s.toCharArray()
        val tokenRe = Regex("[0-9oOоОЗзбlI|і]+(?:[.,][0-9oOоОЗзбlI|і]+)?")
        for (m in tokenRe.findAll(s)) {
            val t = m.value
            val digits = t.count { it.isDigit() }
            val letters = t.count { !it.isDigit() && it != '.' && it != ',' }
            // токен должен в основном состоять из цифр и не быть частью слова
            val before = if (m.range.first > 0) s[m.range.first - 1] else ' '
            val after = if (m.range.last + 1 < s.length) s[m.range.last + 1] else ' '
            val inWord = (before.isLetter() && before !in "гgк") || (after.isLetter() && after !in "гgкkм%")
            if (digits >= 1 && (digits >= letters || t.length <= 5) && !inWord) {
                for (i in m.range) NUM_CONFUSION[chars[i]]?.let { chars[i] = it }
            }
        }
        return String(chars)
    }

    private fun kw(vararg stems: String) = Regex(stems.joinToString("|") { Regex.escape(skeleton(it)) })

    private val PROTEIN = kw("белк", "белок", "protein", "протеин")
    private val FAT = kw("жир", "fat", "lipid")
    private val CARBS = kw("углевод", "carbohydrate", "carbs")
    private val FIBER = kw("пищевые волокна", "пищ. волокна", "клетчатк", "волокн", "fibre", "fiber")
    private val KCAL_UNIT = Regex("(ккал|кка[лп]|kcal|kkal|ккaл|кал(?![а-я]))", RegexOption.IGNORE_CASE)
    private val KJ_UNIT = Regex("(кдж|kj|кдж|кдж|kдж|кдж)", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("(\\d+(?:[.,]\\d+)?)")

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    fun parse(raw: String): Parsed {
        val text = fixDigits(raw.replace('\u00A0', ' ').replace('—', '-').replace('–', '-'))
        val sk = skeleton(text)           // та же длина, индексы совпадают
        val warnings = mutableListOf<String>()

        // --- БЖУ: число после ключевого слова (до следующего ключевого слова или 45 символов)
        val allKeys = listOf(PROTEIN, FAT, CARBS, FIBER)
        fun valueAfter(re: Regex, exclude: Regex? = null): Double? {
            for (m in re.findAll(sk)) {
                val ctxStart = maxOf(0, m.range.first - 12)
                if (exclude != null && exclude.containsMatchIn(sk.substring(ctxStart, m.range.first))) continue
                val from = m.range.last + 1
                var to = minOf(sk.length, from + 45)
                for (k in allKeys + listOf(KCAL_UNIT, Regex("энерг|калор|energy".let { skeleton(it) }))) {
                    val n = k.find(sk, from)
                    if (n != null && n.range.first < to && n.range.first > from) to = n.range.first
                }
                val seg = text.substring(from, to)
                // пропускаем «(г)», «, г», «в т.ч.» и т.п.
                val nm = NUMBER.find(seg) ?: continue
                // не принимать «100» из «на 100 г», если за ним ещё есть число
                var v = num(nm.value)
                val rest = seg.substring(nm.range.last + 1)
                if (v != null && v == 100.0 && Regex("^\\s*(г|g|мл|ml)").containsMatchIn(rest)) {
                    val n2 = NUMBER.find(rest); if (n2 != null) v = num(n2.value)
                }
                if (v != null) return v
            }
            return null
        }
        val saturated = Regex(skeleton("насыщ|saturat|trans|транс|в т.ч|в том числе|including"))
        var protein = valueAfter(PROTEIN)
        var fat = valueAfter(FAT, saturated)
        var carbs = valueAfter(CARBS)
        var fiber = valueAfter(FIBER)

        // --- Табличная вёрстка: строка с ≥2 ключевыми словами без чисел + следующая строка с числами
        var kcal: Double? = null; var kj: Double? = null
        val energyKw = Regex(skeleton("энерг|калор|ккал|kcal|energy"))
        val lines = text.lines()
        for (i in 0 until lines.size - 1) {
            val skl = skeleton(lines[i])
            val order = listOf("p" to PROTEIN, "f" to FAT, "c" to CARBS, "e" to energyKw)
                .mapNotNull { (k, re) -> re.find(skl)?.let { k to it.range.first } }.sortedBy { it.second }
            if (order.count { it.first != "e" } >= 2 && NUMBER.findAll(lines[i]).none { (num(it.value) ?: 0.0) != 100.0 }) {
                val nums = NUMBER.findAll(lines[i + 1]).mapNotNull { num(it.value) }.toList()
                if (nums.size >= order.size) {
                    order.forEachIndexed { idx, (k, _) ->
                        when (k) { "p" -> protein = nums[idx]; "f" -> fat = nums[idx]; "c" -> carbs = nums[idx]; "e" -> kcal = nums[idx] }
                    }
                }
            }
        }

        // --- Энергия: «250 ккал», «1046 кДж»
        fun isKcal(after: String) = KCAL_UNIT.find(after)?.range?.first == 0 ||
            Regex("^(ккал|кка[лl]|ксаl|ккаl)").containsMatchIn(skeleton(after))
        fun isKj(after: String) = KJ_UNIT.find(after)?.range?.first == 0 ||
            Regex("^(кдж|к[дd]ж|кj|кд\\*)").containsMatchIn(skeleton(after))
        for (m in NUMBER.findAll(text)) {
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 8)).trimStart().lowercase()
            val v = num(m.value) ?: continue
            if (kcal == null && isKcal(after)) kcal = v
            if (kj == null && isKj(after)) kj = v
        }
        if (kcal == null) { // «ккал 250» / «Энергетическая ценность, ккал: 250» (в пределах строки)
            KCAL_UNIT.find(text.lowercase())?.let { u ->
                val lineEnd = text.indexOf('\n', u.range.last + 1).let { if (it < 0) text.length else it }
                val seg = text.substring(u.range.last + 1, minOf(lineEnd, u.range.last + 14))
                NUMBER.find(seg)?.let { kcal = num(it.value) }
            }
        }
        if (kcal == null) { // «энергетическая ценность 250» без единиц
            Regex(skeleton("калорийност|энергетическая ценност|energy")).find(sk)?.let { u ->
                val seg = text.substring(u.range.last + 1, minOf(text.length, u.range.last + 40))
                val first = NUMBER.findAll(seg).firstOrNull { num(it.value) != 100.0 }
                if (first != null) {
                    val after = seg.substring(first.range.last + 1).trimStart().lowercase()
                    val v = num(first.value)
                    if (v != null && !isKj(after) && v in 5.0..950.0) kcal = v
                }
            }
        }
        var fromKj = false
        if (kcal == null && kj != null) { kcal = kj!! / 4.184; fromKj = true }

        // --- На 100 г или на порцию
        val per100 = Regex("(на|в|per|/)\\s*100\\s*(г|g|мл|ml|гр)", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            Regex("100\\s*(г|g|мл|ml)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        var portion: Double? = null
        Regex("(порци[яиюе]|serving|упаковк)[^0-9]{0,25}(\\d+(?:[.,]\\d+)?)\\s*(г|g|мл|ml)", RegexOption.IGNORE_CASE)
            .find(text)?.let { portion = num(it.groupValues[2]) }

        // --- Проверки правдоподобия
        val sumMacro = (protein ?: 0.0) + (fat ?: 0.0) + (carbs ?: 0.0) + (fiber ?: 0.0)
        if (per100 && sumMacro > 100.5) warnings += "Сумма БЖУ больше 100 г на 100 г — вероятно, потеряна запятая. Проверьте значения."
        if (kcal != null && protein != null && fat != null && carbs != null) {
            val est = protein!! * 4 + fat!! * 9 + carbs!! * 4
            if (est > 20 && kotlin.math.abs(est - kcal!!) / est > 0.25) warnings += "Калорийность отличается от расчёта по БЖУ (≈${est.toInt()} ккал) более чем на 25%."
        }
        if (fromKj) warnings += "Калорийность пересчитана из кДж (÷ 4,184)."
        if (!per100 && portion != null) warnings += "Похоже, значения указаны на порцию $portion г — будут пересчитаны на 100 г."
        return Parsed(kcal, kj, protein, fat, carbs, fiber, per100 || portion == null, if (per100) null else portion, fromKj, warnings)
    }
}
