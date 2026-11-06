package com.stafir.domain

/** Una foto del banco qualità con le categorie presenti secondo l'utente (elenco completo). */
data class QualitySample(val mediaId: Long, val expected: Set<String>)

/** Cosa ha riconosciuto un modello su una foto del banco, e in quanto tempo. */
data class QualityPrediction(val mediaId: Long, val predicted: Set<String>, val millis: Long)

data class ConceptScore(val conceptId: String, val tp: Int, val fp: Int, val fn: Int) {
    /** Delle volte che il modello ha detto "c'è", quante aveva ragione. */
    val precision: Double get() = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
    /** Delle volte che "c'era davvero", quante il modello l'ha trovato. */
    val recall: Double get() = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)
    val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
}

data class SampleOutcome(val mediaId: Long, val missed: Set<String>, val extra: Set<String>)

data class QualityReport(
    val samples: Int,
    val overall: ConceptScore,
    val perConcept: List<ConceptScore>,
    val exactMatches: Int,
    val avgMillis: Double,
    val outcomes: List<SampleOutcome>,
)

/** Misura la qualità del riconoscimento confrontando le previsioni con le etichette fatte a mano. */
object QualityMetrics {

    fun evaluate(samples: List<QualitySample>, predictions: List<QualityPrediction>): QualityReport {
        val byId = predictions.associateBy { it.mediaId }
        val counts = HashMap<String, IntArray>() // tp, fp, fn
        var exact = 0
        val outcomes = mutableListOf<SampleOutcome>()
        var evaluated = 0
        for (s in samples) {
            val p = byId[s.mediaId] ?: continue
            evaluated++
            val tp = s.expected intersect p.predicted
            val fn = s.expected - p.predicted
            val fp = p.predicted - s.expected
            tp.forEach { counts.getOrPut(it) { IntArray(3) }[0]++ }
            fp.forEach { counts.getOrPut(it) { IntArray(3) }[1]++ }
            fn.forEach { counts.getOrPut(it) { IntArray(3) }[2]++ }
            if (fn.isEmpty() && fp.isEmpty()) exact++
            outcomes += SampleOutcome(s.mediaId, fn, fp)
        }
        val per = counts.map { (id, c) -> ConceptScore(id, c[0], c[1], c[2]) }
            .sortedWith(compareBy<ConceptScore> { it.f1 }.thenBy { it.conceptId })
        val overall = ConceptScore("tutte", per.sumOf { it.tp }, per.sumOf { it.fp }, per.sumOf { it.fn })
        val timed = samples.mapNotNull { byId[it.mediaId]?.millis }
        return QualityReport(
            samples = evaluated,
            overall = overall,
            perConcept = per,
            exactMatches = exact,
            avgMillis = if (timed.isEmpty()) 0.0 else timed.average(),
            outcomes = outcomes,
        )
    }
}
