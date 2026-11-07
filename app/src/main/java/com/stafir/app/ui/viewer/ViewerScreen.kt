package com.stafir.app.ui.viewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.stafir.app.StafirApp
import com.stafir.app.ui.GalleryViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stafir.app.ui.common.Format
import com.stafir.app.ui.common.Thumb
import com.stafir.core.db.MediaEntity
import com.stafir.core.db.MediaLabelEntity
import com.stafir.core.media.contentUri
import com.stafir.domain.Source
import com.stafir.domain.Vocabulary
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextOverflow
import com.stafir.app.ui.common.FaceThumb
import com.stafir.app.ui.people.FaceCircle
import com.stafir.app.ui.people.NameDialog
import com.stafir.core.db.FaceWithPerson
import com.stafir.core.db.PersonName

/** Visualizzatore a schermo intero: scorri tra le foto, pizzica per ingrandire, tocca per i comandi. */
@Composable
fun ViewerScreen(vm: GalleryViewModel, startIndex: Int, onBack: () -> Unit, onPerson: (Long) -> Unit, personId: Long? = null) {
    val source = remember(personId) { if (personId == null) vm.paging else vm.personPaging(personId) }
    val items = source.collectAsLazyPagingItems()
    // Aspetta che la lista sia pronta prima di posizionarsi sulla foto scelta
    var ready by remember { mutableStateOf(items.itemCount > startIndex) }
    LaunchedEffect(Unit) {
        snapshotFlow { items.itemCount }.first { it > startIndex }
        ready = true
    }
    if (!ready) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex) { items.itemCount }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { if (personId == null) vm.lastViewedIndex = it }
    }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<MediaEntity?>(null) }
    val ctx = LocalContext.current
    val requestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.refresh()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            val m = items[page]
            if (m == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (m.isVideo) {
                if (pager.currentPage == page) {
                    VideoPlayer(m.contentUri(), playing = true, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(Thumb(m.id, true, m.dateModified), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            } else {
                Zoomable(onTap = { chrome = !chrome }) {
                    FullImage(m)
                }
            }
        }

        val current = items.peek(pager.currentPage.coerceIn(0, (items.itemCount - 1).coerceAtLeast(0)))
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    current?.let {
                        Text(Format.dateLong(it.dateTaken), color = Color.White, style = MaterialTheme.typography.titleSmall)
                        listOfNotNull(it.city, it.country).joinToString(", ").takeIf { s -> s.isNotEmpty() }?.let { place ->
                            Text(place, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(chrome && current != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val m = current ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { share(ctx, m) }) { Icon(Icons.Filled.Share, "Condividi", tint = Color.White) }
                IconButton(onClick = {
                    val pi = MediaStore.createFavoriteRequest(ctx.contentResolver, listOf(m.contentUri()), !m.isFavorite)
                    requestLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }) {
                    Icon(if (m.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Preferito", tint = Color.White)
                }
                IconButton(onClick = { info = m }) { Icon(Icons.Filled.Info, "Informazioni", tint = Color.White) }
                IconButton(onClick = {
                    val pi = MediaStore.createTrashRequest(ctx.contentResolver, listOf(m.contentUri()), true)
                    requestLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }) { Icon(Icons.Filled.Delete, "Sposta nel cestino", tint = Color.White) }
            }
        }
    }
    info?.let { InfoSheet(vm, it, onDismiss = { info = null }, onPerson = { id -> info = null; onPerson(id) }) }
}

@Composable
private fun FullImage(m: MediaEntity) {
    val ctx = LocalContext.current
    // Prima la miniatura (istantanea), poi l'immagine in alta qualità
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(ctx).data(m.contentUri()).size(3072).build(),
        contentDescription = m.displayName,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
        loading = {
            AsyncImage(Thumb(m.id, false, m.dateModified), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Impossibile aprire questo file: potrebbe essere danneggiato.", color = Color.White, modifier = Modifier.padding(32.dp))
            }
        },
    )
}

private fun share(ctx: android.content.Context, m: MediaEntity) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = m.mimeType ?: if (m.isVideo) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, m.contentUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Condividi"))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InfoSheet(vm: GalleryViewModel, start: MediaEntity, onDismiss: () -> Unit, onPerson: (Long) -> Unit) {
    val ctx = LocalContext.current
    val m by produceState(start, start.id) { value = vm.mediaById(start.id) ?: start }
    val labels by produceState<List<MediaLabelEntity>>(emptyList(), start.id) { value = vm.labelsFor(start.id) }
    var facesVersion by remember { mutableStateOf(0) }
    val faces by produceState<List<FaceWithPerson>>(emptyList(), start.id, facesVersion) { value = vm.facesOf(start.id) }
    var chosenFace by remember { mutableStateOf<FaceWithPerson?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(m.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            InfoLine("Data", Format.dateLong(m.dateTaken))
            InfoLine("Dimensioni", "${m.width} × ${m.height} · ${Format.bytes(m.sizeBytes)}")
            if (m.isVideo) InfoLine("Durata", Format.duration(m.durationMs))
            InfoLine("Cartella", m.relativePath)
            InfoLine("Provenienza", runCatching { Source.valueOf(m.source).italian }.getOrDefault(m.source))
            m.mimeType?.let { InfoLine("Formato", it) }
            val place = listOfNotNull(m.city, m.region, m.country).joinToString(", ")
            if (place.isNotEmpty()) InfoLine("Luogo", place)
            if (m.latitude != null && m.longitude != null) {
                TextButton(onClick = {
                    val geo = Uri.parse("geo:${m.latitude},${m.longitude}?q=${m.latitude},${m.longitude}")
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                }) {
                    Icon(Icons.Filled.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apri sulla mappa")
                }
            }
            if (faces.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Persone", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    faces.forEach { f ->
                        Column(Modifier.width(76.dp).clickable { chosenFace = f }, horizontalAlignment = Alignment.CenterHorizontally) {
                            FaceCircle(f.thumb(m), 64)
                            Text(
                                f.personName ?: "Senza nome", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (f.personName == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text("Tocca un volto per dargli un nome o correggerlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            when (m.analysisState) {
                0 -> Text("Non ancora analizzata: lo sarà a breve.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                -1 -> Text("Saltata: ${m.analysisError ?: "file non leggibile"}", color = MaterialTheme.colorScheme.error)
                else -> {
                    val concepts = Vocabulary.conceptsForLabels(labels.filter { it.confidence >= 0.6f }.map { it.label })
                    if (concepts.isNotEmpty()) {
                        Text("Categorie", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            concepts.forEach { SuggestionChip(onClick = {}, label = { Text("${it.emoji} ${it.italianName}") }) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Cosa ho riconosciuto", style = MaterialTheme.typography.titleSmall)
                    if (labels.isEmpty()) Text("Niente di specifico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.forEach {
                            SuggestionChip(onClick = {}, label = { Text("${Vocabulary.italianLabel(it.label)} ${(it.confidence * 100).toInt()}%") })
                        }
                    }
                    Text(
                        "Presto potrai correggere le etichette con un tocco.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (!m.isVideo) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                QualityBenchEditor(m.id, labels)
            }
        }
    }
    chosenFace?.let { f ->
        FaceDialog(vm, f, m, onDismiss = { chosenFace = null }, onChanged = { chosenFace = null; facesVersion++ }, onPerson = onPerson)
    }
}

private fun FaceWithPerson.thumb(m: MediaEntity) = FaceThumb(m.id, m.isVideo, m.dateModified, frameMs, boxLeft, boxTop, boxRight, boxBottom)

/** Cosa fare con un volto: dargli un nome, correggerlo o vedere tutte le foto della persona. */
@Composable
private fun FaceDialog(vm: GalleryViewModel, f: FaceWithPerson, m: MediaEntity, onDismiss: () -> Unit, onChanged: () -> Unit, onPerson: (Long) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    val named by produceState(emptyList<PersonName>(), naming) { value = vm.namedPeople() }
    val who = f.personName
    if (naming) {
        NameDialog(
            initial = null,
            title = if (who == null) "Come si chiama?" else "Chi è, allora?",
            existing = named,
            onDismiss = onDismiss,
        ) { name -> vm.nameFace(f.id, name) { onChanged() } }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { FaceCircle(f.thumb(m), 72) },
        title = { Text(who ?: "Persona senza nome") },
        text = {
            Column {
                if (who == null) {
                    TextButton(onClick = { naming = true }) { Text("Dai un nome (vale per tutte le sue foto)") }
                } else {
                    TextButton(onClick = { vm.removeFace(f.id); onChanged() }) { Text("Non è $who") }
                    TextButton(onClick = { naming = true }) { Text("È un'altra persona…") }
                }
                f.personId?.let { pid ->
                    TextButton(onClick = { onDismiss(); onPerson(pid) }) { Text(if (who == null) "Vedi tutte le sue foto" else "Vedi tutte le foto di $who") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}

/** Etichettatura a mano per il banco qualità (misura la precisione del riconoscimento). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityBenchEditor(mediaId: Long, labels: List<MediaLabelEntity>) {
    val ctx = LocalContext.current
    val store = (ctx.applicationContext as StafirApp).container.quality
    val samples by store.samples.collectAsStateWithLifecycle()
    val saved = samples.firstOrNull { it.mediaId == mediaId }?.expected
    var open by remember(mediaId) { mutableStateOf(false) }
    var chosen by remember(mediaId, saved) { mutableStateOf(saved ?: emptySet()) }

    if (!open) {
        TextButton(onClick = { open = true }) {
            Text(if (saved != null) "Banco qualità: modifica le etichette di questa foto" else "Banco qualità: aggiungi questa foto")
        }
        return
    }
    Text("Banco qualità", style = MaterialTheme.typography.titleSmall)
    Text(
        "Seleziona TUTTE le categorie presenti nella foto. Servono a misurare quanto è preciso il riconoscimento (Impostazioni → Avanzate).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Vocabulary.concepts.forEach { c ->
            FilterChip(
                selected = c.id in chosen,
                onClick = { chosen = if (c.id in chosen) chosen - c.id else chosen + c.id },
                label = { Text("${c.emoji} ${c.italianName}") },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { store.put(mediaId, chosen); open = false }) { Text("Salva") }
        TextButton(onClick = {
            chosen = Vocabulary.conceptsForLabels(labels.filter { it.confidence >= 0.6f }.map { it.label }).map { it.id }.toSet()
        }) { Text("Parti dal riconoscimento") }
        if (saved != null) TextButton(onClick = { store.remove(mediaId); open = false }) { Text("Togli") }
    }
}
