package com.example.healthplan.food

import java.io.InputStream

/** Встроенная база (assets/foods.csv, USDA FoodData Central SR Legacy) + свои продукты пользователя. */
class FoodDb(val builtIn: List<Food>) {
    var custom: List<Food> = emptyList()
    val all: List<Food> get() = custom + builtIn
    fun byId(id: String): Food? = all.firstOrNull { it.id == id }

    fun search(query: String, restrictions: Restrictions? = null): List<Food> {
        val q = normalize(query)
        val words = q.split(' ').filter { it.isNotBlank() }
        val base = all.filter { restrictions == null || restrictions.allows(it) }
        if (words.isEmpty()) return base
        return base.filter { f -> val n = normalize(f.name + " " + f.category); words.all { n.contains(it) } }
            .sortedWith(compareBy<Food>({ !normalize(it.name).startsWith(words[0]) }, { !it.isCustom }, { it.name.length }))
    }

    companion object {
        fun normalize(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^a-zа-я0-9% ]"), " ").replace(Regex("\\s+"), " ").trim()

        fun parseCsv(input: InputStream): List<Food> {
            val out = ArrayList<Food>(400)
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).forEach { line ->
                    val c = splitCsv(line)
                    if (c.size < 10) return@forEach
                    val rawId = c[0]
                    val id = if (rawId.startsWith("R")) "r:" + rawId.substring(1) else "u:$rawId"
                    val src = if (rawId.startsWith("R")) "Расчёт по примерной рецептуре из продуктов USDA SR Legacy: " + c[9].removePrefix("Recipe: ")
                    else "USDA FoodData Central, SR Legacy, FDC ID $rawId: «${c[9]}»"
                    out += Food(id, c[1], c[2], N(c[3].toDouble(), c[4].toDouble(), c[5].toDouble(), c[6].toDouble(), c[7].toDouble()), c[8], src)
                }
            }
            return out
        }

        /** CSV с разделителем ';' и кавычками (python csv). */
        fun splitCsv(line: String): List<String> {
            val res = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                    ch == '"' -> q = !q
                    ch == ';' && !q -> { res += sb.toString(); sb.clear() }
                    else -> sb.append(ch)
                }
                i++
            }
            res += sb.toString()
            return res
        }
    }
}
