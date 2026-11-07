package com.stafir.app.ui.common

import android.graphics.Bitmap
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
import com.stafir.ai.FrameLoader
import com.stafir.core.media.MediaStoreSync
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Miniatura di un volto: ritaglio quadrato attorno al volto, dalla stessa immagine usata per riconoscerlo. */
data class FaceThumb(
    val mediaId: Long,
    val isVideo: Boolean,
    val dateModified: Long,
    val frameMs: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

class FaceThumbFetcher(private val data: FaceThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val ctx = options.context
        val uri = MediaStoreSync.contentUri(data.mediaId, data.isVideo)
        val src: Bitmap = if (data.isVideo && data.frameMs >= 0) {
            FrameLoader(ctx).videoFrame(uri, data.frameMs, 1024) ?: ctx.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
        } else {
            ctx.contentResolver.loadThumbnail(uri, Size(if (data.isVideo) 1024 else 1280, if (data.isVideo) 1024 else 1280), null)
        }
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val cx = (data.left + data.right) / 2 * w
        val cy = (data.top + data.bottom) / 2 * h
        val side = min(max((data.right - data.left) * w, (data.bottom - data.top) * h) * 1.7f, min(w, h))
        val x = (cx - side / 2).coerceIn(0f, w - side).roundToInt()
        val y = (cy - side / 2).coerceIn(0f, h - side).roundToInt()
        val s = side.roundToInt().coerceAtLeast(1)
        val target = options.size.width.pxOrElse { 256 }.coerceIn(48, 512)
        val crop = Bitmap.createBitmap(src, x, y, min(s, src.width - x), min(s, src.height - y))
        val out = Bitmap.createScaledBitmap(crop, target, target, true)
        if (crop !== src && crop !== out) crop.recycle()
        if (src !== out) src.recycle()
        return ImageFetchResult(image = out.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<FaceThumb> {
        override fun create(data: FaceThumb, options: Options, imageLoader: ImageLoader): Fetcher = FaceThumbFetcher(data, options)
    }

    class Key : Keyer<FaceThumb> {
        override fun key(data: FaceThumb, options: Options): String =
            "face:${data.mediaId}:${data.dateModified}:${data.frameMs}:${data.left}:${data.top}"
    }
}
