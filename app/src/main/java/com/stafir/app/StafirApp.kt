package com.stafir.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.stafir.app.indexing.AnalysisPipeline
import com.stafir.app.system.Permissions
import com.stafir.app.ui.common.ThumbnailFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StafirApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _syncTick = MutableStateFlow(0L)
    /** Cambia ogni volta che la libreria è stata riallineata (per aggiornare l'interfaccia). */
    val syncTick = _syncTick.asStateFlow()
    private var pendingSync: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createChannels()
        if (Permissions.hasMediaAccess(this)) startWatching()
    }

    /** Da chiamare quando i permessi sono concessi. */
    fun startWatching() {
        container.scheduler.ensurePeriodic()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = requestSync()
        }
        runCatching {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        }
        requestSync(0)
    }

    /** Sincronizzazione con piccolo ritardo, per raggruppare tante modifiche ravvicinate. */
    fun requestSync(delayMs: Long = 1500) {
        pendingSync?.cancel()
        pendingSync = appScope.launch(Dispatchers.IO) {
            delay(delayMs)
            try {
                container.mediaSync.sync()
                _syncTick.value = System.currentTimeMillis()
                val s = container.settings.current
                if (s.onboardingDone && container.database.mediaDao().pendingCount(s.recognizeVideos, if (s.recognizeFaces) AnalysisPipeline.FACES_VERSION else 0) > 0) {
                    // KEEP: non disturba un'analisi già in corso o in attesa della carica.
                    container.scheduler.scheduleAnalysis(now = false)
                }
            } catch (_: SecurityException) {
            } catch (e: Exception) {
                android.util.Log.w("StafirApp", "Sincronizzazione non riuscita", e)
            }
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INDEXING, "Riconoscimento in background", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra l'avanzamento del riconoscimento di foto e video"
            },
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailFetcher.Factory())
                add(ThumbnailFetcher.Key())
                add(com.stafir.app.ui.common.FaceThumbFetcher.Factory())
                add(com.stafir.app.ui.common.FaceThumbFetcher.Key())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(150)
            .build()

    companion object {
        const val CHANNEL_INDEXING = "indexing"
    }
}
