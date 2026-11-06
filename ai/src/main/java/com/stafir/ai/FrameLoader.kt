package com.stafir.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size

/** Carica immagini ridotte (per l'analisi) da foto e fotogrammi chiave dei video. */
class FrameLoader(private val context: Context) {

    /** Miniatura di sistema (velocissima: Android la tiene già in cache). */
    fun thumbnail(uri: Uri, size: Int = 512): Bitmap =
        context.contentResolver.loadThumbnail(uri, Size(size, size), null)

    /**
     * Pochi fotogrammi chiave di un video (al 15%, 50% e 85% della durata),
     * così non serve elaborare ogni secondo.
     */
    fun videoKeyframes(uri: Uri, durationMs: Long, count: Int = 3, size: Int = 512): List<Bitmap> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val dur = if (durationMs > 0) durationMs else
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val positions = when {
                dur <= 0 -> listOf(0L)
                count <= 1 -> listOf(dur / 2)
                else -> (0 until count).map { i -> (dur * (0.15 + 0.7 * i / (count - 1))).toLong() }
            }
            positions.mapNotNull { ms ->
                r.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
            }
        } finally {
            r.release()
        }
    }
}
