package com.stafir.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface

/**
 * Legge le coordinate GPS originali di foto (EXIF) e video (metadati).
 * Richiede il permesso ACCESS_MEDIA_LOCATION: senza, Android nasconde la posizione.
 */
class LocationReader(private val context: Context) {

    fun read(uri: Uri, isVideo: Boolean): Pair<Double, Double>? = try {
        if (isVideo) readVideo(uri) else readPhoto(uri)
    } catch (_: Exception) {
        null
    }

    private fun originalUri(uri: Uri): Uri = try {
        MediaStore.setRequireOriginal(uri)
    } catch (_: Exception) {
        uri
    }

    private fun readPhoto(uri: Uri): Pair<Double, Double>? {
        val stream = try {
            context.contentResolver.openInputStream(originalUri(uri))
        } catch (_: SecurityException) {
            context.contentResolver.openInputStream(uri)
        } ?: return null
        stream.use {
            val latLong = ExifInterface(it).latLong ?: return null
            return valid(latLong[0], latLong[1])
        }
    }

    private fun readVideo(uri: Uri): Pair<Double, Double>? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) ?: return null
            // Formato ISO-6709, es. "+45.4642+009.1900/"
            val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(loc) ?: return null
            return valid(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
        } finally {
            r.release()
        }
    }

    private fun valid(lat: Double, lon: Double): Pair<Double, Double>? =
        if ((lat == 0.0 && lon == 0.0) || lat !in -90.0..90.0 || lon !in -180.0..180.0) null else lat to lon
}
