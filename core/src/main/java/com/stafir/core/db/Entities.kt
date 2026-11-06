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
