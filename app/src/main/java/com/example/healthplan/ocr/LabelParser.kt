package com.example.healthplan.ocr

import kotlin.math.abs
import kotlin.math.min

/**
 * Разбор текста этикетки (результат OCR) → пищевая ценность.
 *
 * Поддерживает:
 * - строчный формат «белки – 2,6 г; жиры -- 10,0 г; углеводы: 4,1 г» (дефис, en/em-тире, двойной дефис, двоеточие, «~»);
 * - столбцы «Белки, г 12,6» / «Белки ........ 2,6 г», значение на следующей строке;
 * - таблицы «заголовки строкой, числа строкой ниже», «Б/Ж/У: 2,6/10/4,1», «кДж/ккал 494/118»;
 * - английские этикетки (energy/calories/protein/fat/carbohydrate/fibre), US Nutrition Facts (на порцию);
 * - ошибки OCR: латиница вместо кириллицы, О/o→0, З→3, б→6, l/I/|→1 в числах, «2, 6» и «1 0,0», искажённые
 *   ключевые слова (нечёткое сравнение: «углеводь», «6елки», «энергетичесая»), «ккап/kcal/ккаl», «кДж/kJ/кдЖ»;
 * - кДж без ккал (ккал = кДж / 4,184) и расчёт ккал по БЖУ (4/9/4, волокна 2), если энергии на этикетке нет.
 */
object LabelParser {

    data class Parsed(
        val kcal: Double?, val kj: Double?, val protein: Double?, val fat: Double?, val carbs: Double?, val fiber: Double?,
        val per100: Boolean, val portionG: Double?, val kcalFromKj: Boolean, val warnings: List<String>,
        /** true — калорийность не найдена на этикетке и рассчитана по БЖУ. */
        val kcalFromMacros: Boolean = false,
    ) {
        val found get() = listOfNotNull(kcal, protein, fat, carbs).size
        /** Сколько значений прочитано прямо с этикетки (без расчётов). */
        val foundDirect get() = listOfNotNull(if (kcalFromMacros) null else kcal, protein, fat, carbs).size
    }

    // Латиница → кириллица по внешнему сходству (для поиска ключевых слов независимо от раскладки OCR)
    private val SKELETON = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к', 'm' to 'м', 'o' to 'о',
        'p' to 'р', 't' to 'т', 'x' to 'х', 'y' to 'у', 'ё' to 'е', 'u' to 'и', 'r' to 'г', '6' to 'б'
    )

    /** Посимвольно (длина строки сохраняется, индексы совпадают с исходным текстом). */
    fun skeleton(s: String, digits: Boolean = true): String {
        val sb = StringBuilder(s.length)
        for (ch in s) { val l = Character.toLowerCase(ch); sb.append(if (!digits && l.isDigit()) l else SKELETON[l] ?: l) }
        return sb.toString()
    }

    private val NUM_CONFUSION = mapOf('o' to '0', 'O' to '0', 'о' to '0', 'О' to '0', 'З' to '3', 'з' to '3', 'б' to '6',
        'l' to '1', 'I' to '1', '|' to '1', 'і' to '1')

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

    /** Исправление типичных ошибок OCR внутри «числовых» токенов; длина строки сохраняется. */
    fun fixDigits(s: String): String {
        val chars = s.toCharArray()
        val tokenRe = Regex("[0-9oOоОЗзбlI|і]+(?:[.,][0-9oOоОЗзбlI|і]+)?")
        for (m in tokenRe.findAll(s)) {
            val t = m.value
            val digits = t.count { it.isDigit() }
            val letters = t.count { !it.isDigit() && it != '.' && it != ',' }
            val before = if (m.range.first > 0) s[m.range.first - 1] else ' '
            val after = if (m.range.last + 1 < s.length) s[m.range.last + 1].lowercaseChar() else ' '
            val inWord = (before.isLetter() && before.lowercaseChar() !in "гgк") || (after.isLetter() && after !in "гgкkм")
            if (digits >= 1 && (digits >= letters || t.length <= 5) && !inWord) {
                for (i in m.range) NUM_CONFUSION[chars[i]]?.let { chars[i] = it }
            }
        }
        return String(chars)
    }

    /** Нормализация: тире/дефисы, пробелы, разорванные десятичные числа, ошибки OCR в числах. */
    fun normalize(raw: String): String {
        var t = raw.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\t', ' ').replace('\r', '\n')
            .replace(Regex("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212\u2E3A\u2E3B]"), "-")
            .replace('：', ':').replace('﹕', ':')
        t = t.replace(Regex("-{2,}"), "-").replace(Regex("[ ]{2,}"), " ")
        t = fixDigits(t)
        // «2, 6 г» / «2 ,6 г» → «2,6 г» (только перед единицей измерения)
        t = Regex("(\\d)(?: ([.,]) ?| ?([.,]) )(\\d{1,2})(?=\\s*(г|g|Г|ккал|kcal|кдж|кДж|kJ|%|мг|mg))")
            .replace(t) { m -> m.groupValues[1] + (m.groupValues[2].ifEmpty { m.groupValues[3] }) + m.groupValues[4] }
        // «1 0,0 г» → «10,0 г» (OCR разорвал число пробелом)
        t = t.replace(Regex("(?<![\\d.,])(\\d) (\\d{1,2}[.,]\\d{1,2})(?=\\s*(г|g|Г))"), "$1$2")
        return t
    }

    // ---------- ключевые слова ----------
    private class Kw(exact: List<String>, fuzzy: List<String> = emptyList(), val excludeTokens: List<String> = emptyList()) {
        val exact = Regex(exact.joinToString("|") { Regex.escape(skeleton(it)) })
        val fuzzy = fuzzy.map { skeleton(it) }
        val exclude = excludeTokens.map { skeleton(it) }
    }

    private val PROTEIN = Kw(listOf("белк", "белок", "protein", "протеин"), listOf("белки", "белок", "protein"))
    private val FAT = Kw(listOf("жир", "жнр", "fat", "lipid"), listOf("жиры"),
        excludeTokens = listOf("обезжир", "жирн", "жирово", "жировых"))
    private val CARBS = Kw(listOf("углевод", "углев", "carbohydrate", "carbs"), listOf("углеводы", "carbohydrate"))
    private val FIBER = Kw(listOf("пищевые волокна", "пищевых волокон", "пищ. волокна", "пищ.волокна", "клетчатк", "волокн", "fibre", "fiber"),
        listOf("клетчатка", "волокна"))
    private val ENERGY = Kw(listOf("энерг", "калорийн", "energy", "calories", "calorie", "ккал", "kcal"),
        listOf("энергетическая", "калорийность", "energy", "calories"))
    private val KEYS = listOf(PROTEIN, FAT, CARBS, FIBER, ENERGY)

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val tmp = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = tmp }
        }
        return d[b.length]
    }

    private val TOKEN = Regex("[\\p{L}]+")

    /** Все вхождения ключевого слова: диапазон ВСЕГО слова (для точных совпадений — от совпадения до конца слова). */
    private fun find(kw: Kw, sk: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        fun tokenEnd(i: Int): Int { var e = i; while (e + 1 < sk.length && sk[e + 1].isLetter()) e++; return e }
        fun tokenStart(i: Int): Int { var s = i; while (s - 1 >= 0 && sk[s - 1].isLetter()) s--; return s }
        for (m in kw.exact.findAll(sk)) out += tokenStart(m.range.first)..tokenEnd(m.range.last)
        if (kw.fuzzy.isNotEmpty()) for (t in TOKEN.findAll(sk)) {
            val w = t.value
            if (out.any { it.first == t.range.first }) continue
            for (stem in kw.fuzzy) {
                if (w.length < stem.length - 1) continue
                val allowed = if (stem.length >= 8) 2 else 1
                val d = (stem.length - 1..stem.length + 1).minOf { l -> lev(w.take(l), stem) }
                if (d <= allowed && w[0] == stem[0] || d == 0) { out += t.range; break }
            }
        }
        return out.filter { r -> val word = sk.substring(r.first, min(sk.length, r.last + 1)); kw.exclude.none { word.contains(it) } }
            .distinctBy { it.first }.sortedBy { it.first }
    }

    private val NUMBER = Regex("(\\d+(?:[.,]\\d+)?)")
    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    private data class Cand(val v: Double, val hasDecimal: Boolean, val digits: String)

    fun parse(raw: String): Parsed {
        val text = normalize(raw)
        val sk = skeleton(text)
        val skd = skeleton(text, digits = false)   // для шаблонов с числами
        val warnings = mutableListOf<String>()
        val allHits = KEYS.flatMap { find(it, sk) }.map { it.first }.sorted()

        // --- БЖУ: число после ключевого слова (до следующего ключевого слова / 45 символов; переход на следующую строку —
        // только если там сразу идёт число)
        val qualifiers = Regex(skeleton("насыщ|saturat|trans|транс|в т.ч|в т. ч|в том числе|including|of which|из них|полиненас|мононенас"))
        fun cand(kw: Kw, checkQualifier: Boolean): Double? {
            val cands = mutableListOf<Cand>()
            for (r in find(kw, sk)) {
                if (checkQualifier && qualifiers.containsMatchIn(sk.substring(maxOf(0, r.first - 14), r.first))) continue
                val from = r.last + 1
                var to = minOf(sk.length, from + 45)
                allHits.firstOrNull { it > r.last && it < to }?.let { to = it }
                var seg = text.substring(from, to)
                val nl = seg.indexOf('\n')
                if (nl >= 0) {
                    val head = seg.substring(0, nl)
                    if (NUMBER.containsMatchIn(head)) seg = head
                    else {
                        val tail = seg.substring(nl + 1)
                        val m = NUMBER.find(tail)
                        seg = if (m != null && Regex("^[\\s:.,;()\\-~=гgГр/*]*$").matches(tail.substring(0, m.range.first))) head + " " + tail.substringBefore('\n') else head
                    }
                }
                var nm = NUMBER.find(seg) ?: continue
                var rest = seg.substring(nm.range.last + 1)
                // «на 100 г 2,6 г» → пропускаем «100 г», если дальше есть число
                if (num(nm.value) == 100.0 && Regex("^\\s*(г|g|мл|ml)").containsMatchIn(rest)) { nm = NUMBER.find(rest) ?: continue; rest = rest.substring(nm.range.last + 1) }
                val v = num(nm.value) ?: continue
                cands += Cand(v, nm.value.contains('.') || nm.value.contains(','), nm.value.filter { it.isDigit() })
            }
            val first = cands.firstOrNull() ?: return null
            // «26» и «2,6» в разных местах/проходах → запятая, скорее всего, потерялась
            if (!first.hasDecimal) cands.firstOrNull { it.hasDecimal && it.digits == first.digits }?.let { return it.v }
            return first.v
        }
        var protein = cand(PROTEIN, false)
        var fat = cand(FAT, true)
        var carbs = cand(CARBS, false)
        var fiber = cand(FIBER, false)

        // --- «Б/Ж/У: 2,6/10/4,1»
        Regex("(?:^|[^а-яa-z])б\\s*/\\s*ж\\s*/\\s*у[^0-9\\n]{0,12}(?:(?:на|в|per)\\s*100\\s*(?:г|g|мл|ml)[^0-9\\n]{0,4})?(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)")
            .find(skd)?.let { m ->
                val g = m.groups
                if (protein == null) protein = num(text.substring(g[1]!!.range)); if (fat == null) fat = num(text.substring(g[2]!!.range))
                if (carbs == null) carbs = num(text.substring(g[3]!!.range))
            }

        // --- Табличная вёрстка: строка с ≥2 ключевыми словами без чисел + следующая строка с числами
        var kcal: Double? = null; var kj: Double? = null
        val lines = text.lines()
        for (i in 0 until lines.size - 1) {
            val skl = skeleton(lines[i])
            val order = listOf("p" to PROTEIN, "f" to FAT, "c" to CARBS, "e" to ENERGY)
                .mapNotNull { (k, kw) -> find(kw, skl).firstOrNull()?.let { k to it.first } }.sortedBy { it.second }
            if (order.count { it.first != "e" } >= 2 && NUMBER.findAll(lines[i]).none { (num(it.value) ?: 0.0) != 100.0 }) {
                val nums = NUMBER.findAll(lines[i + 1]).mapNotNull { num(it.value) }.toList()
                if (nums.size >= order.size) {
                    order.forEachIndexed { idx, (k, _) ->
                        when (k) { "p" -> protein = nums[idx]; "f" -> fat = nums[idx]; "c" -> carbs = nums[idx]; "e" -> kcal = nums[idx] }
                    }
                }
            }
        }

        // --- Энергия
        val kcalRe = Regex("^(?:к{1,2}[аa][лlп1iі]|кса[лl1i]|к ?кал|кал)(?![а-я])")
        val kjRe = Regex("^(к ?[дd] ?[жx>*]|кj)")
        fun isKcal(afterSk: String) = kcalRe.containsMatchIn(afterSk)
        fun isKj(afterSk: String) = kjRe.containsMatchIn(afterSk)
        // «кДж/ккал 494/118» и «ккал/кДж 118/494»
        Regex("(к ?[дd] ?[жx>]|кj)\\s*/\\s*(к{1,2}[аa][лlп]|кса[лl])[^0-9\\n]{0,20}(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)").find(skd)?.let { m ->
            kj = num(text.substring(m.groups[3]!!.range)); if (kcal == null) kcal = num(text.substring(m.groups[4]!!.range))
        }
        Regex("(к{1,2}[аa][лlп]|кса[лl])\\s*/\\s*(к ?[дd] ?[жx>]|кj)[^0-9\\n]{0,20}(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)").find(skd)?.let { m ->
            if (kcal == null) kcal = num(text.substring(m.groups[3]!!.range)); if (kj == null) kj = num(text.substring(m.groups[4]!!.range))
        }
        // «118 ккал», «494 кДж»
        for (m in NUMBER.findAll(text)) {
            val v = num(m.value) ?: continue
            val afterSk = sk.substring(m.range.last + 1, minOf(sk.length, m.range.last + 9)).trimStart()
            if (kcal == null && isKcal(afterSk)) kcal = v
            if (kj == null && isKj(afterSk)) kj = v
        }
        // «ккал 250» / «Энергетическая ценность, ккал: 250» / «Calories 230» (в пределах строки)
        if (kcal == null) {
            for (u in Regex("(?<![а-я])(?:к{1,2}[аa][лl]|кса[лl])(?![а-я])|" + skeleton("calories") + "|" + skeleton("calorie")).findAll(sk)) {
                val lineEnd = text.indexOf('\n', u.range.last + 1).let { if (it < 0) text.length else it }
                val seg = text.substring(u.range.last + 1, minOf(lineEnd, u.range.last + 16))
                val n = NUMBER.find(seg) ?: continue
                if (isKj(sk.substring(u.range.last + 1 + n.range.last + 1).trimStart().take(4))) continue
                kcal = num(n.value); break
            }
        }
        if (kcal == null) { // «энергетическая ценность 250» без единиц
            for (r in find(ENERGY, sk)) {
                val seg = text.substring(r.last + 1, minOf(text.length, r.last + 40))
                val first = NUMBER.findAll(seg).firstOrNull { num(it.value) != 100.0 } ?: continue
                val after = sk.substring(r.last + 1 + first.range.last + 1).trimStart()
                val v = num(first.value)
                if (v != null && !isKj(after) && v in 5.0..950.0) { kcal = v; break }
            }
        }
        var fromKj = false
        if (kcal == null && kj != null) { kcal = kj!! / 4.184; fromKj = true }

        // --- На 100 г или на порцию
        val per100 = Regex("(на|в|per|/)\\s*100\\s*(г|g|мл|ml|гр)", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            Regex("100\\s*(г|g|мл|ml)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        var portion: Double? = null
        Regex("(порци[яиюей]|serving size|serving|упаковк)[^\\n]{0,40}?\\(?(\\d+(?:[.,]\\d+)?)\\s*(г|g|мл|ml)\\b", RegexOption.IGNORE_CASE)
            .find(text)?.let { portion = num(it.groupValues[2]) }

        // --- Калорийность по БЖУ, если на этикетке её нет
        var fromMacros = false
        if (kcal == null && protein != null && fat != null && carbs != null) {
            kcal = protein!! * 4 + fat!! * 9 + carbs!! * 4 + (fiber ?: 0.0) * 2
            fromMacros = true
        }

        // --- Проверки правдоподобия
        val sumMacro = (protein ?: 0.0) + (fat ?: 0.0) + (carbs ?: 0.0) + (fiber ?: 0.0)
        if (per100 && sumMacro > 100.5) warnings += "Сумма БЖУ больше 100 г на 100 г — вероятно, потеряна запятая. Проверьте значения."
        if (!fromMacros && kcal != null && protein != null && fat != null && carbs != null) {
            val est = protein!! * 4 + fat!! * 9 + carbs!! * 4
            if (est > 20 && abs(est - kcal!!) / est > 0.25) warnings += "Калорийность отличается от расчёта по БЖУ (≈${est.toInt()} ккал) более чем на 25%."
        }
        if (!fromKj && kcal != null && kj != null && kj!! > 0 && abs(kcal!! * 4.184 - kj!!) / kj!! > 0.15)
            warnings += "ккал и кДж на этикетке не согласуются (${kcal!!.toInt()} ккал ≠ ${kj!!.toInt()} кДж ÷ 4,184) — проверьте."
        if (fromKj) warnings += "Калорийность пересчитана из кДж (÷ 4,184)."
        if (fromMacros) warnings += "Калорийность на этикетке не найдена — рассчитана по БЖУ (белки и углеводы 4, жиры 9, волокна 2 ккал/г)."
        if (!per100 && portion != null) warnings += "Похоже, значения указаны на порцию $portion г — будут пересчитаны на 100 г."
        return Parsed(kcal, kj, protein, fat, carbs, fiber, per100 || portion == null, if (per100) null else portion, fromKj, warnings, fromMacros)
    }

    /** Оценка качества результата OCR для выбора лучшего прохода. */
    fun score(text: String): Double {
        val p = parse(text)
        val sk = skeleton(normalize(text))
        val kwHits = KEYS.sumOf { find(it, sk).size.coerceAtMost(2) }
        val decimals = listOfNotNull(p.protein, p.fat, p.carbs).count { it % 1.0 != 0.0 }
        return p.foundDirect * 10.0 + kwHits * 1.5 + decimals * 0.5 - p.warnings.count { "запятая" in it || "25%" in it } * 4.0 +
            text.count { it.isLetter() && it in 'а'..'я' || it in 'А'..'Я' } / 400.0
    }
}
