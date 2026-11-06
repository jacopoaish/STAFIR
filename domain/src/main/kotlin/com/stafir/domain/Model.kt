package com.stafir.domain

/** Tipo di elemento multimediale. */
enum class MediaKind { PHOTO, VIDEO }

/** Filtro sul tipo di elemento. */
enum class MediaTypeFilter { ANY, PHOTOS, VIDEOS }

/** Da dove arriva un file (ricavato dal percorso e dal nome). */
enum class Source(val italian: String) {
    CAMERA("Fotocamera"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    MESSAGING("Altre app di messaggi"),
    SCREENSHOT("Screenshot"),
    SCREEN_RECORDING("Registrazioni schermo"),
    DOWNLOAD("Download"),
    SOCIAL("Social"),
    EDITED("Modificate"),
    OTHER("Altro");
}

/** Intervallo di date in millisecondi epoch, estremi inclusi/esclusi: [fromMs, toMs). */
data class DateRange(val fromMs: Long, val toMs: Long, val label: String)

enum class PlaceLevel { CITY, REGION, COUNTRY }

data class PlaceFilter(val level: PlaceLevel, val value: String)

/**
 * Comando riconosciuto nella barra (es. "crea un carosello...").
 * In Fase 1 i comandi vengono riconosciuti e spiegati; l'esecuzione arriva nelle fasi successive.
 */
data class CommandIntent(val verb: String, val description: String, val availableInPhase: Int)

/**
 * Ricerca strutturata: è il risultato della comprensione di una frase in italiano
 * e/o dei filtri toccati sotto la barra.
 */
data class SearchQuery(
    val mediaType: MediaTypeFilter = MediaTypeFilter.ANY,
    val dateRange: DateRange? = null,
    val sources: Set<Source> = emptySet(),
    val places: List<PlaceFilter> = emptyList(),
    val concepts: List<Concept> = emptyList(),
    val favoritesOnly: Boolean = false,
    val freeText: List<String> = emptyList(),
    val command: CommandIntent? = null,
) {
    val isEmpty: Boolean
        get() = mediaType == MediaTypeFilter.ANY && dateRange == null && sources.isEmpty() &&
            places.isEmpty() && concepts.isEmpty() && !favoritesOnly && freeText.isEmpty()

    /** Unisce due ricerche (es. testo scritto + filtri toccati). */
    fun mergedWith(other: SearchQuery): SearchQuery = SearchQuery(
        mediaType = if (other.mediaType != MediaTypeFilter.ANY) other.mediaType else mediaType,
        dateRange = other.dateRange ?: dateRange,
        sources = sources + other.sources,
        places = (places + other.places).distinct(),
        concepts = (concepts + other.concepts).distinctBy { it.id },
        favoritesOnly = favoritesOnly || other.favoritesOnly,
        freeText = (freeText + other.freeText).distinct(),
        command = command ?: other.command,
    )

    /** Spiegazione leggibile di cosa è stato capito, mostrata come "Ho capito: ...". */
    fun explain(): List<String> = buildList {
        when (mediaType) {
            MediaTypeFilter.PHOTOS -> add("Solo foto")
            MediaTypeFilter.VIDEOS -> add("Solo video")
            MediaTypeFilter.ANY -> {}
        }
        concepts.forEach { add(it.italianName) }
        places.forEach { add("📍 ${it.value}") }
        dateRange?.let { add("📅 ${it.label}") }
        sources.forEach { add("Da ${it.italian}") }
        if (favoritesOnly) add("★ Preferiti")
        freeText.forEach { add("\"$it\"") }
    }
}
