package com.stafir.app.indexing

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.stafir.app.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class IndexScheduler(private val context: Context, private val settings: AppSettings) {
    private val wm get() = WorkManager.getInstance(context)

    /**
     * [now] = true: parte subito anche senza carica (tasto "Analizza ora").
     * Altrimenti rispetta l'impostazione "solo in carica".
     */
    fun scheduleAnalysis(now: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(!now && settings.current.onlyWhileCharging)
            .build()
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setConstraints(constraints)
            .addTag(TAG_ANALYSIS)
            .build()
        wm.enqueueUniqueWork(
            UNIQUE_ANALYSIS,
            if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun stopAnalysis() {
        wm.cancelUniqueWork(UNIQUE_ANALYSIS)
        settings.analysisRunning = false
    }

    /** Controllo periodico (ogni 6 ore) più trigger sui nuovi file. */
    fun ensurePeriodic() {
        val periodic = PeriodicWorkRequestBuilder<NewMediaWorker>(6, TimeUnit.HOURS)
            .setInputData(workDataOf(NewMediaWorker.KEY_PERIODIC to true))
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        watchNewMedia()
    }

    /** [fromWorker] = true quando chiamato dal worker stesso: la nuova attesa si accoda a quella in corso. */
    fun watchNewMedia(fromWorker: Boolean = false) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(60, TimeUnit.SECONDS)
            .build()
        val request = OneTimeWorkRequestBuilder<NewMediaWorker>().setConstraints(constraints).build()
        wm.enqueueUniqueWork(
            UNIQUE_TRIGGER,
            if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    val analysisInfo: Flow<WorkInfo?> =
        wm.getWorkInfosForUniqueWorkFlow(UNIQUE_ANALYSIS).map { list -> list.lastOrNull() }

    companion object {
        const val UNIQUE_ANALYSIS = "analysis"
        const val UNIQUE_PERIODIC = "periodic-sync"
        const val UNIQUE_TRIGGER = "new-media-trigger"
        const val TAG_ANALYSIS = "analysis"
    }
}
