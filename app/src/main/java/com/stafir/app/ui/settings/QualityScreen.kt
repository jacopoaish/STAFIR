package com.stafir.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stafir.app.StafirApp
import com.stafir.app.quality.QualityBench
import com.stafir.app.ui.common.Thumb
import com.stafir.domain.QualityReport
import com.stafir.domain.Vocabulary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun pct(x: Double) = "${(x * 100).toInt()}%"
private fun conceptName(id: String) = Vocabulary.conceptById(id)?.let { "${it.emoji} ${it.italianName}" } ?: id

/**
 * Banco qualità: misura la precisione del riconoscimento sulle foto etichettate a mano dall'utente.
 * Le foto si aggiungono dal visualizzatore (ⓘ → «Banco qualità»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = (ctx.applicationContext as StafirApp).container
    val store = container.quality
    val samples by store.samples.collectAsStateWithLifecycle()
    val runs by store.runs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var report by remember { mutableStateOf<QualityReport?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Banco qualità") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Etichetta a mano alcune tue foto (consigliate almeno 30, di tipi diversi): Stafir confronta ciò che riconosce con le tue risposte " +
                    "e ti dice quanto è preciso. Per aggiungere una foto: aprila, tocca ⓘ e poi «Banco qualità».",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = samples.isNotEmpty() && !running,
                onClick = {
                    running = true; progress = 0f
                    scope.launch {
                        report = withContext(Dispatchers.Default) {
                            QualityBench(container).run { d, t -> progress = d.toFloat() / t }
                        }
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (samples.isEmpty()) "Nessuna foto nel banco" else "Misura la qualità (${samples.size} foto)") }
            if (running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            report?.let { r -> ReportView(r) }

            if (runs.isNotEmpty()) {
                SectionHeader("Misure precedenti")
                val fmt = SimpleDateFormat("d MMM HH:mm", Locale.ITALIAN)
                runs.forEach { run ->
                    Text(
                        "${fmt.format(Date(run.timestamp))} · soglia ${(run.threshold * 100).toInt()}% · ${run.samples} foto · " +
                            "precisione ${pct(run.precision)} · richiamo ${pct(run.recall)} · ${run.avgMillis.toInt()} ms/foto",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            SectionHeader("Foto nel banco (${samples.size})")
            samples.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = Thumb(s.mediaId, false, 0),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (s.expected.isEmpty()) "Nessuna categoria" else s.expected.joinToString(", ") { conceptName(it) },
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { store.remove(s.mediaId) }) { Icon(Icons.Filled.Close, "Togli dal banco") }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ReportView(r: QualityReport) {
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Risultato su ${r.samples} foto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Metric("Precisione", pct(r.overall.precision))
                Metric("Richiamo", pct(r.overall.recall))
                Metric("Perfette", if (r.samples > 0) pct(r.exactMatches.toDouble() / r.samples) else "–")
                Metric("Tempo", "${r.avgMillis.toInt()} ms")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Precisione: quando dice «c'è il mare», quante volte ha ragione. Richiamo: delle foto con il mare, quante ne trova.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    SectionHeader("Per categoria (dalla più debole)")
    r.perConcept.forEach { c ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(conceptName(c.conceptId), Modifier.weight(1f))
            Text("P ${pct(c.precision)} · R ${pct(c.recall)}", style = MaterialTheme.typography.bodySmall)
        }
    }
    val wrong = r.outcomes.filter { it.missed.isNotEmpty() || it.extra.isNotEmpty() }
    if (wrong.isNotEmpty()) {
        SectionHeader("Dove sbaglia (${wrong.size})")
        wrong.take(50).forEach { o ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = Thumb(o.mediaId, false, 0),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (o.missed.isNotEmpty()) Text("Non trovato: " + o.missed.joinToString(", ") { conceptName(it) }, style = MaterialTheme.typography.bodySmall)
                    if (o.extra.isNotEmpty()) Text("Visto per errore: " + o.extra.joinToString(", ") { conceptName(it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

