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

    /** Apre il file originale (con la posizione); se non è permesso, quello normale. */
    private fun openFd(uri: Uri) = try {
        context.contentResolver.openFileDescriptor(originalUri(uri), "r")
    } catch (_: SecurityException) {
        context.contentResolver.openFileDescriptor(uri, "r")
    } catch (_: UnsupportedOperationException) {
        context.contentResolver.openFileDescriptor(uri, "r")
    }

    private fun readPhoto(uri: Uri): Pair<Double, Double>? {
        // Il descrittore di file permette di leggere anche HEIC/HEIF in modo efficiente.
        val pfd = openFd(uri) ?: return null
        pfd.use {
            val latLong = ExifInterface(it.fileDescriptor).latLong ?: return null
            return valid(latLong[0], latLong[1])
        }
    }

    private fun readVideo(uri: Uri): Pair<Double, Double>? {
        val pfd = openFd(uri) ?: return null
        val r = MediaMetadataRetriever()
        try {
            pfd.use { r.setDataSource(it.fileDescriptor) }
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
