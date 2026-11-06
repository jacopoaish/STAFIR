package com.stafir.app.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stafir.app.StafirApp

/**
 * Si attiva quando cambia la libreria multimediale (anche con l'app chiusa):
 * aggiorna il database e prenota il riconoscimento dei file nuovi.
 */
class NewMediaWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StafirApp).container
        return try {
            val r = container.mediaSync.sync()
            if (r.added > 0 || r.updated > 0) container.scheduler.scheduleAnalysis(now = false)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } finally {
            // I trigger sui contenuti valgono una volta sola: ci si ri-registra.
            container.scheduler.watchNewMedia(fromWorker = !inputData.getBoolean(KEY_PERIODIC, false))
        }
    }

    companion object {
        const val KEY_PERIODIC = "periodic"
    }
}
