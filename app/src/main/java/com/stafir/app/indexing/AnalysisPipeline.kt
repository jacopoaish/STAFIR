package com.stafir.app.indexing

import android.graphics.Bitmap
import com.stafir.ai.FaceRecognizer
import com.stafir.ai.FrameLoader
import com.stafir.ai.MlKitSceneRecognizer
import com.stafir.ai.RecognizedLabel
import com.stafir.ai.mergeLabels
import com.stafir.app.AppContainer
import com.stafir.app.AppSettings
import com.stafir.core.db.MediaEntity
import com.stafir.core.db.MediaLabelEntity
import com.stafir.core.media.MediaMetadataReader
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
    private val metadata = MediaMetadataReader(container.context)
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

    private val faces by lazy { FaceRecognizer(container.context) }

    /** true se il riconoscimento volti è attivo e il modello è presente in questa build. */
    fun facesEnabled(s: AppSettings.Values) = s.recognizeFaces && faces.available

    suspend fun analyze(m: MediaEntity, s: AppSettings.Values) {
        val uri = m.contentUri()
        val full = m.analysisState != 1
        val wantScenes = full && s.recognizeScenes
        val wantFaces = facesEnabled(s) && m.facesVersion < FACES_VERSION
        if (m.isVideo && !s.recognizeVideos && !full) return

        // 1) Immagini da guardare: la foto (più grande se cerchiamo volti) o alcuni fotogrammi del video
        val images: List<Pair<Long, Bitmap>> = when {
            !wantScenes && !wantFaces -> emptyList()
            m.isVideo -> if (s.recognizeVideos) {
                frames.videoKeyframesTimed(uri, m.durationMs, 3, if (wantFaces) 1024 else 512)
                    .ifEmpty { listOf(-1L to frames.thumbnail(uri)) }
            } else emptyList()
            else -> listOf(-1L to frames.thumbnail(uri, if (wantFaces) 1280 else 512))
        }
        var labels: List<RecognizedLabel> = emptyList()
        val found = mutableListOf<FoundFace>()
        try {
            if (wantScenes) labels = mergeLabels(images.map { scenes.recognize(it.second) })
            if (wantFaces) images.forEach { (ms, bmp) -> faces.detect(bmp).forEach { found += FoundFace(ms, it) } }
        } finally {
            images.forEach { it.second.recycle() }
        }

        if (full) {
            // 2) Metadati originali: luogo (GPS -> città/regione/paese, senza internet) e data di scatto
            var updated = m
            val needsDate = m.dateEstimated && !m.isVideo
            val meta = if (s.recognizePlaces || needsDate) metadata.read(uri, m.isVideo) else null
            if (needsDate && meta?.dateTakenMs != null) {
                updated = updated.copy(dateTaken = meta.dateTakenMs!!, dateEstimated = false)
            }
            if (s.recognizePlaces) {
                val ll = meta?.latLon
                if (ll != null) {
                    val place = container.geocoder.lookup(ll.first, ll.second)
                    updated = updated.copy(
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
        // 4) Volti: ogni volto va alla persona giusta (o diventa una persona nuova)
        if (wantFaces) {
            container.faceIndex.addFaces(m.id, found)
            container.database.faceDao().setFacesVersion(m.id, FACES_VERSION)
        }
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
        runCatching { faces.close() }
    }

    companion object {
        const val VERSION = 1
        /** Aumentare quando cambia il modello dei volti: i file verranno rianalizzati solo per i volti. */
        const val FACES_VERSION = 1
    }
}
