package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityMetricsTest {
    @Test fun computesPrecisionRecallAndExactMatches() {
        val samples = listOf(
            QualitySample(1, setOf("sea", "sunset")),
            QualitySample(2, setOf("food")),
            QualitySample(3, setOf("dog")),
        )
        val preds = listOf(
            QualityPrediction(1, setOf("sea"), 20),          // manca sunset
            QualityPrediction(2, setOf("food"), 30),         // esatto
            QualityPrediction(3, setOf("dog", "people"), 40), // people in più
        )
        val r = QualityMetrics.evaluate(samples, preds)
        assertEquals(3, r.samples)
        assertEquals(1, r.exactMatches)
        assertEquals(3, r.overall.tp)
        assertEquals(1, r.overall.fp)
        assertEquals(1, r.overall.fn)
        assertEquals(0.75, r.overall.precision, 1e-9)
        assertEquals(0.75, r.overall.recall, 1e-9)
        assertEquals(30.0, r.avgMillis, 1e-9)
        val sunset = r.perConcept.first { it.conceptId == "sunset" }
        assertEquals(0.0, sunset.recall, 1e-9)
        assertEquals(setOf("sunset"), r.outcomes.first { it.mediaId == 1L }.missed)
        assertEquals(setOf("people"), r.outcomes.first { it.mediaId == 3L }.extra)
    }

    @Test fun samplesWithoutPredictionAreIgnored() {
        val r = QualityMetrics.evaluate(listOf(QualitySample(1, setOf("sea"))), emptyList())
        assertEquals(0, r.samples)
        assertEquals(1.0, r.overall.precision, 1e-9)
    }
}
