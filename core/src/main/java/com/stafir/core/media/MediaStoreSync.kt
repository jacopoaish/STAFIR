package com.stafir.core.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import com.stafir.core.db.MediaEntity
import com.stafir.core.db.StafirDatabase
import com.stafir.domain.SourceClassifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Conteggio dell'archivio mostrato al primo avvio. */
data class ArchiveCount(val photos: Int, val videos: Int, val photoBytes: Long, val videoBytes: Long) {
    val total get() = photos + videos
    val totalBytes get() = photoBytes + videoBytes
}

data class SyncResult(val added: Int, val updated: Int, val removed: Int, val full: Boolean)

/**
 * Tiene il database allineato con la libreria multimediale del telefono (MediaStore),
 * la stessa usata dalla Galleria. Non copia né modifica i file.
 */
class MediaStoreSync(private val context: Context, private val db: StafirDatabase) {

    private val resolver get() = context.contentResolver
    /** Una sola sincronizzazione alla volta (le richieste arrivano da più parti). */
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("media_sync", Context.MODE_PRIVATE)

    private val imagesUri: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videoUri: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Conta velocemente foto e video e il loro peso totale. */
    fun countArchive(): ArchiveCount {
        fun count(uri: Uri): Pair<Int, Long> {
            var n = 0
            var bytes = 0L
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                n = c.count
                while (c.moveToNext()) bytes += c.getLong(0)
            }
            return n to bytes
        }
        val (p, pb) = count(imagesUri)
        val (v, vb) = count(videoUri)
        return ArchiveCount(p, v, pb, vb)
    }

    /**
     * Sincronizza. La prima volta (o se MediaStore è stato ricostruito) legge tutto;
     * le volte successive legge solo ciò che è cambiato dall'ultima generazione vista.
     */
    suspend fun sync(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): SyncResult = mutex.withLock {
        syncLocked(onProgress)
    }

    private suspend fun syncLocked(onProgress: (done: Int, total: Int) -> Unit): SyncResult {
        val version = MediaStore.getVersion(context)
        val lastGen = prefs.getLong(KEY_GENERATION, -1)
        val full = lastGen < 0 || prefs.getString(KEY_VERSION, null) != version
        val dao = db.mediaDao()

        val existing = dao.allSyncKeys().associateBy { it.id }
        val selection = if (full) null else "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?"
        val args = if (full) null else arrayOf(lastGen.toString())

        var maxGen = maxOf(lastGen, 0L)
        val toInsert = ArrayList<MediaEntity>()
        val toUpdate = ArrayList<Pair<MediaEntity, Boolean>>()
        val seen = HashSet<Long>()

        for ((uri, isVideo) in listOf(imagesUri to false, videoUri to true)) {
            resolver.query(uri, projection(isVideo), selection, args, null)?.use { c ->
                val total = c.count
                var done = 0
                val idx = Columns(c)
                while (c.moveToNext()) {
                    val e = idx.read(c, isVideo)
                    seen += e.id
                    maxGen = maxOf(maxGen, e.generation)
                    val old = existing[e.id]
                    if (old == null) {
                        toInsert += e
                    } else if (old.dateModified != e.dateModified || old.sizeBytes != e.sizeBytes || old.isFavorite != e.isFavorite) {
                        val contentChanged = old.dateModified != e.dateModified || old.sizeBytes != e.sizeBytes
                        toUpdate += e to contentChanged
                    }
                    if (++done % 2000 == 0) onProgress(done, total)
                }
                onProgress(total, total)
            }
        }

        // File cancellati con altre app: confronto degli identificativi (veloce, solo gli ID).
        val removed = if (full) {
            existing.keys.filter { it !in seen }
        } else {
            val current = HashSet<Long>(existing.size + toInsert.size)
            for (uri in listOf(imagesUri, videoUri)) {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
                    while (c.moveToNext()) current += c.getLong(0)
                }
            }
            existing.keys.filter { it !in current }
        }

        db.withTransaction {
            toInsert.chunked(500).forEach { dao.insertAll(it) }
            toUpdate.forEach { (e, reset) ->
                dao.updateMetadata(
                    e.id, e.displayName, e.relativePath, e.bucketName, e.mimeType, e.sizeBytes, e.width, e.height,
                    e.durationMs, e.dateTaken, e.dateModified, e.generation, e.isFavorite, e.source, reset,
                )
            }
        }
        if (removed.isNotEmpty()) dao.deleteCompletely(removed)
        if (full || toInsert.isNotEmpty() || toUpdate.isNotEmpty() || removed.isNotEmpty()) dao.bumpUi()

        prefs.edit().putLong(KEY_GENERATION, maxGen).putString(KEY_VERSION, version)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        return SyncResult(toInsert.size, toUpdate.size, removed.size, full)
    }

    fun forceFullSyncNextTime() {
        prefs.edit().remove(KEY_GENERATION).apply()
    }

    private fun projection(isVideo: Boolean) = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.RELATIVE_PATH)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.DATE_TAKEN)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.DATE_ADDED)
        add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        add(MediaStore.MediaColumns.IS_FAVORITE)
        add(MediaStore.MediaColumns.ORIENTATION)
        if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
    }.toTypedArray()

    private class Columns(c: Cursor) {
        val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val path = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val bucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val mime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val width = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val height = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val taken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val modified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val gen = c.getColumnIndexOrThrow(MediaStore.MediaColumns.GENERATION_MODIFIED)
        val fav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
        val orientation = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
        val duration = c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)

        fun read(c: Cursor, isVideo: Boolean): MediaEntity {
            val displayName = c.getString(name) ?: ""
            val relativePath = c.getString(path) ?: ""
            val modifiedSec = c.getLong(modified)
            var takenMs = if (c.isNull(taken)) 0L else c.getLong(taken)
            if (takenMs <= 0L) takenMs = (if (modifiedSec > 0) modifiedSec else c.getLong(added)) * 1000L
            var w = c.getInt(width)
            var h = c.getInt(height)
            val rot = if (c.isNull(orientation)) 0 else c.getInt(orientation)
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            return MediaEntity(
                id = c.getLong(id),
                isVideo = isVideo,
                displayName = displayName,
                relativePath = relativePath,
                bucketName = c.getString(bucket),
                mimeType = c.getString(mime),
                sizeBytes = c.getLong(size),
                width = w,
                height = h,
                durationMs = if (duration >= 0 && !c.isNull(duration)) c.getLong(duration) else 0L,
                dateTaken = takenMs,
                dateModified = modifiedSec,
                generation = c.getLong(gen),
                isFavorite = c.getInt(fav) == 1,
                source = SourceClassifier.classify(relativePath, displayName).name,
            )
        }
    }

    companion object {
        private const val KEY_GENERATION = "generation"
        private const val KEY_VERSION = "version"
        private const val KEY_LAST_SYNC = "last_sync"

        fun contentUri(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            id,
        )

    }
}

fun MediaEntity.contentUri(): Uri = MediaStoreSync.contentUri(id, isVideo)
