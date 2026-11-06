package com.stafir.app.ui.common

import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.stafir.core.media.MediaStoreSync

/** Richiesta di miniatura per la griglia. */
data class Thumb(val id: Long, val isVideo: Boolean, val dateModified: Long)

/**
 * Usa le miniature che Android tiene già pronte (le stesse della Galleria):
 * scorrimento fluido anche con centinaia di migliaia di elementi, nessuno spazio in più.
 */
class ThumbnailFetcher(
    private val data: Thumb,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 384 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { w }.coerceIn(64, 1024)
        val uri = MediaStoreSync.contentUri(data.id, data.isVideo)
        val bmp = options.context.contentResolver.loadThumbnail(uri, Size(w, h), null)
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options)
    }

    class Key : Keyer<Thumb> {
        override fun key(data: Thumb, options: Options): String = "thumb:${data.id}:${data.dateModified}"
    }
}
