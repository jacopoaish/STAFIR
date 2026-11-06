package com.stafir.domain

import java.text.Normalizer

object TextNormalizer {
    private val diacritics = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val nonWord = "[^a-z0-9' ]+".toRegex()
    private val spaces = "\\s+".toRegex()

    /** minuscolo, senza accenti, solo lettere/numeri/spazi. */
    fun normalize(text: String): String {
        val lower = text.lowercase()
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(diacritics, "")
        return noAccents.replace(nonWord, " ").replace(spaces, " ").trim()
    }

    /** Divide in parole, separando anche gli articoli apostrofati ("all'alba" -> "all", "alba"). */
    fun tokens(text: String): List<String> =
        normalize(text).replace('\'', ' ').split(' ').filter { it.isNotBlank() }
}
