package com.stafir.app.quality

import android.os.SystemClock
import com.stafir.app.AppContainer
import com.stafir.app.indexing.AnalysisPipeline
import com.stafir.domain.QualityMetrics
import com.stafir.domain.QualityPrediction
import com.stafir.domain.QualityReport
import com.stafir.domain.QualitySample
import com.stafir.domain.Vocabulary

/**
 * Esegue il riconoscimento sulle foto del banco qualità e confronta il risultato
 * con le etichette fatte a mano. Misura anche il tempo per foto.
 */
class QualityBench(private val container: AppContainer) {

    suspend fun run(onProgress: (done: Int, total: Int) -> Unit): QualityReport {
        val store = container.quality
        val dao = container.database.mediaDao()
        val samples = store.samples.value
        val threshold = container.settings.current.minConfidence
        val pipeline = AnalysisPipeline(container)
        val predictions = mutableListOf<QualityPrediction>()
        val evaluated = mutableListOf<QualitySample>()
        val modelName = pipeline.sceneModelName
        try {
            var warmedUp = false
            samples.forEachIndexed { i, s ->
                val m = dao.byId(s.mediaId)
                if (m != null) {
                    try {
                        // Il primo riconoscimento carica il modello: non lo contiamo nei tempi.
                        if (!warmedUp) {
                            pipeline.recognize(m, videos = true); warmedUp = true
                        }
                        val t0 = SystemClock.elapsedRealtime()
                        val labels = pipeline.recognize(m, videos = true)
                        val ms = SystemClock.elapsedRealtime() - t0
                        val predicted = Vocabulary.conceptsForLabels(
                            labels.filter { it.confidence >= threshold }.map { it.name },
                        ).map { it.id }.toSet()
                        predictions += QualityPrediction(m.id, predicted, ms)
                        evaluated += s.copy(expected = Vocabulary.withImplied(s.expected))
                    } catch (_: Exception) {
                        // File non più leggibile: viene escluso dalla misura.
                    }
                }
                onProgress(i + 1, samples.size)
            }
        } finally {
            pipeline.close()
        }
        val report = QualityMetrics.evaluate(evaluated, predictions)
        store.addRun(
            QualityRun(
                timestamp = System.currentTimeMillis(),
                model = modelName,
                threshold = threshold,
                samples = report.samples,
                precision = report.overall.precision,
                recall = report.overall.recall,
                f1 = report.overall.f1,
                avgMillis = report.avgMillis,
            ),
        )
        return report
    }
}
