package com.stafir.app.indexing

import android.graphics.Bitmap
import com.stafir.ai.FrameLoader
import com.stafir.ai.MlKitSceneRecognizer
import com.stafir.ai.RecognizedLabel
import com.stafir.ai.mergeLabels
import com.stafir.app.AppContainer
import com.stafir.app.AppSettings
import com.stafir.core.db.MediaEntity
import com.stafir.core.db.MediaLabelEntity
import com.stafir.core.media.LocationReader
import com.stafir.core.media.contentUri
import com.stafir.domain.Source
import com.stafir.domain.Vocabulary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Analizza un singolo file: scene/oggetti, luogo, provenienza, testo ricercabile.
 * Ogni passo è un modulo indipendente; i prossimi (volti, OCR, duplicati...) si aggiungono qui.
 */
class AnalysisPipeline(private val container: AppContainer) : AutoCloseable {

    private val frames = FrameLoader(container.context)
    private val locations = LocationReader(container.context)
    // Salviamo le etichette da 0.4 in su; la soglia di ricerca è regolabile in "Avanzate".
    private val scenes by lazy { MlKitSceneRecognizer(minConfidence = 0.4f) }
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN)

    val sceneModelName: String get() = scenes.displayName

    /** Riconosce scene e oggetti in una foto (miniatura) o in alcuni fotogrammi chiave di un video. */
    suspend fun recognize(m: MediaEntity, videos: Boolean): List<RecognizedLabel> {
        val uri = m.contentUri()
        val bitmaps: List<Bitmap> = if (m.isVideo) {
            if (videos) frames.videoKeyframes(uri, m.durationMs).ifEmpty { listOf(frames.thumbnail(uri)) }
            else emptyList()
        } else {
            listOf(frames.thumbnail(uri, 512))
        }
        return try {
            mergeLabels(bitmaps.map { scenes.recognize(it) })
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    suspend fun analyze(m: MediaEntity, s: AppSettings.Values) {
        val uri = m.contentUri()

        // 1) Scene e oggetti
        val labels: List<RecognizedLabel> = if (s.recognizeScenes) recognize(m, s.recognizeVideos) else emptyList()

        // 2) Luogo (GPS -> città/regione/paese, senza internet)
        var updated = m
        if (s.recognizePlaces) {
            val ll = locations.read(uri, m.isVideo)
            if (ll != null) {
                val place = container.geocoder.lookup(ll.first, ll.second)
                updated = m.copy(
                    latitude = ll.first, longitude = ll.second,
                    city = place?.city, region = place?.region, country = place?.country,
                )
            }
        }

        // 3) Testo ricercabile
        val content = buildSearchText(updated, labels)
        container.database.mediaDao().saveAnalysis(
            media = updated,
            labels = labels.map { MediaLabelEntity(m.id, it.name, it.confidence, scenes.id) },
            ftsContent = content,
            version = VERSION,
        )
    }

    private fun buildSearchText(m: MediaEntity, labels: List<RecognizedLabel>): String {
        val words = LinkedHashSet<String>()
        labels.forEach {
            words += Vocabulary.italianLabel(it.name)
            words += it.name
        }
        Vocabulary.conceptsForLabels(labels.filter { it.confidence >= 0.6f }.map { it.name })
            .forEach { words += it.italianName }
        listOfNotNull(m.city, m.region, m.country).forEach { words += it }
        runCatching { words += Source.valueOf(m.source).italian }
        words += m.displayName.substringBeforeLast('.').replace(Regex("[_\\-.]+"), " ")
        m.bucketName?.let { words += it }
        words += monthFormat.format(Date(m.dateTaken))
        words += if (m.isVideo) "video" else "foto"
        return words.joinToString(" ")
    }

    override fun close() {
        runCatching { scenes.close() }
    }

    companion object {
        const val VERSION = 1
    }
}
