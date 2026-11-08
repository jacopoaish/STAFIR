package com.stafir.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Metadati letti dal file originale. */
data class FileMeta(
    val latLon: Pair<Double, Double>? = null,
    /** Data di scatto dall'EXIF (millisecondi), se presente. */
    val dateTakenMs: Long? = null,
)

/**
 * Legge dal file originale le coordinate GPS (foto: EXIF, video: metadati) e la data di scatto EXIF.
 * Per la posizione serve il permesso ACCESS_MEDIA_LOCATION: senza, Android la nasconde.
 */
class MediaMetadataReader(private val context: Context) {

    fun read(uri: Uri, isVideo: Boolean): FileMeta = try {
        if (isVideo) readVideo(uri) else readPhoto(uri)
    } catch (_: Exception) {
        FileMeta()
    }

    private fun originalUri(uri: Uri): Uri = try {
        MediaStore.setRequireOriginal(uri)
    } catch (_: Exception) {
        uri
    }

    /** Apre il file originale (con la posizione); se non è permesso, quello normale. */
    private fun openFd(uri: Uri): ParcelFileDescriptor? = try {
        context.contentResolver.openFileDescriptor(originalUri(uri), "r")
    } catch (_: SecurityException) {
        context.contentResolver.openFileDescriptor(uri, "r")
    } catch (_: UnsupportedOperationException) {
        context.contentResolver.openFileDescriptor(uri, "r")
    }

    private fun readPhoto(uri: Uri): FileMeta {
        // Il descrittore di file permette di leggere anche HEIC/HEIF in modo efficiente.
        val pfd = openFd(uri) ?: return FileMeta()
        pfd.use {
            val exif = ExifInterface(it.fileDescriptor)
            val ll = exif.latLong?.let { a -> valid(a[0], a[1]) }
            return FileMeta(ll, exifDate(exif))
        }
    }

    private fun exifDate(exif: ExifInterface): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return try {
            val local = LocalDateTime.parse(raw.trim(), EXIF_FORMAT)
            if (local.year < 1990 || local.year > 2100) return null
            val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            if (offset != null && OFFSET.matches(offset)) {
                OffsetDateTime.parse(raw.trim().replace(' ', 'T').replaceFirst(':', '-').replaceFirst(':', '-') + offset)
                    .toInstant().toEpochMilli()
            } else {
                // Senza fuso orario: l'ora è quella locale di quando è stata scattata.
                local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readVideo(uri: Uri): FileMeta {
        val pfd = openFd(uri) ?: return FileMeta()
        val r = MediaMetadataRetriever()
        try {
            pfd.use { r.setDataSource(it.fileDescriptor) }
            val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) ?: return FileMeta()
            // Formato ISO-6709, es. "+45.4642+009.1900/"
            val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(loc) ?: return FileMeta()
            return FileMeta(valid(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()))
        } finally {
            r.release()
        }
    }

    private fun valid(lat: Double, lon: Double): Pair<Double, Double>? =
        if ((lat == 0.0 && lon == 0.0) || lat !in -90.0..90.0 || lon !in -180.0..180.0) null else lat to lon

    private companion object {
        val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
        val OFFSET = Regex("[+-]\\d{2}:\\d{2}")
    }
}
