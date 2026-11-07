package com.stafir.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stafir.ai.FaceRecognizer
import com.stafir.app.BuildConfigInfo
import com.stafir.app.StafirApp
import com.stafir.app.indexing.AnalysisPipeline
import com.stafir.app.system.DeviceHelper
import com.stafir.app.ui.GalleryViewModel
import com.stafir.app.ui.common.Format
import com.stafir.app.ui.onboarding.ToggleRow
import com.stafir.core.db.MediaEntity
import com.stafir.core.db.ValueCount
import com.stafir.domain.Source
import com.stafir.domain.Vocabulary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

/** Ricontrolla lo stato dei permessi quando si torna dalle impostazioni di sistema. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onStats: () -> Unit,
    onBatteryGuide: () -> Unit,
    onQuality: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as StafirApp
    val settings = vm.settings
    val s by settings.values.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var advanced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    Page("Impostazioni", onBack) {
        SectionTitle("Riconoscimento")
        ToggleRow(Icons.Filled.Landscape, "Scene e oggetti", "Mare, montagna, cibo, animali, documenti…", s.recognizeScenes) { settings.setRecognizeScenes(it) }
        ToggleRow(Icons.Filled.Place, "Luoghi", "Città, regione e paese dal GPS, senza internet", s.recognizePlaces) { settings.setRecognizePlaces(it) }
        ToggleRow(Icons.Filled.Videocam, "Video", "3 fotogrammi chiave per ogni video", s.recognizeVideos) { settings.setRecognizeVideos(it) }
        ToggleRow(Icons.Filled.Face, "Volti e persone", "Raggruppa le foto della stessa persona, solo sul telefono", s.recognizeFaces) {
            settings.setRecognizeFaces(it)
            app.container.scheduler.scheduleAnalysis(now = false)
        }
        ToggleRow(Icons.Filled.BatteryChargingFull, "Solo in carica", "Consigliato: l'analisi automatica parte quando il telefono è in carica", s.onlyWhileCharging) {
            settings.setOnlyWhileCharging(it)
            app.container.scheduler.scheduleAnalysis(now = false)
        }
        Row {
            Button(onClick = { vm.analyzeNow() }) { Text("Analizza ora") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.stopAnalysis() }) { Text("Ferma") }
        }
        NavRow(Icons.Filled.BarChart, "Archivio e avanzamento", "Quanti file, quanto spazio, cosa è stato riconosciuto", onStats)

        SectionTitle("Funzionamento in background")
        NavRow(
            Icons.Filled.BatteryChargingFull,
            if (DeviceHelper.isXiaomi) "Avvio automatico e batteria (HyperOS)" else "Batteria",
            if (tick >= 0 && DeviceHelper.isIgnoringBatteryOptimizations(ctx)) "Nessuna restrizione: ottimo" else "Da configurare: tocca qui",
            onBatteryGuide,
        )
        if (DeviceHelper.supportsManageMedia) {
            val can = tick >= 0 && DeviceHelper.canManageMedia(ctx)
            NavRow(
                Icons.Filled.PermMedia,
                "Gestione contenuti multimediali",
                if (can) "Attiva: cestino e preferiti senza conferme ripetute" else "Facoltativo: evita una conferma per ogni file spostato nel cestino",
            ) { DeviceHelper.launchFirst(ctx, listOf(DeviceHelper.manageMediaIntent(ctx))) }
        }

        SectionTitle("Aspetto")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            SingleChoiceSegmentedButtonRow {
                listOf("Sistema", "Chiaro", "Scuro").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = s.themeMode == i,
                        onClick = { settings.setThemeMode(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(label) }
                }
            }
        }

        SectionTitle("Privacy")
        Row(Modifier.padding(vertical = 8.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(
                "Stafir non ha il permesso di accedere a internet: foto, video e risultati del riconoscimento restano sul telefono. " +
                    "I file non vengono mai copiati, spostati o cancellati senza una tua conferma.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        Row(
            Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text("Avanzate", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (advanced) {
            Text("Soglia di riconoscimento: ${(s.minConfidence * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Text(
                "Più bassa = più risultati ma meno precisi. Più alta = meno risultati ma più sicuri.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(value = s.minConfidence, onValueChange = { settings.setMinConfidence((it * 20).toInt() / 20f) }, valueRange = 0.4f..0.9f, steps = 9)

            NavRow(
                Icons.Filled.Science,
                "Banco qualità",
                "Misura precisione e velocità del riconoscimento sulle tue foto etichettate a mano",
                onQuality,
            )

            SectionTitle("Modelli in uso")
            ModelRow("Scene e oggetti", "Google ML Kit Image Labeling (incluso, 400+ categorie)", "CPU · TFLite/XNNPACK")
            ModelRow(
                "Volti",
                "Google ML Kit Face Detection + FaceNet 128-d (Apache 2.0, incluso)",
                if (FaceRecognizer.modelPresent(app)) "CPU · TFLite/XNNPACK, 4 thread" else "Modello non presente in questa build",
            )
            ModelRow("Luoghi", "GeoNames città >15.000 abitanti (incluso)", if (app.container.geocoder.isAvailable) "Pronto" else "Dati non presenti in questa build")
            ModelRow("Comprensione frasi", "Analizzatore italiano a regole, riconosce anche i nomi delle persone", "< 1 ms per frase")
            Text(
                "In arrivo: CLIP multilingue su NPU Hexagon per la ricerca per immagini, piccolo modello linguistico locale e OCR. " +
                    "Il «banco di prova» per confrontarli arriverà con le funzioni avanzate.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Manutenzione")
            Row {
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { app.container.database.mediaDao().retryFailed() }
                        vm.analyzeNow(); message = "Riprovo i file saltati."
                    }
                }) { Text("Riprova saltati") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { app.container.database.mediaDao().resetAllAnalysis() }
                        message = "Rianalizzerò tutto l'archivio (in carica)."
                    }
                }) { Text("Rianalizza tutto") }
            }
            OutlinedButton(onClick = {
                app.container.mediaSync.forceFullSyncNextTime()
                app.requestSync(0)
                message = "Ricostruisco l'indice dei file."
            }) { Text("Ricostruisci indice file") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            SectionTitle("Informazioni")
            Text("Stafir ${BuildConfigInfo.versionName(ctx)} · analisi v${AnalysisPipeline.VERSION}", style = MaterialTheme.typography.bodySmall)
            Text("Dispositivo: ${DeviceHelper.deviceName}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Immagini dell'icona: ESA/Hubble, ESA/Webb, EHT Collaboration (CC BY 4.0); NASA; foto CC0 da Stocksnap e Rawpixel.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelRow(title: String, model: String, hw: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(model, style = MaterialTheme.typography.bodyMedium)
        Text(hw, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatsScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val kinds by vm.kindStats.collectAsStateWithLifecycle()
    val a by vm.analysis.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val dao = (ctx.applicationContext as StafirApp).container.database.mediaDao()
    val failed by dao.failed().collectAsStateWithLifecycle(initialValue = emptyList<MediaEntity>())
    val sources by dao.sourceStats().collectAsStateWithLifecycle(initialValue = emptyList<ValueCount>())
    val labels by produceState(emptyList<ValueCount>(), a.analyzed / 200) {
        value = withContext(Dispatchers.IO) { dao.topLabels(30) }
    }

    Page("Archivio", onBack) {
        val photos = kinds.firstOrNull { !it.isVideo }
        val videos = kinds.firstOrNull { it.isVideo }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${Format.count((photos?.count ?: 0) + (videos?.count ?: 0))} elementi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${Format.count(photos?.count ?: 0)} foto · ${Format.bytes(photos?.bytes ?: 0)}")
                Text("${Format.count(videos?.count ?: 0)} video · ${Format.bytes(videos?.bytes ?: 0)}")
                Text(
                    "Spazio usato da Stafir: ${Format.bytes(ctx.getDatabasePath("stafir.db").length())} (solo indice, nessuna copia dei file)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionTitle("Riconoscimento")
        LinearProgressIndicator(
            progress = { if (a.total > 0) (a.analyzed + a.failed).toFloat() / a.total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text("Analizzati: ${Format.count(a.analyzed)} · Da fare: ${Format.count(a.pending)} · Saltati: ${Format.count(a.failed)}")
        Text(
            when {
                a.running -> "In corso · tempo stimato: ${Format.eta(a.etaSeconds)}"
                a.waitingForCharge -> "In attesa della ricarica · tempo stimato: ${Format.eta(a.etaSeconds)}"
                a.pending == 0 -> "Tutto l'archivio è stato analizzato ✓"
                else -> "In pausa"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Provenienza")
        sources.forEach {
            Text("${runCatching { Source.valueOf(it.value).italian }.getOrDefault(it.value)}: ${Format.count(it.count)}")
        }

        if (labels.isNotEmpty()) {
            SectionTitle("Cosa c'è nel tuo archivio")
            Text(labels.joinToString(" · ") { "${Vocabulary.italianLabel(it.value)} (${Format.count(it.count)})" }, style = MaterialTheme.typography.bodyMedium)
        }

        if (failed.isNotEmpty()) {
            SectionTitle("File saltati (${failed.size})")
            Text("Questi file non sono stati analizzati. Di solito sono danneggiati o in un formato particolare: restano comunque visibili nella galleria.", style = MaterialTheme.typography.bodySmall)
            failed.take(100).forEach {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(it.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text("${it.relativePath} · ${it.analysisError ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BatteryGuideScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val s by vm.settings.values.collectAsStateWithLifecycle()
    val batteryOk = tick >= 0 && DeviceHelper.isIgnoringBatteryOptimizations(ctx)

    Page(if (DeviceHelper.isXiaomi) "HyperOS: non fermare l'analisi" else "Batteria", onBack) {
        Text(
            "Il riconoscimento lavora in background. ${if (DeviceHelper.isXiaomi) "HyperOS" else "Il sistema"} a volte chiude le app per risparmiare batteria: " +
                "segui questi passi una volta sola.",
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp),
        )
        if (DeviceHelper.isXiaomi) {
            GuideStep(
                1, "Attiva «Avvio automatico»",
                "Si apre la schermata di HyperOS: cerca Stafir nell'elenco e attiva l'interruttore. Poi torna qui e tocca «Fatto».",
                done = s.autostartConfirmed,
                action = "Apri" to { DeviceHelper.launchFirst(ctx, DeviceHelper.autostartIntents(ctx)); Unit },
                secondary = (if (s.autostartConfirmed) "Annulla" else "Fatto") to { vm.settings.setAutostartConfirmed(!s.autostartConfirmed); Unit },
            )
        }
        GuideStep(
            if (DeviceHelper.isXiaomi) 2 else 1, "Batteria: «Nessuna restrizione»",
            if (DeviceHelper.isXiaomi) "Nella schermata Risparmio batteria scegli «Nessuna restrizione». Se compare una richiesta di sistema, tocca «Consenti»."
            else "Consenti all'app di funzionare in background senza ottimizzazioni.",
            done = batteryOk,
            action = "Apri" to {
                if (DeviceHelper.isXiaomi) DeviceHelper.launchFirst(ctx, DeviceHelper.xiaomiBatteryIntents(ctx))
                else DeviceHelper.launchFirst(ctx, listOf(DeviceHelper.batteryOptimizationIntent(ctx)))
                Unit
            },
            secondary = if (DeviceHelper.isXiaomi && !batteryOk) "Richiesta di sistema" to { DeviceHelper.launchFirst(ctx, listOf(DeviceHelper.batteryOptimizationIntent(ctx))); Unit } else null,
        )
        if (DeviceHelper.isXiaomi) {
            GuideStep(
                3, "Blocca Stafir nelle app recenti (facoltativo)",
                "Apri le app recenti, tieni premuta l'anteprima di Stafir e tocca il lucchetto 🔒. Così HyperOS non la chiude pulendo la memoria.",
                done = false, action = null, secondary = null,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Puoi verificare che funzioni: metti il telefono in carica con lo schermo spento e, dopo qualche minuto, il numero di file analizzati deve crescere.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GuideStep(
    n: Int,
    title: String,
    text: String,
    done: Boolean,
    action: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("$n. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row {
                action?.let { (label, f) -> TextButton(onClick = f) { Text(label) } }
                secondary?.let { (label, f) -> TextButton(onClick = f) { Text(label) } }
            }
        }
    }
}
