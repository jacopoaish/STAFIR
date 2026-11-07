package com.stafir.domain

import java.time.LocalDate
import java.time.ZoneId

/** Elenco dei luoghi conosciuti (quelli presenti nell'archivio), per riconoscerli nelle frasi. */
fun interface PlaceDirectory {
    /** [normalizedPhrase] è già normalizzata (minuscolo, senza accenti). */
    fun lookup(normalizedPhrase: String): PlaceFilter?

    companion object {
        val EMPTY = PlaceDirectory { null }

        fun of(places: Collection<PlaceFilter>, aliases: Map<String, PlaceFilter> = emptyMap()): PlaceDirectory {
            val map = HashMap<String, PlaceFilter>()
            // A parità di nome il paese vince sulla regione che vince sulla città (es. "San Marino").
            places.sortedByDescending { it.level.ordinal }.forEach { map.putIfAbsent(TextNormalizer.normalize(it.value), it) }
            aliases.forEach { (k, v) -> map.putIfAbsent(TextNormalizer.normalize(k), v) }
            return PlaceDirectory { map[it] }
        }
    }
}

/** Persone con un nome (dai volti riconosciuti), per riconoscerle nelle frasi. */
fun interface PersonDirectory {
    /** [normalizedPhrase] è già normalizzata (minuscolo, senza accenti). */
    fun lookup(normalizedPhrase: String): PersonFilter?

    companion object {
        val EMPTY = PersonDirectory { null }

        fun of(people: Collection<PersonFilter>): PersonDirectory {
            val map = HashMap<String, PersonFilter>()
            people.forEach { map.putIfAbsent(TextNormalizer.normalize(it.name), it) }
            return PersonDirectory { map[it] }
        }
    }
}

/**
 * Comprende frasi in italiano naturale e le trasforma in una [SearchQuery] strutturata.
 *
 * Fase 1: comprensione a regole (veloce, < 1 ms, nessun modello). È progettata per essere
 * affiancata in seguito da un piccolo modello linguistico locale per le frasi più complesse:
 * entrambi producono la stessa [SearchQuery].
 */
class ItalianQueryParser(
    private val places: PlaceDirectory = PlaceDirectory.EMPTY,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val people: PersonDirectory = PersonDirectory.EMPTY,
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) {

    fun parse(text: String): SearchQuery {
        val tokens = TextNormalizer.tokens(text)
        if (tokens.isEmpty()) return SearchQuery()

        var wantsPhotos = false
        var wantsVideos = false
        var dateRange: DateRange? = null
        val sources = linkedSetOf<Source>()
        val placeFilters = mutableListOf<PlaceFilter>()
        val personFilters = mutableListOf<PersonFilter>()
        val concepts = mutableListOf<Concept>()
        var favorites = false
        val free = mutableListOf<String>()
        var command: CommandIntent? = null

        val now = today()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]

            // 1) Comandi (solo come prime parole)
            if (command == null && i <= 1) {
                val cmd = COMMANDS[t]
                if (cmd != null) {
                    command = cmd
                    i++
                    continue
                }
            }

            // 2) Date
            val date = DateExpressions.match(tokens, i, now, zone)
            if (date != null) {
                dateRange = dateRange?.let { mergeDates(it, date.first) } ?: date.first
                i += date.second
                continue
            }

            // 3) Tipo di elemento
            if (t in VIDEO_WORDS) { wantsVideos = true; i++; continue }
            if (t in PHOTO_WORDS) { wantsPhotos = true; i++; continue }

            // 4) Provenienza (prova anche due parole: "registrazioni schermo")
            val src2 = if (i + 1 < tokens.size) SOURCE_WORDS["$t ${tokens[i + 1]}"] else null
            if (src2 != null) { sources += src2; i += 2; continue }
            val src = SOURCE_WORDS[t]
            if (src != null) { sources += src; i++; continue }

            // 5) Preferiti
            if (t in FAVORITE_WORDS) { favorites = true; i++; continue }

            // 5b) Persone con un nome (anche di più parole: "zia maria"): hanno la precedenza sulle altre parole
            var matchedPerson = false
            for (len in 3 downTo 1) {
                if (i + len <= tokens.size) {
                    val p = people.lookup(tokens.subList(i, i + len).joinToString(" "))
                    if (p != null) { personFilters += p; i += len; matchedPerson = true; break }
                }
            }
            if (matchedPerson) continue

            // 6) Luoghi su più parole ("reggio emilia", "stati uniti")
            var matchedPlace = false
            for (len in 4 downTo 2) {
                if (i + len <= tokens.size) {
                    val phrase = tokens.subList(i, i + len).joinToString(" ")
                    val p = places.lookup(phrase)
                    if (p != null) {
                        placeFilters += p; i += len; matchedPlace = true; break
                    }
                }
            }
            if (matchedPlace) continue

            // 7) Parole vuote (e, se c'è un comando, ciò che il comando crea: "carosello", "album"...)
            if (t in STOPWORDS || t.all { it.isDigit() }) { i++; continue }
            if (command != null && t in COMMAND_OBJECTS) { i++; continue }

            // 8) Concetti (scene, soggetti, documenti...)
            val concept = Vocabulary.conceptForWord(t) ?: singularCandidates(t).firstNotNullOfOrNull { Vocabulary.conceptForWord(it) }
            if (concept != null) {
                if (concepts.none { it.id == concept.id }) concepts += concept
                i++; continue
            }

            // 9) Luogo di una parola
            val p = places.lookup(t)
            if (p != null) { placeFilters += p; i++; continue }

            // 10) Testo libero (nomi file, cartelle, persone in futuro)
            if (t.length >= 2) free += t
            i++
        }

        val type = when {
            wantsVideos && !wantsPhotos -> MediaTypeFilter.VIDEOS
            wantsPhotos && !wantsVideos -> MediaTypeFilter.PHOTOS
            else -> MediaTypeFilter.ANY
        }
        return SearchQuery(
            mediaType = type,
            dateRange = dateRange,
            sources = sources,
            places = placeFilters.distinct(),
            people = personFilters.distinctBy { it.id },
            concepts = concepts,
            favoritesOnly = favorites,
            freeText = free.distinct(),
            command = command,
        )
    }

    private fun mergeDates(a: DateRange, b: DateRange): DateRange {
        // "dal 2020 al 2022" -> unione; altrimenti intersezione se si sovrappongono
        val from = minOf(a.fromMs, b.fromMs)
        val to = maxOf(a.toMs, b.toMs)
        return DateRange(from, to, "${a.label} – ${b.label}")
    }

    private fun singularCandidates(t: String): List<String> = when {
        t.length < 4 -> emptyList()
        t.endsWith("i") -> listOf(t.dropLast(1) + "o", t.dropLast(1) + "e", t.dropLast(1) + "a")
        t.endsWith("e") -> listOf(t.dropLast(1) + "a", t.dropLast(1) + "o")
        t.endsWith("he") -> listOf(t.dropLast(2) + "a")
        else -> emptyList()
    }

    companion object {
        val VIDEO_WORDS = setOf("video", "filmato", "filmati", "filmino", "filmini", "clip", "registrazione", "registrazioni")
        val PHOTO_WORDS = setOf("foto", "fotografia", "fotografie", "immagine", "immagini", "scatto", "scatti", "fotine")
        val FAVORITE_WORDS = setOf("preferita", "preferite", "preferito", "preferiti", "cuore", "stella", "stellina")

        val SOURCE_WORDS: Map<String, Source> = mapOf(
            "whatsapp" to Source.WHATSAPP, "wa" to Source.WHATSAPP, "whats" to Source.WHATSAPP,
            "telegram" to Source.TELEGRAM,
            "signal" to Source.MESSAGING, "messenger" to Source.MESSAGING, "messaggi" to Source.MESSAGING,
            "screenshot" to Source.SCREENSHOT, "screen" to Source.SCREENSHOT, "schermata" to Source.SCREENSHOT,
            "schermate" to Source.SCREENSHOT, "catture" to Source.SCREENSHOT,
            "registrazioni schermo" to Source.SCREEN_RECORDING, "registrazione schermo" to Source.SCREEN_RECORDING,
            "download" to Source.DOWNLOAD, "scaricate" to Source.DOWNLOAD, "scaricati" to Source.DOWNLOAD,
            "scaricata" to Source.DOWNLOAD, "scaricato" to Source.DOWNLOAD,
            "fotocamera" to Source.CAMERA, "camera" to Source.CAMERA, "scattate" to Source.CAMERA, "scattati" to Source.CAMERA,
            "instagram" to Source.SOCIAL, "facebook" to Source.SOCIAL, "tiktok" to Source.SOCIAL, "social" to Source.SOCIAL,
            "modificate" to Source.EDITED, "modificati" to Source.EDITED,
        )

        val COMMANDS: Map<String, CommandIntent> = mapOf(
            "crea" to CommandIntent("crea", "Creare caroselli, storie e reel", 9),
            "componi" to CommandIntent("crea", "Creare caroselli, storie e reel", 9),
            "sposta" to CommandIntent("sposta", "Spostare file in album o nella cartella privata", 6),
            "metti" to CommandIntent("sposta", "Mettere file in album o categorie", 7),
            "aggiungi" to CommandIntent("aggiungi", "Aggiungere a un album o a una categoria", 7),
            "elimina" to CommandIntent("elimina", "Spostare nel cestino", 6),
            "cancella" to CommandIntent("elimina", "Spostare nel cestino", 6),
            "nascondi" to CommandIntent("nascondi", "Nascondere nella cartella privata", 8),
            "converti" to CommandIntent("converti", "Convertire formato (es. HEIC → JPG)", 9),
            "ridimensiona" to CommandIntent("ridimensiona", "Ridimensionare", 9),
            "ritaglia" to CommandIntent("ritaglia", "Ritagliare nei formati social", 9),
            "esporta" to CommandIntent("esporta", "Esportare", 10),
            "rinomina" to CommandIntent("rinomina", "Rinominare", 6),
            "condividi" to CommandIntent("condividi", "Condividere", 6),
            "proponi" to CommandIntent("proponi", "Proporre foto da eliminare o migliori scatti", 5),
            "ogni" to CommandIntent("automazione", "Automazioni \"se succede questo, fai quello\"", 7),
        )

        /** Oggetti dei comandi: non sono parole da cercare nelle foto. */
        val COMMAND_OBJECTS: Set<String> = setOf(
            "carosello", "caroselli", "storia", "storie", "reel", "collage", "presentazione", "album",
            "cartella", "cartelle", "privata", "privato", "cestino", "jpg", "jpeg", "heic", "png", "formato",
            "instagram", "post", "didascalia", "hashtag", "nuovo", "nuova", "nuovi", "nuove", "settimana",
        )

        val STOPWORDS: Set<String> = setOf(
            "il", "lo", "la", "i", "gli", "le", "l", "un", "uno", "una", "un'",
            "di", "del", "dello", "della", "dei", "degli", "delle", "dell", "d",
            "a", "al", "allo", "alla", "ai", "agli", "alle", "all",
            "da", "dal", "dallo", "dalla", "dai", "dagli", "dalle", "dall",
            "in", "nel", "nello", "nella", "nei", "negli", "nelle", "nell",
            "su", "sul", "sullo", "sulla", "sui", "sugli", "sulle", "sull",
            "con", "col", "per", "tra", "fra", "e", "ed", "o", "oppure", "anche", "che", "chi", "dove", "quando",
            "mio", "mia", "miei", "mie", "tuo", "tua", "suo", "sua", "nostro", "nostra",
            "tutto", "tutta", "tutti", "tutte", "qualche", "alcune", "alcuni", "ogni",
            "mostra", "mostrami", "fammi", "vedere", "vedi", "cerca", "cercami", "trova", "trovami", "voglio", "vorrei",
            "ho", "hai", "ha", "abbiamo", "sono", "era", "erano", "c", "ci", "ce", "fatto", "fatta", "fatte", "fatti",
            "migliori", "migliore", "belle", "bella", "belli", "bello", "piu", "meno", "solo", "soltanto",
            "questo", "questa", "questi", "queste", "quel", "quella", "quelle", "quelli", "quest", "me", "mi", "noi",
            "periodo", "durante", "dentro", "fuori", "presso", "vicino", "sotto", "sopra", "senza",
        )
    }
}
