package com.stafir.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Riconosce espressioni di data in italiano a partire dalla parola [start]. */
internal object DateExpressions {

    val MONTHS = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    // Niente abbreviazioni ("mar", "set"...): si confondono con parole normali o con
    // parole a metà mentre si scrive (es. "mar" → "mare").
    private fun month(t: String): Int? = MONTHS.indexOf(t).takeIf { it >= 0 }?.plus(1)

    private fun year(t: String?): Int? = t?.toIntOrNull()?.takeIf { it in 1900..2100 && t.length == 4 }

    /** Ritorna l'intervallo trovato e quante parole ha consumato, oppure null. */
    fun match(tokens: List<String>, start: Int, today: LocalDate, zone: ZoneId): Pair<DateRange, Int>? {
        fun at(k: Int) = tokens.getOrNull(start + k)
        fun range(from: LocalDate, toExclusive: LocalDate, label: String) = DateRange(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            toExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            label,
        )
        val t0 = at(0) ?: return null
        val t1 = at(1)
        val t2 = at(2)

        when (t0) {
            "oggi" -> return range(today, today.plusDays(1), "oggi") to 1
            "ieri" -> return range(today.minusDays(1), today, "ieri") to 1
            "stamattina", "stasera", "stanotte" -> return range(today, today.plusDays(1), "oggi") to 1
        }

        // "questa settimana", "questo mese", "quest anno"
        if (t0 == "questa" && t1 == "settimana") {
            val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            return range(monday, today.plusDays(1), "questa settimana") to 2
        }
        if (t0 == "questo" && t1 == "mese") return range(today.withDayOfMonth(1), today.plusDays(1), "questo mese") to 2
        if ((t0 == "quest" || t0 == "questo") && t1 == "anno") return range(today.withDayOfYear(1), today.plusDays(1), "quest'anno") to 2
        if (t0 == "quest" && t1 == "estate") return season("estate", today.year, ::range)?.let { it to 2 }

        // "settimana scorsa", "scorsa settimana", "mese scorso", "anno scorso"
        val pairScorso = setOf(t0, t1)
        if ("scorsa" in pairScorso && "settimana" in pairScorso) {
            val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            return range(monday.minusWeeks(1), monday, "settimana scorsa") to 2
        }
        if ("scorso" in pairScorso && "mese" in pairScorso) {
            val first = today.withDayOfMonth(1)
            return range(first.minusMonths(1), first, "mese scorso") to 2
        }
        if ("scorso" in pairScorso && "anno" in pairScorso) {
            val first = today.withDayOfYear(1)
            return range(first.minusYears(1), first, "anno scorso") to 2
        }
        if (t0 == "scorsa" && t1 in SEASONS) return season(t1!!, today.year - 1, ::range)?.let { it to 2 }

        // "ultimi 30 giorni", "ultime 2 settimane", "ultimo mese", "ultima settimana", "ultimo anno"
        if (t0 in setOf("ultimi", "ultime", "ultimo", "ultima", "negli")) {
            var k = 1
            if (t0 == "negli") { if (t1 != "ultimi") return null; k = 2 }
            val n = at(k)?.toIntOrNull()
            val unit = if (n != null) at(k + 1) else at(k)
            val count = n ?: 1
            val used = k + (if (n != null) 2 else 1)
            val from = when (unit) {
                "giorni", "giorno" -> today.minusDays(count.toLong() - 1)
                "settimane", "settimana" -> today.minusWeeks(count.toLong())
                "mesi", "mese" -> today.minusMonths(count.toLong())
                "anni", "anno" -> today.minusYears(count.toLong())
                else -> return null
            }
            return range(from, today.plusDays(1), "ultimi $count $unit") to used
        }

        // "dal 2020 al 2022"
        if ((t0 == "dal" || t0 == "da") && year(t1) != null && (t2 == "al" || t2 == "a") && year(at(3)) != null) {
            val y1 = year(t1)!!; val y2 = year(at(3))!!
            return range(LocalDate.of(minOf(y1, y2), 1, 1), LocalDate.of(maxOf(y1, y2) + 1, 1, 1), "$y1–$y2") to 4
        }

        // Stagioni: "estate 2023", "estate"
        if (t0 in SEASONS) {
            val y = year(t1)
            if (y != null) return season(t0, y, ::range)?.let { it to 2 }
            // senza anno: l'ultima stagione conclusa o in corso
            val candidate = season(t0, today.year, ::range)
            if (candidate != null) {
                val startOfThis = candidate.fromMs
                val todayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
                return (if (startOfThis <= todayMs) candidate else season(t0, today.year - 1, ::range)!!) to 1
            }
        }

        // Mese [anno]: "agosto 2023", "agosto"
        val m = month(t0)
        if (m != null) {
            val y = year(t1)
            if (y != null) {
                val from = LocalDate.of(y, m, 1)
                return range(from, from.plusMonths(1), "${MONTHS[m - 1]} $y") to 2
            }
            // senza anno: il mese più recente con quel nome
            val yy = if (m <= today.monthValue) today.year else today.year - 1
            val from = LocalDate.of(yy, m, 1)
            return range(from, from.plusMonths(1), "${MONTHS[m - 1]} $yy") to 1
        }

        // Anno: "2024", "nel 2024" (la preposizione è una parola vuota)
        val y = year(t0)
        if (y != null) return range(LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1), "$y") to 1

        return null
    }

    private val SEASONS = setOf("estate", "inverno", "primavera", "autunno")

    private fun season(
        name: String,
        year: Int,
        range: (LocalDate, LocalDate, String) -> DateRange,
    ): DateRange? = when (name) {
        "primavera" -> range(LocalDate.of(year, 3, 21), LocalDate.of(year, 6, 21), "primavera $year")
        "estate" -> range(LocalDate.of(year, 6, 1), LocalDate.of(year, 9, 21), "estate $year")
        "autunno" -> range(LocalDate.of(year, 9, 21), LocalDate.of(year, 12, 21), "autunno $year")
        "inverno" -> range(LocalDate.of(year - 1, 12, 1), LocalDate.of(year, 3, 21), "inverno $year")
        else -> null
    }
}
