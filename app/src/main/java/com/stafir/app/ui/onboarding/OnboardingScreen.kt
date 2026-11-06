package com.stafir.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stafir.app.StafirApp
import com.stafir.app.system.DeviceHelper
import com.stafir.app.system.Permissions
import com.stafir.app.ui.common.Format
import com.stafir.core.media.ArchiveCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quanti file analizzare subito al primo avvio, anche senza carica. */
private const val FIRST_RUN_LIMIT = 1500

/** Procedura guidata in 3 schermate: accesso → cosa riconoscere → avvia. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(if (Permissions.hasMediaAccess(ctx)) 1 else 0) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        StepDots(step)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) {
            AnimatedContent(targetState = step, label = "onboarding") { s ->
                when (s) {
                    0 -> PermissionStep(onGranted = { step = 1 })
                    1 -> ChooseStep(onNext = { step = 2 })
                    else -> StartStep(onFinished = onFinished)
                }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            Box(
                Modifier.height(6.dp).width(if (i == step) 32.dp else 16.dp)
                    .padding(0.dp),
            ) {
                LinearProgressIndicator(
                    progress = { if (i <= step) 1f else 0f },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PermissionStep(onGranted: () -> Unit) {
    val ctx = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Permissions.hasMediaAccess(ctx)) {
            (ctx.applicationContext as StafirApp).startWatching()
            onGranted()
        } else denied = true
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Benvenuto in Stafir", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "La tua galleria intelligente: trova qualsiasi foto o video scrivendo come parli, ad esempio «mare in Sicilia nel 2024».",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        InfoRow(Icons.Filled.Photo, "Accesso a foto e video", "Per mostrarli e riconoscerli. Lavora sugli stessi file della Galleria: nessuna copia, nessuno spazio in più.")
        InfoRow(Icons.Filled.Place, "Posizione nelle foto", "Per sapere dove le hai scattate (città, regione) anche senza internet.")
        InfoRow(Icons.Filled.Lock, "Tutto resta sul telefono", "L'app non ha accesso a internet: nessuna foto lascia il tuo telefono.")
        Spacer(Modifier.height(24.dp))
        if (denied) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Senza accesso non posso mostrarti le foto.", fontWeight = FontWeight.SemiBold)
                    Text("Tocca «Apri impostazioni» → Autorizzazioni → Foto e video → «Consenti sempre».")
                    TextButton(onClick = { DeviceHelper.launchFirst(ctx, listOf(DeviceHelper.appDetailsIntent(ctx))) }) {
                        Text("Apri impostazioni")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = {
                val perms = Permissions.media.toMutableList()
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                launcher.launch(perms.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Consenti accesso") }
    }
}

@Composable
private fun ChooseStep(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val settings = (ctx.applicationContext as StafirApp).container.settings
    val s by settings.values.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Cosa vuoi riconoscere?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Puoi cambiarlo quando vuoi dalle impostazioni.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        ToggleRow(Icons.Filled.Landscape, "Scene e oggetti", "Mare, montagna, cibo, animali, città, documenti…", s.recognizeScenes) { settings.setRecognizeScenes(it) }
        ToggleRow(Icons.Filled.Place, "Luoghi", "Città, regione e paese dal GPS, senza internet", s.recognizePlaces) { settings.setRecognizePlaces(it) }
        ToggleRow(Icons.Filled.Videocam, "Video", "Analizza 3 fotogrammi chiave per ogni video", s.recognizeVideos) { settings.setRecognizeVideos(it) }
        ToggleRow(Icons.Filled.BatteryChargingFull, "Solo in carica", "Il riconoscimento dell'intero archivio avviene mentre il telefono è in carica", s.onlyWhileCharging) { settings.setOnlyWhileCharging(it) }
        Spacer(Modifier.height(8.dp))
        Text("In arrivo nelle prossime fasi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
        ToggleRow(Icons.Filled.Face, "Volti e persone", "Fase 2", false, enabled = false) {}
        ToggleRow(Icons.Filled.Description, "Testo, scontrini e documenti (OCR)", "Fase 3", false, enabled = false) {}
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Avanti") }
    }
}

@Composable
private fun StartStep(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as StafirApp
    val c = app.container
    var count by remember { mutableStateOf<ArchiveCount?>(null) }
    var syncProgress by remember { mutableStateOf(0f) }
    var syncDone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            count = withContext(Dispatchers.IO) { c.mediaSync.countArchive() }
            withContext(Dispatchers.IO) {
                c.mediaSync.sync { done, total -> syncProgress = if (total > 0) done.toFloat() / total else 1f }
            }
            syncDone = true
        } catch (e: Exception) {
            error = "Non riesco a leggere l'archivio (${e.javaClass.simpleName}). Controlla che l'accesso a foto e video sia consentito."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Il tuo archivio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val n = count
        if (n == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Sto contando foto e video…")
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Format.count(n.total), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(if (n.total == 1) "elemento trovato" else "elementi trovati", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${Format.count(n.photos)} foto (${Format.bytes(n.photoBytes)})\n${Format.count(n.videos)} video (${Format.bytes(n.videoBytes)})",
                        textAlign = TextAlign.Center,
                    )
                    Text("Totale: ${Format.bytes(n.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!syncDone) {
                Text("Preparo l'indice…")
                LinearProgressIndicator(progress = { syncProgress }, modifier = Modifier.fillMaxWidth())
            } else {
                // Stima: ~25 file al secondo su Snapdragon 8 Gen 2 (verrà misurata davvero durante l'analisi)
                val etaSec = (n.photos / 25.0 + n.videos / 6.0).toLong()
                Text(
                    "Il riconoscimento di tutto l'archivio richiederà ${Format.eta(etaSec)}. " +
                        "Parto subito dalle ${Format.count(FIRST_RUN_LIMIT)} foto più recenti, così puoi già provare la ricerca; " +
                        "il resto proseguirà da solo quando il telefono è in carica.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (DeviceHelper.isXiaomi) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Consiglio per ${DeviceHelper.deviceName}", fontWeight = FontWeight.SemiBold)
                    Text("HyperOS tende a chiudere le app in background. Attiva «Avvio automatico» e togli le restrizioni batteria: potrai farlo anche dopo, dalle impostazioni.")
                    Row {
                        TextButton(onClick = { DeviceHelper.launchFirst(ctx, DeviceHelper.autostartIntents(ctx)); c.settings.setAutostartConfirmed(true) }) { Text("Avvio automatico") }
                        TextButton(onClick = { DeviceHelper.launchFirst(ctx, DeviceHelper.xiaomiBatteryIntents(ctx)) }) { Text("Batteria") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = syncDone,
            onClick = {
                scope.launch {
                    c.settings.setOnboardingDone(true)
                    app.startWatching()
                    c.scheduler.scheduleAnalysis(now = true, limit = FIRST_RUN_LIMIT)
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Avvia") }
        if (error != null) {
            OutlinedButton(onClick = { c.settings.setOnboardingDone(true); onFinished() }, modifier = Modifier.fillMaxWidth()) {
                Text("Continua comunque")
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
