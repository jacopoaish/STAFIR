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
     * [limit] > 0: se il telefono non è in carica si ferma dopo quel numero di file
     * e il resto prosegue quando il telefono è in carica (usato al primo avvio).
     */
    fun scheduleAnalysis(now: Boolean, limit: Int = 0) {
        wm.enqueueUniqueWork(
            UNIQUE_ANALYSIS,
            if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request(requiresCharging = !now && settings.current.onlyWhileCharging, limit = limit),
        )
    }

    /** Chiamato dal worker che ha raggiunto il limite: il resto riparte quando c'è la carica. */
    fun continueLater() {
        wm.enqueueUniqueWork(
            UNIQUE_ANALYSIS,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(requiresCharging = settings.current.onlyWhileCharging, limit = 0),
        )
    }

    private fun request(requiresCharging: Boolean, limit: Int) =
        OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(requiresCharging)
                    .build(),
            )
            .setInputData(workDataOf(AnalysisWorker.KEY_LIMIT to limit))
            .addTag(TAG_ANALYSIS)
            .build()

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

    /** Lo stato più significativo: in corso, poi in attesa, poi l'ultimo concluso. */
    val analysisInfo: Flow<WorkInfo?> =
        wm.getWorkInfosForUniqueWorkFlow(UNIQUE_ANALYSIS).map { list ->
            list.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?: list.firstOrNull { !it.state.isFinished }
                ?: list.lastOrNull()
        }

    companion object {
        const val UNIQUE_ANALYSIS = "analysis"
        const val UNIQUE_PERIODIC = "periodic-sync"
        const val UNIQUE_TRIGGER = "new-media-trigger"
        const val TAG_ANALYSIS = "analysis"
    }
}
