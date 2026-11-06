package com.stafir.app.indexing

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stafir.app.R
import com.stafir.app.StafirApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileNotFoundException

/**
 * Riconoscimento in background. Riparte sempre da dove era arrivato perché lo stato
 * di ogni file è salvato nel database; le volte successive guarda solo i file nuovi.
 */
class AnalysisWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val container = (ctx.applicationContext as StafirApp).container

    override suspend fun doWork(): Result {
        val settings = container.settings
        val dao = container.database.mediaDao()
        try {
            setForeground(foregroundInfo(0, 0, null))
        } catch (e: Exception) {
            // Android può vietare il servizio in primo piano se l'app è chiusa: proseguiamo comunque.
            Log.i(TAG, "Analisi senza notifica in primo piano: ${e.message}")
        }

        try {
            container.mediaSync.sync()
        } catch (e: SecurityException) {
            return Result.failure(workDataOf(KEY_ERROR to "Permesso di accesso a foto e video mancante"))
        }

        val s = settings.current
        val total = dao.pendingCount(s.recognizeVideos)
        if (total == 0) {
            settings.analysisRunning = false
            return Result.success()
        }
        settings.analysisRunning = true
        val pipeline = AnalysisPipeline(container)
        val power = applicationContext.getSystemService(PowerManager::class.java)
        val battery = applicationContext.getSystemService(BatteryManager::class.java)
        val limit = inputData.getInt(KEY_LIMIT, 0)
        val semaphore = Semaphore(PARALLELISM)
        var done = 0
        val start = System.currentTimeMillis()
        var lastUiBump = start
        var reachedLimit = false

        try {
            while (!isStopped) {
                // Primo avvio senza carica: analizza solo le foto più recenti, il resto in carica.
                if (limit > 0 && done >= limit && !battery.isCharging) {
                    reachedLimit = true
                    break
                }

                // Protezione dal surriscaldamento
                when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> {
                        setProgress(progressData(done, total, -1, PAUSED_HOT))
                        return Result.retry()
                    }
                    PowerManager.THERMAL_STATUS_MODERATE -> delay(2_000)
                    PowerManager.THERMAL_STATUS_LIGHT -> delay(300)
                    else -> {}
                }

                val current = settings.current
                val batch = dao.pendingBatch(BATCH, current.recognizeVideos)
                if (batch.isEmpty()) break

                coroutineScope {
                    batch.map { item ->
                        async {
                            semaphore.withPermit {
                                try {
                                    pipeline.analyze(item, current)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: OutOfMemoryError) {
                                    dao.markFailed(item.id, "File troppo grande da analizzare")
                                } catch (e: Exception) {
                                    dao.markFailed(item.id, friendlyError(e))
                                }
                            }
                        }
                    }.awaitAll()
                }

                done += batch.size
                val elapsed = (System.currentTimeMillis() - start) / 1000f
                val rate = if (elapsed > 0) done / elapsed else 0f
                if (rate > 0) settings.analysisRate = rate
                val remaining = (total - done).coerceAtLeast(0)
                val eta = if (rate > 0) (remaining / rate).toLong() else -1L
                val now = System.currentTimeMillis()
                settings.analysisHeartbeat = now
                setProgress(progressData(done, total, eta, RUNNING))
                notify(done, total, eta)
                // Ogni tanto la griglia e la ricerca si aggiornano con i nuovi risultati.
                if (now - lastUiBump > UI_REFRESH_MS) {
                    dao.bumpUi(now)
                    lastUiBump = now
                }
            }
        } finally {
            pipeline.close()
            // Arresto controllato (fine, pausa, carica staccata): non è un'interruzione del sistema.
            settings.analysisRunning = false
            withContext(NonCancellable) { dao.bumpUi() }
        }
        if (reachedLimit) container.scheduler.continueLater()
        return Result.success()
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is FileNotFoundException -> "File non trovato (forse cancellato o spostato)"
        is SecurityException -> "Accesso al file negato"
        is java.io.IOException -> "File danneggiato o in un formato non leggibile"
        else -> "Impossibile analizzare: ${e.javaClass.simpleName}"
    }

    private fun progressData(done: Int, total: Int, eta: Long, state: String) =
        workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_ETA to eta, KEY_STATE to state)

    private fun notification(done: Int, total: Int, eta: Long?) =
        NotificationCompat.Builder(applicationContext, StafirApp.CHANNEL_INDEXING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Riconoscimento foto e video")
            .setContentText(
                if (total == 0) "Preparazione…"
                else "$done di $total" + (eta?.takeIf { it >= 0 }?.let { " · ${formatEta(it)}" } ?: ""),
            )
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun foregroundInfo(done: Int, total: Int, eta: Long?) =
        ForegroundInfo(NOTIFICATION_ID, notification(done, total, eta), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun notify(done: Int, total: Int, eta: Long) {
        try {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(done, total, eta))
        } catch (_: SecurityException) {
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0, null)

    companion object {
        private const val TAG = "AnalysisWorker"
        const val NOTIFICATION_ID = 1001
        const val BATCH = 24
        const val PARALLELISM = 3
        const val UI_REFRESH_MS = 15_000L
        const val KEY_LIMIT = "limit"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ETA = "eta"
        const val KEY_STATE = "state"
        const val KEY_ERROR = "error"
        const val RUNNING = "running"
        const val PAUSED_HOT = "hot"

        fun formatEta(seconds: Long): String = when {
            seconds < 60 -> "meno di un minuto"
            seconds < 3600 -> "circa ${seconds / 60} min"
            else -> "circa ${seconds / 3600} h ${(seconds % 3600) / 60} min"
        }
    }
}
