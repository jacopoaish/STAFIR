package com.stafir.ai

import android.graphics.Bitmap

/** Etichetta prodotta da un riconoscitore (nome in inglese minuscolo, confidenza 0..1). */
data class RecognizedLabel(val name: String, val confidence: Float)

/**
 * Un riconoscitore di immagini. Ogni riconoscitore è un modulo separato:
 * in futuro si aggiungono volti, testo (OCR), CLIP, ecc. implementando questa interfaccia,
 * e il "banco di prova" potrà confrontarli sulle stesse foto.
 */
interface ImageRecognizer : AutoCloseable {
    val id: String
    val displayName: String
    /** Su quale hardware gira (mostrato nelle impostazioni avanzate). */
    val accelerator: String

    suspend fun recognize(bitmap: Bitmap): List<RecognizedLabel>
}

/** Unisce le etichette di più fotogrammi tenendo la confidenza più alta. */
fun mergeLabels(all: List<List<RecognizedLabel>>): List<RecognizedLabel> =
    all.flatten()
        .groupBy { it.name }
        .map { (name, list) -> RecognizedLabel(name, list.maxOf { it.confidence }) }
        .sortedByDescending { it.confidence }
