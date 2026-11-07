package com.stafir.core.search

import androidx.sqlite.db.SimpleSQLiteQuery
import com.stafir.domain.MediaTypeFilter
import com.stafir.domain.PlaceLevel
import com.stafir.domain.SearchQuery
import com.stafir.domain.TextNormalizer

/** Trasforma una [SearchQuery] in SQL sul database locale. */
object SearchSqlBuilder {

    data class Sql(val where: String, val args: List<Any>)

    fun where(q: SearchQuery, minConfidence: Float): Sql {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        when (q.mediaType) {
            MediaTypeFilter.PHOTOS -> clauses += "isVideo = 0"
            MediaTypeFilter.VIDEOS -> clauses += "isVideo = 1"
            MediaTypeFilter.ANY -> {}
        }
        q.dateRange?.let {
            clauses += "dateTaken >= ? AND dateTaken < ?"
            args.add(it.fromMs); args.add(it.toMs)
        }
        if (q.sources.isNotEmpty()) {
            clauses += "source IN (${q.sources.joinToString(",") { "?" }})"
            args.addAll(q.sources.map { it.name })
        }
        if (q.places.isNotEmpty()) {
            val parts = q.places.map {
                args.add(it.value)
                when (it.level) {
                    PlaceLevel.CITY -> "city = ?"
                    PlaceLevel.REGION -> "region = ?"
                    PlaceLevel.COUNTRY -> "country = ?"
                }
            }
            clauses += "(" + parts.joinToString(" OR ") + ")"
        }
        if (q.favoritesOnly) clauses += "isFavorite = 1"
        q.people.forEach { person ->
            clauses += "id IN (SELECT mediaId FROM face WHERE personId = ?)"
            args.add(person.id)
        }
        q.concepts.forEach { concept ->
            if (concept.labels.isEmpty()) return@forEach
            clauses += "id IN (SELECT mediaId FROM media_label WHERE label IN (${concept.labels.joinToString(",") { "?" }}) AND confidence >= ?)"
            args.addAll(concept.labels)
            args.add(minConfidence)
        }
        val ftsTerms = q.freeText.map { TextNormalizer.normalize(it).replace(" ", "") }.filter { it.isNotBlank() }
        if (ftsTerms.isNotEmpty()) {
            clauses += "id IN (SELECT rowid FROM media_fts WHERE media_fts MATCH ?)"
            args.add(ftsTerms.joinToString(" ") { "$it*" })
        }
        val where = if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")
        return Sql(where, args)
    }

    fun select(q: SearchQuery, minConfidence: Float): SimpleSQLiteQuery {
        val w = where(q, minConfidence)
        return SimpleSQLiteQuery("SELECT * FROM media ${w.where} ORDER BY dateTaken DESC, id DESC", w.args.toTypedArray())
    }

    /** Mese di scatto ("2024-08") nel fuso del telefono, come lo mostra la griglia. */
    const val MONTH = "strftime('%Y-%m', dateTaken / 1000, 'unixepoch', 'localtime')"

    /** Quanti risultati per mese, nello stesso ordine della griglia (dal più recente). */
    fun months(q: SearchQuery, minConfidence: Float): SimpleSQLiteQuery {
        val w = where(q, minConfidence)
        return SimpleSQLiteQuery(
            "SELECT $MONTH AS month, COUNT(*) AS count FROM media ${w.where} GROUP BY month ORDER BY month DESC",
            w.args.toTypedArray(),
        )
    }

    /** Id e tipo di tutti i risultati, o solo di quelli di un mese ("2024-08"). */
    fun ids(q: SearchQuery, minConfidence: Float, month: String? = null): SimpleSQLiteQuery {
        val w = where(q, minConfidence)
        if (month == null) return SimpleSQLiteQuery("SELECT id, isVideo FROM media ${w.where}", w.args.toTypedArray())
        val cond = if (w.where.isEmpty()) "WHERE $MONTH = ?" else "${w.where} AND $MONTH = ?"
        return SimpleSQLiteQuery("SELECT id, isVideo FROM media $cond", (w.args + month).toTypedArray())
    }

    fun count(q: SearchQuery, minConfidence: Float): SimpleSQLiteQuery {
        val w = where(q, minConfidence)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM media ${w.where}", w.args.toTypedArray())
    }
}
