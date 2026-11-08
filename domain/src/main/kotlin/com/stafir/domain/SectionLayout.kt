package com.stafir.domain

/**
 * Disposizione della griglia divisa per mesi: un'intestazione prima dei file di ogni mese.
 * Traduce tra la posizione nella griglia (intestazioni comprese) e la posizione del file
 * nell'elenco dei risultati (quella usata dal visualizzatore).
 *
 * @param counts quanti file ha ogni sezione, nell'ordine della griglia
 * @param headers false = nessuna intestazione (griglia semplice)
 */
class SectionLayout(counts: List<Int>, val headers: Boolean = true) {
    private val counts = counts.map { it.coerceAtLeast(0) }.toIntArray()
    private val starts = IntArray(this.counts.size)
    val mediaCount: Int

    init {
        var o = 0
        for (i in this.counts.indices) { starts[i] = o; o += this.counts[i] }
        mediaCount = o
    }

    val sectionCount: Int get() = counts.size
    val gridCount: Int get() = mediaCount + if (headers) counts.size else 0

    fun start(section: Int): Int = starts[section]
    fun count(section: Int): Int = counts[section]

    /** Posizione nella griglia dell'intestazione di una sezione. */
    fun headerIndex(section: Int): Int = starts[section] + section

    /** Sezione che contiene il file n° [media]. */
    fun sectionOfMedia(media: Int): Int {
        if (counts.isEmpty()) return 0
        var lo = 0
        var hi = counts.size - 1
        while (lo < hi) { // ultima sezione con start <= media
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= media) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Posizione nella griglia del file n° [media]. */
    fun gridOfMedia(media: Int): Int = if (!headers) media else media + sectionOfMedia(media) + 1

    /** Sezione mostrata alla posizione [grid] della griglia (intestazione o file). */
    fun sectionOfGrid(grid: Int): Int {
        if (!headers) return sectionOfMedia(grid)
        if (counts.isEmpty()) return 0
        var lo = 0
        var hi = counts.size - 1
        while (lo < hi) { // ultima sezione con intestazione <= grid
            val mid = (lo + hi + 1) / 2
            if (headerIndex(mid) <= grid) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** File alla posizione [grid] (per un'intestazione: il primo file del suo mese). */
    fun mediaOfGrid(grid: Int): Int {
        if (mediaCount == 0) return 0
        if (!headers) return grid.coerceIn(0, mediaCount - 1)
        val s = sectionOfGrid(grid)
        return (grid - s - 1).coerceAtLeast(starts[s]).coerceIn(0, mediaCount - 1)
    }

    companion object {
        /** Griglia senza intestazioni. */
        fun flat(total: Int) = SectionLayout(listOf(total), headers = false)
    }
}
