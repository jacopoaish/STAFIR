package com.stafir.app.ui.common

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val it = Locale.ITALIAN
    private val number = NumberFormat.getIntegerInstance(it)

    fun count(n: Int): String = number.format(n)

    /** "1 risultato", "3 risultati". */
    fun plural(n: Int, one: String, many: String): String = "${count(n)} ${if (n == 1) one else many}"
    fun count(n: Long): String = number.format(n)

    fun bytes(b: Long): String {
        val gb = b / 1_000_000_000.0
        val mb = b / 1_000_000.0
        return when {
            gb >= 1 -> String.format(it, "%.1f GB", gb)
            mb >= 1 -> String.format(it, "%.1f MB", mb)
            else -> String.format(it, "%d KB", b / 1000)
        }
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(it, "%d:%02d:%02d", h, m, sec) else String.format(it, "%d:%02d", m, sec)
    }

    fun dateLong(ms: Long): String = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", it).format(Date(ms))
        .replaceFirstChar { c -> c.uppercase() }

    /** "2024-08" -> "Agosto 2024". */
    fun monthKey(key: String): String = runCatching {
        java.time.YearMonth.parse(key).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", it))
            .replaceFirstChar { c -> c.uppercase() }
    }.getOrDefault(key)

    fun monthYear(ms: Long): String = SimpleDateFormat("MMMM yyyy", it).format(Date(ms))
        .replaceFirstChar { c -> c.uppercase() }

    fun eta(seconds: Long): String = when {
        seconds < 0 -> "calcolo in corso…"
        seconds < 60 -> "meno di un minuto"
        seconds < 3600 -> "circa ${seconds / 60} min"
        seconds < 86400 -> "circa ${seconds / 3600} h ${(seconds % 3600) / 60} min"
        else -> "circa ${seconds / 86400} giorni"
    }
}
