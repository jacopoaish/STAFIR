package com.stafir.app.quality

import android.content.Context
import com.stafir.domain.QualitySample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una misura salvata, per confrontare nel tempo modelli e soglie diverse. */
data class QualityRun(
    val timestamp: Long,
    val model: String,
    val threshold: Float,
    val samples: Int,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val avgMillis: Double,
)

/**
 * Il "banco qualità": poche foto dell'utente etichettate a mano (le categorie presenti),
 * usate per misurare quanto è preciso il riconoscimento. Salvato in un piccolo file JSON.
 */
class QualityStore(context: Context) {
    private val file = File(context.filesDir, "quality_set.json")

    private val _samples = MutableStateFlow<List<QualitySample>>(emptyList())
    val samples: StateFlow<List<QualitySample>> = _samples.asStateFlow()

    private val _runs = MutableStateFlow<List<QualityRun>>(emptyList())
    val runs: StateFlow<List<QualityRun>> = _runs.asStateFlow()

    init {
        load()
    }

    fun expectedFor(mediaId: Long): Set<String>? = _samples.value.firstOrNull { it.mediaId == mediaId }?.expected

    @Synchronized
    fun put(mediaId: Long, expected: Set<String>) {
        _samples.value = _samples.value.filterNot { it.mediaId == mediaId } + QualitySample(mediaId, expected)
        save()
    }

    @Synchronized
    fun remove(mediaId: Long) {
        _samples.value = _samples.value.filterNot { it.mediaId == mediaId }
        save()
    }

    @Synchronized
    fun addRun(run: QualityRun) {
        _runs.value = (listOf(run) + _runs.value).take(30)
        save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("samples") ?: JSONArray()
            _samples.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val exp = o.getJSONArray("expected")
                QualitySample(o.getLong("id"), (0 until exp.length()).map { exp.getString(it) }.toSet())
            }
            val runs = root.optJSONArray("runs") ?: JSONArray()
            _runs.value = (0 until runs.length()).map { i ->
                val o = runs.getJSONObject(i)
                QualityRun(
                    o.getLong("t"), o.getString("model"), o.getDouble("threshold").toFloat(), o.getInt("n"),
                    o.getDouble("p"), o.getDouble("r"), o.getDouble("f1"), o.getDouble("ms"),
                )
            }
        } catch (_: Exception) {
            // File illeggibile: si riparte da un banco vuoto senza bloccare l'app.
        }
    }

    private fun save() {
        val root = JSONObject()
        root.put("version", 1)
        root.put("samples", JSONArray().apply {
            _samples.value.forEach { s ->
                put(JSONObject().put("id", s.mediaId).put("expected", JSONArray(s.expected.sorted())))
            }
        })
        root.put("runs", JSONArray().apply {
            _runs.value.forEach { r ->
                put(
                    JSONObject().put("t", r.timestamp).put("model", r.model).put("threshold", r.threshold.toDouble())
                        .put("n", r.samples).put("p", r.precision).put("r", r.recall).put("f1", r.f1).put("ms", r.avgMillis),
                )
            }
        })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }
}
