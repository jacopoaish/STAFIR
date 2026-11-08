package com.stafir.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ricava la data di scatto dal nome del file, quando i metadati mancano
 * (foto di WhatsApp, copie da vecchi telefoni, backup ripristinati...).
 * Esempi: IMG_20240502_173000.jpg, IMG-20240110-WA0003.jpg,
 * Screenshot_2024-05-02-17-30-00-123_com.app.jpg, photo_2024-01-10_12-30-45.jpg, PXL_20240101_123456789.jpg
 */
object FileNameDates {
    private val dateTimeCompact = Regex("""(?:^|\D)(\d{4})(\d{2})(\d{2})[_\-. ]?(\d{2})(\d{2})(\d{2})\d{0,3}(?:\D|$)""")
    private val dateTimeDashed = Regex("""(?:^|\D)(\d{4})-(\d{2})-(\d{2})[_\- ](\d{2})[\-.:](\d{2})[\-.:](\d{2})(?:\D|$)""")
    private val dateCompact = Regex("""(?:^|\D)(\d{4})(\d{2})(\d{2})(?:\D|$)""")
    private val dateDashed = Regex("""(?:^|\D)(\d{4})-(\d{2})-(\d{2})(?:\D|$)""")
    private val epochMillis = Regex("""^(\d{13})(?:\D|$)""")

    fun parse(name: String?, zone: ZoneId): Long? {
        if (name.isNullOrBlank()) return null
        for (re in listOf(dateTimeCompact, dateTimeDashed)) {
            re.find(name)?.let { m ->
                val g = m.groupValues.drop(1).map { it.toInt() }
                toMillis(g[0], g[1], g[2], g[3], g[4], g[5], zone)?.let { return it }
            }
        }
        for (re in listOf(dateCompact, dateDashed)) {
            re.find(name)?.let { m ->
                val g = m.groupValues.drop(1).map { it.toInt() }
                // Solo la data: mezzogiorno, così il giorno resta giusto in ogni fuso orario vicino.
                toMillis(g[0], g[1], g[2], 12, 0, 0, zone)?.let { return it }
            }
        }
        epochMillis.find(name)?.let { m ->
            val ms = m.groupValues[1].toLong()
            val year = java.time.Instant.ofEpochMilli(ms).atZone(zone).year
            if (year in MIN_YEAR..MAX_YEAR) return ms
        }
        return null
    }

    private fun toMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, zone: ZoneId): Long? {
        if (y !in MIN_YEAR..MAX_YEAR || mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || s > 59) return null
        return try {
            LocalDateTime.of(LocalDate.of(y, mo, d), java.time.LocalTime.of(h, mi, s)).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }

    private const val MIN_YEAR = 1990
    private const val MAX_YEAR = 2100
}
