package com.stafir.app.ui.home

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.stafir.app.ui.AnalysisUi
import com.stafir.app.ui.GalleryViewModel
import com.stafir.app.ui.common.Format
import com.stafir.app.ui.common.Thumb
import com.stafir.core.db.MediaEntity
import com.stafir.domain.MediaTypeFilter
import com.stafir.domain.PlaceLevel
import com.stafir.domain.Source
import com.stafir.domain.Vocabulary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    onOpen: (Int) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onBatteryGuide: () -> Unit,
) {
    val ctx = LocalContext.current
    val items = vm.paging.collectAsLazyPagingItems()
    val text by vm.text.collectAsStateWithLifecycle()
    val chips by vm.chips.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val settings by vm.settings.values.collectAsStateWithLifecycle()
    val killed by vm.killedBySystem.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Scorrendo i risultati la tastiera si chiude da sola
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) focus.clearFocus()
    }

    val selecting = selection.isNotEmpty()
    var searchFocused by remember { mutableStateOf(false) }
    // "Indietro": prima chiude la tastiera, poi annulla la selezione, poi azzera la ricerca.
    BackHandler(enabled = searchFocused) { focus.clearFocus() }
    BackHandler(enabled = !searchFocused && selecting) { vm.clearSelection() }
    BackHandler(enabled = !searchFocused && !selecting && (text.isNotEmpty() || !chips.isEmpty)) { vm.clearAll() }

    // Torna in cima solo quando cambia davvero la ricerca (non al ritorno dal visualizzatore)
    val query by vm.currentQuery.collectAsStateWithLifecycle()
    LaunchedEffect(query) {
        if (vm.lastScrolledQuery != null && vm.lastScrolledQuery != query) gridState.scrollToItem(0)
        vm.lastScrolledQuery = query
    }
    // Al ritorno dal visualizzatore, mostra la foto vista per ultima se è fuori schermo
    LaunchedEffect(Unit) {
        val idx = vm.lastViewedIndex ?: return@LaunchedEffect
        vm.lastViewedIndex = null
        val visible = withTimeoutOrNull(1_500) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.index } }.first { it.isNotEmpty() }
        }
        if (visible == null || idx !in visible.first()..visible.last()) gridState.scrollToItem(idx)
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.text.value = it }
        }
    }
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            vm.clearSelection()
            vm.refresh()
        }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(Format.plural(selection.size, "selezionato", "selezionati")) },
                    navigationIcon = { IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Filled.Close, "Annulla") } },
                    actions = {
                        IconButton(onClick = {
                            val uris = ArrayList(vm.selectedUris())
                            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "*/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(send, "Condividi"))
                        }) { Icon(Icons.Filled.Share, "Condividi") }
                        IconButton(onClick = {
                            val pi = MediaStore.createTrashRequest(ctx.contentResolver, vm.selectedUris(), true)
                            trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        }) { Icon(Icons.Filled.Delete, "Sposta nel cestino") }
                    },
                )
            } else {
                Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = text,
                            onValueChange = { vm.text.value = it },
                            modifier = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused },
                            placeholder = { Text("Cerca o chiedi… es. «mare in Sicilia 2024»", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (text.isNotEmpty()) {
                                    IconButton(onClick = { vm.text.value = "" }) { Icon(Icons.Filled.Close, "Cancella") }
                                } else {
                                    IconButton(onClick = {
                                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Cosa cerchi?")
                                        }
                                        runCatching { voice.launch(i) }
                                    }) { Icon(Icons.Filled.Mic, "Detta") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        )
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Impostazioni") }
                    }
                    FilterRow(vm)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Avvisi
            if (killed) {
                Banner(
                    "Il sistema ha interrotto il riconoscimento. Su HyperOS serve attivare l'avvio automatico.",
                    action = "Risolvi", onAction = onBatteryGuide, onDismiss = { vm.dismissKilledBanner() },
                )
            } else if (vm.needsBatterySetup && analysis.pending > 0) {
                Banner(
                    "Per non far fermare il riconoscimento, attiva «Avvio automatico» e togli le restrizioni batteria.",
                    action = "Mostrami come", onAction = onBatteryGuide, onDismiss = null,
                )
            }
            if (vm.partialAccess) {
                Banner("Hai concesso l'accesso solo ad alcune foto. Per vedere tutto l'archivio consenti l'accesso completo.", action = null, onAction = {}, onDismiss = null)
            }
            AnalysisCard(analysis, onNow = { vm.analyzeNow() }, onStop = { vm.stopAnalysis() }, onDetails = onStats)

            // Cosa ho capito
            if (search.understood.isNotEmpty() || search.notice != null) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    if (search.understood.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ho capito: ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            search.understood.forEach {
                                AssistChip(onClick = {}, label = { Text(it) }, modifier = Modifier.padding(end = 6.dp))
                            }
                        }
                    }
                    search.notice?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (search.resultCount >= 0 && (text.isNotEmpty() || !chips.isEmpty)) {
                Text(
                    Format.plural(search.resultCount, "risultato", "risultati"),
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (items.itemCount == 0 && search.resultCount == 0) {
                EmptyState(searching = text.isNotEmpty() || !chips.isEmpty, analysis = analysis)
            } else {
                MediaGrid(
                    items = items,
                    state = gridState,
                    columns = settings.gridColumns,
                    selection = selection,
                    onColumnsChange = { vm.settings.setGridColumns(it) },
                    onClick = { index, m ->
                        if (selecting) vm.toggleSelection(m) else onOpen(index)
                    },
                    onLongClick = { m -> vm.toggleSelection(m) },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(vm: GalleryViewModel) {
    val chips by vm.chips.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = chips.mediaType == MediaTypeFilter.PHOTOS, onClick = { vm.toggleType(MediaTypeFilter.PHOTOS) }, label = { Text("Foto") })
        FilterChip(selected = chips.mediaType == MediaTypeFilter.VIDEOS, onClick = { vm.toggleType(MediaTypeFilter.VIDEOS) }, label = { Text("Video") })
        FilterChip(selected = chips.favoritesOnly, onClick = { vm.toggleFavorites() }, label = { Text("Preferiti") })
        DropChip("Categorie", chips.concepts.size) { close ->
            Vocabulary.concepts.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.emoji}  ${c.italianName}") },
                    trailingIcon = { if (chips.concepts.any { it.id == c.id }) Icon(Icons.Filled.CheckCircle, null) },
                    onClick = { vm.toggleConcept(c) },
                )
            }
        }
        DropChip("Luoghi", chips.places.size) { _ ->
            if (options.places.isEmpty()) {
                DropdownMenuItem(text = { Text("I luoghi compaiono man mano che le foto vengono analizzate") }, onClick = {}, enabled = false)
            }
            options.places.forEach { p ->
                val prefix = when (p.level) { PlaceLevel.COUNTRY -> "🌍"; PlaceLevel.REGION -> "🗺️"; PlaceLevel.CITY -> "📍" }
                DropdownMenuItem(
                    text = { Text("$prefix  ${p.value}") },
                    trailingIcon = { if (p in chips.places) Icon(Icons.Filled.CheckCircle, null) },
                    onClick = { vm.togglePlace(p) },
                )
            }
        }
        DropChip("Data", if (chips.dateRange != null) 1 else 0) { close ->
            DropdownMenuItem(text = { Text("Qualsiasi") }, onClick = { vm.setYear(null); close() })
            options.years.forEach { y ->
                DropdownMenuItem(text = { Text(y) }, onClick = { vm.setYear(y); close() })
            }
        }
        DropChip("Provenienza", chips.sources.size) { _ ->
            Source.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.italian) },
                    trailingIcon = { if (s in chips.sources) Icon(Icons.Filled.CheckCircle, null) },
                    onClick = { vm.toggleSource(s) },
                )
            }
        }
        FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Persone · presto") })
    }
}

@Composable
private fun DropChip(label: String, activeCount: Int, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = activeCount > 0,
            onClick = { open = true },
            label = { Text(if (activeCount > 0) "$label ($activeCount)" else label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

@Composable
private fun Banner(text: String, action: String?, onAction: () -> Unit, onDismiss: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
            if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi") }
        }
    }
}

@Composable
private fun AnalysisCard(a: AnalysisUi, onNow: () -> Unit, onStop: () -> Unit, onDetails: () -> Unit) {
    AnimatedVisibility(visible = a.pending > 0 || a.running) {
        Card(
            onClick = onDetails,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
                val title = when {
                    a.tooHot -> "In pausa: il telefono è caldo"
                    a.running -> "Riconoscimento in corso"
                    a.waitingForCharge -> "In attesa della ricarica"
                    else -> "Da riconoscere"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${Format.count(a.analyzed)} di ${Format.count(a.total)}" +
                                (if (a.running) " · ${Format.eta(a.etaSeconds)}" else " · ${Format.count(a.pending)} rimanenti") +
                                (if (a.failed > 0) " · ${a.failed} saltati" else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (a.running) TextButton(onClick = onStop) { Text("Pausa") }
                    else TextButton(onClick = onNow) { Text("Analizza ora") }
                }
                LinearProgressIndicator(
                    progress = { if (a.total > 0) (a.analyzed + a.failed).toFloat() / a.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, analysis: AnalysisUi) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (searching) "🔎" else "🖼️", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (searching) "Nessun risultato" else "Nessuna foto o video trovati",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                searching && analysis.pending > 0 ->
                    "Ho riconosciuto finora ${Format.count(analysis.analyzed)} file su ${Format.count(analysis.total)}: i risultati cresceranno man mano. Prova anche parole diverse, ad esempio «spiaggia» invece di «mare»."
                searching -> "Prova con parole diverse o togli qualche filtro."
                else -> "Scatta qualche foto: comparirà qui automaticamente."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: LazyPagingItems<MediaEntity>,
    state: LazyGridState,
    columns: Int,
    selection: Set<Long>,
    onColumnsChange: (Int) -> Unit,
    onClick: (Int, MediaEntity) -> Unit,
    onLongClick: (MediaEntity) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var zoomAccum by remember { mutableFloatStateOf(1f) }
    val currentColumns by androidx.compose.runtime.rememberUpdatedState(columns)

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                // Pizzica con due dita per cambiare la dimensione delle miniature
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        zoomAccum = 1f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoomAccum *= event.calculateZoom()
                                event.changes.forEach { it.consume() }
                                if (zoomAccum > 1.3f && currentColumns > 2) {
                                    onColumnsChange(currentColumns - 1); zoomAccum = 1f
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (zoomAccum < 0.75f && currentColumns < 7) {
                                    onColumnsChange(currentColumns + 1); zoomAccum = 1f
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val m = items[index]
                if (m == null) {
                    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer))
                } else {
                    GridCell(
                        m = m,
                        selected = m.id in selection,
                        selecting = selection.isNotEmpty(),
                        modifier = Modifier.combinedClickable(
                            onClick = { onClick(index, m) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick(m)
                            },
                        ),
                    )
                }
            }
        }
        FastScroller(items, state, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun GridCell(m: MediaEntity, selected: Boolean, selecting: Boolean, modifier: Modifier) {
    Box(modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer)) {
        AsyncImage(
            model = Thumb(m.id, m.isVideo, m.dateModified),
            contentDescription = m.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (selected) 10.dp else 0.dp),
        )
        if (m.isVideo) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(Format.duration(m.durationMs), color = Color.White, fontSize = 10.sp)
            }
        }
        if (m.isFavorite) {
            Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(14.dp))
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp)
                    .background(if (selected) Color.White else Color.Transparent, CircleShape),
            )
        }
    }
}

/** Barra laterale da trascinare per scorrere velocemente decine di migliaia di elementi, con la data. */
@Composable
private fun FastScroller(items: LazyPagingItems<MediaEntity>, state: LazyGridState, modifier: Modifier) {
    val count = items.itemCount
    if (count < 200) return
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val shownFraction = if (dragging) fraction else state.firstVisibleItemIndex.toFloat() / count
    val thumbH = with(density) { 48.dp.toPx() }

    Box(
        modifier
            .fillMaxHeight(0.9f)
            .width(36.dp)
            .onSizeChanged { size = it }
            .pointerInput(count) {
                detectVerticalDragGestures(
                    onDragStart = { o -> dragging = true; fraction = (o.y / size.height).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((fraction * (count - 1)).roundToInt()) }
                }
            },
    ) {
        val y = (shownFraction * (size.height - thumbH)).roundToInt().coerceAtLeast(0)
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y) }.padding(end = 2.dp).width(6.dp).height(48.dp),
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.5f),
        ) {}
        if (dragging) {
            val idx = (fraction * (count - 1)).roundToInt()
            val label = items.peek(idx)?.let { Format.monthYear(it.dateTaken) } ?: "…"
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-with(density) { 44.dp.roundToPx() }, y) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp,
            ) { Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge) }
        }
    }
}
