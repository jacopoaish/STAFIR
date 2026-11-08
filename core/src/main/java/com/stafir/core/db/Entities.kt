package com.stafir.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un file della libreria multimediale del telefono. L'app NON copia i file:
 * qui ci sono solo i metadati e i risultati del riconoscimento.
 * [id] è lo stesso identificativo usato da MediaStore (e quindi dalla Galleria).
 */
@Entity(
    tableName = "media",
    indices = [
        Index("dateTaken"),
        Index("analysisState"),
        Index("source"),
        Index("city"),
        Index("region"),
        Index("country"),
        Index(value = ["isVideo", "dateTaken"]),
    ],
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val isVideo: Boolean,
    val displayName: String,
    val relativePath: String,
    val bucketName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    /** Data di scatto in millisecondi. */
    val dateTaken: Long,
    /**
     * true se la data non viene dai metadati di scatto ma è stimata (dal nome del file o dalla
     * data di modifica): durante l'analisi proviamo a migliorarla leggendo l'EXIF.
     */
    val dateEstimated: Boolean = false,
    /** Data di modifica in secondi (da MediaStore), usata per capire se il file è cambiato. */
    val dateModified: Long,
    val generation: Long,
    val isFavorite: Boolean,
    val source: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    /** 0 = da analizzare, 1 = analizzato, -1 = errore (file danneggiato o illeggibile). */
    val analysisState: Int = 0,
    val analysisVersion: Int = 0,
    val analysisError: String? = null,
    val analyzedAt: Long = 0,
    /** Versione del riconoscimento volti già applicata a questo file (0 = da fare). */
    @ColumnInfo(defaultValue = "0") val facesVersion: Int = 0,
)

/**
 * Un volto trovato in una foto o in un fotogramma di video. Non è un'immagine: è la posizione
 * nel file e una "impronta" numerica (embedding) che serve a riconoscere la stessa persona.
 */
@Entity(tableName = "face", indices = [Index("mediaId"), Index("personId")])
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    /** Per i video: istante del fotogramma in ms; -1 per le foto. */
    val frameMs: Long,
    /** Riquadro del volto, in frazioni (0..1) dell'immagine. */
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val embedding: ByteArray,
    /** Qualità del volto (grandezza e posa): serve a scegliere la miniatura migliore. */
    val quality: Float,
    val personId: Long?,
    /** true se è stato l'utente ad assegnare (o confermare) questo volto alla persona. */
    val confirmed: Boolean,
    /** Persona a cui l'utente ha detto che questo volto NON appartiene. */
    val rejectedPersonId: Long?,
)

/** Una persona: un gruppo di volti, con o senza nome. */
@Entity(tableName = "person", indices = [Index("name")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    /** Somma delle impronte dei volti (il centro del gruppo si ottiene normalizzandola). */
    val centroid: ByteArray,
    val faceCount: Int,
    val coverFaceId: Long?,
    val hidden: Boolean,
    val updatedAt: Long,
)

/** Persona con la miniatura del volto di copertina, per l'elenco Persone. */
data class PersonWithCover(
    val id: Long,
    val name: String?,
    val faceCount: Int,
    val mediaCount: Int,
    val coverMediaId: Long?,
    val coverIsVideo: Boolean?,
    val coverDateModified: Long?,
    val coverFrameMs: Long?,
    val boxLeft: Float?,
    val boxTop: Float?,
    val boxRight: Float?,
    val boxBottom: Float?,
)

/** Volto con il nome della persona (per il pannello informazioni di una foto). */
data class FaceWithPerson(
    val id: Long,
    val mediaId: Long,
    val frameMs: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val personId: Long?,
    val personName: String?,
    val confirmed: Boolean,
)

/** Etichetta riconosciuta in un file (es. "beach" con confidenza 0.87). */
@Entity(
    tableName = "media_label",
    primaryKeys = ["mediaId", "label"],
    indices = [Index(value = ["label", "confidence"])],
)
data class MediaLabelEntity(
    val mediaId: Long,
    val label: String,
    val confidence: Float,
    /** Quale riconoscitore ha prodotto l'etichetta (per il banco di prova e le correzioni). */
    val recognizer: String,
)

/** Testo ricercabile (etichette in italiano, luoghi, nome file, cartella...). */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=1"])
@Entity(tableName = "media_fts")
data class MediaFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val content: String,
)

/**
 * Contatore che la griglia osserva per aggiornarsi. Lo incrementiamo noi quando serve
 * (file nuovi/cancellati, ogni tanto durante l'analisi), così lo scorrimento resta fluido
 * anche mentre migliaia di foto vengono analizzate.
 */
@Entity(tableName = "ui_version")
data class UiVersionEntity(@PrimaryKey val id: Int = 0, val version: Long)

/** Righe di supporto per le statistiche. */
data class KindStat(val isVideo: Boolean, val count: Int, val bytes: Long)
data class StateStat(val analysisState: Int, val count: Int)
data class ValueCount(val value: String, val count: Int)
data class SyncKey(val id: Long, val dateModified: Long, val sizeBytes: Long, val isFavorite: Boolean)

/** Quanti file ci sono in un mese ("2024-08") tra i risultati: per le intestazioni della griglia. */
data class MonthCount(val month: String, val count: Int)

/** Id e tipo di un file (per selezionarne tanti senza caricarli tutti). */
data class IdKind(val id: Long, val isVideo: Boolean)
