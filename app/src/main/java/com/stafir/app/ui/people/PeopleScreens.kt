package com.stafir.app.ui.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.stafir.app.ui.GalleryViewModel
import com.stafir.app.ui.common.FaceThumb
import com.stafir.app.ui.common.Format
import com.stafir.app.ui.common.Thumb
import com.stafir.core.db.PersonName
import com.stafir.core.db.PersonWithCover

fun PersonWithCover.faceThumb(): FaceThumb? = coverMediaId?.let {
    FaceThumb(it, coverIsVideo == true, coverDateModified ?: 0, coverFrameMs ?: -1, boxLeft ?: 0f, boxTop ?: 0f, boxRight ?: 1f, boxBottom ?: 1f)
}

@Composable
fun FaceCircle(thumb: FaceThumb?, size: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.Face, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Elenco delle persone riconosciute: le persone con un nome prima, poi le altre (dalla più frequente). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(vm: GalleryViewModel, onBack: () -> Unit, onPerson: (Long) -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Persone") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
    }) { p ->
        if (people.isEmpty()) {
            Column(Modifier.padding(p).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("🙂", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(if (analysis.pending > 0) "Sto cercando i volti nelle tue foto" else "Nessuna persona trovata per ora", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le persone compaiono qui man mano che le foto vengono analizzate. Dai un nome una volta sola: varrà per tutte le foto e i video.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(p).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tocca una persona per vedere tutte le sue foto e darle un nome. Le impronte dei volti restano solo sul telefono.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(people, key = { it.id }) { person ->
                Column(Modifier.clickable { onPerson(person.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    FaceCircle(person.faceThumb(), 92)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        person.name ?: "Aggiungi un nome", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (person.name == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(Format.plural(person.mediaCount, "foto", "foto"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Finestra per dare (o cambiare) il nome: suggerisce i nomi già usati, per unire le persone uguali. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameDialog(initial: String?, title: String, existing: List<PersonName>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial ?: "") }
    val suggestions = existing.filter { name.isNotBlank() && it.name.startsWith(name.trim(), ignoreCase = true) && !it.name.equals(name.trim(), true) }.take(6)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Persone già conosciute", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s -> SuggestionChip(onClick = { name = s.name }, label = { Text(s.name) }) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Il nome vale per tutte le foto e i video di questa persona. Se scegli un nome già usato, le due persone vengono unite.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Tutte le foto e i video di una persona; qui le si dà un nome, la si unisce ad altre o si correggono errori. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PersonScreen(vm: GalleryViewModel, personId: Long, onBack: () -> Unit, onOpen: (Int) -> Unit, onSwitchPerson: (Long) -> Unit) {
    val person by remember(personId) { vm.person(personId) }.collectAsStateWithLifecycle(initialValue = null)
    val items = remember(personId) { vm.personPaging(personId) }.collectAsLazyPagingItems()
    val people by vm.people.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var notThis by remember { mutableStateOf<Long?>(null) }
    val named by produceState(emptyList<PersonName>(), naming) { value = vm.namedPeople() }
    val label = person?.name ?: "Questa persona"

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(person?.name ?: "Senza nome") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altre azioni") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Unisci con un'altra persona…") }, onClick = { menu = false; merging = true })
                        DropdownMenuItem(text = { Text("Nascondi questa persona") }, onClick = { menu = false; vm.hidePerson(personId); onBack() })
                    }
                }
            },
        )
    }) { p ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(p).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaceCircle(person?.faceThumb(), 84)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person?.name ?: "Senza nome", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(Format.plural(person?.mediaCount ?: 0, "foto o video", "foto e video"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FilledTonalButton(onClick = { naming = true }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(if (person?.name == null) "Aggiungi un nome" else "Cambia nome")
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tieni premuta una foto se non è $label.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val m = items[index]
                Box(
                    Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer)
                        .combinedClickable(onClick = { if (m != null) onOpen(index) }, onLongClick = { notThis = m?.id }),
                ) {
                    if (m != null) {
                        AsyncImage(Thumb(m.id, m.isVideo, m.dateModified), m.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (m.isVideo) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp))
                    }
                }
            }
        }
    }

    if (naming) {
        NameDialog(person?.name, if (person?.name == null) "Come si chiama?" else "Cambia nome", named.filter { it.id != personId }, onDismiss = { naming = false }) { name ->
            naming = false
            vm.renamePerson(personId, name) { finalId -> if (finalId != personId) onSwitchPerson(finalId) }
        }
    }
    notThis?.let { mediaId ->
        AlertDialog(
            onDismissRequest = { notThis = null },
            title = { Text("Non è $label?") },
            text = { Text("Tolgo questa foto da $label. Il volto diventerà una persona a parte, così potrai darle il nome giusto.") },
            confirmButton = { Button(onClick = { vm.removePersonFromMedia(personId, mediaId); notThis = null }) { Text("Togli") } },
            dismissButton = { TextButton(onClick = { notThis = null }) { Text("Annulla") } },
        )
    }
    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("È la stessa persona di…") },
            text = {
                LazyColumn(Modifier.height(380.dp)) {
                    items(people.filter { it.id != personId }, key = { it.id }) { other ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                merging = false
                                // la persona con nome (o più grande) tiene i volti dell'altra
                                val keepOther = other.name != null || person?.name == null && other.faceCount >= (person?.faceCount ?: 0)
                                if (keepOther) { vm.mergePeople(other.id, personId); onSwitchPerson(other.id) } else vm.mergePeople(personId, other.id)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FaceCircle(other.faceThumb(), 44)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(other.name ?: "Senza nome", style = MaterialTheme.typography.titleSmall)
                                Text(Format.plural(other.mediaCount, "foto", "foto"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merging = false }) { Text("Chiudi") } },
        )
    }
}
