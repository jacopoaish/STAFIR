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
            args += it.fromMs; args += it.toMs
        }
        if (q.sources.isNotEmpty()) {
            clauses += "source IN (${q.sources.joinToString(",") { "?" }})"
            args += q.sources.map { it.name }
        }
        if (q.places.isNotEmpty()) {
            val parts = q.places.map {
                args += it.value
                when (it.level) {
                    PlaceLevel.CITY -> "city = ?"
                    PlaceLevel.REGION -> "region = ?"
                    PlaceLevel.COUNTRY -> "country = ?"
                }
            }
            clauses += "(" + parts.joinToString(" OR ") + ")"
        }
        if (q.favoritesOnly) clauses += "isFavorite = 1"
        q.concepts.forEach { concept ->
            if (concept.labels.isEmpty()) return@forEach
            clauses += "id IN (SELECT mediaId FROM media_label WHERE label IN (${concept.labels.joinToString(",") { "?" }}) AND confidence >= ?)"
            args += concept.labels.toList()
            args += minConfidence
        }
        val ftsTerms = q.freeText.map { TextNormalizer.normalize(it).replace(" ", "") }.filter { it.isNotBlank() }
        if (ftsTerms.isNotEmpty()) {
            clauses += "id IN (SELECT rowid FROM media_fts WHERE media_fts MATCH ?)"
            args += ftsTerms.joinToString(" ") { "$it*" }
        }
        val where = if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")
        return Sql(where, args)
    }

    fun select(q: SearchQuery, minConfidence: Float): SimpleSQLiteQuery {
        val w = where(q, minConfidence)
        return SimpleSQLiteQuery("SELECT * FROM media ${w.where} ORDER BY dateTaken DESC, id DESC", w.args.toTypedArray())
    }

    fun count(q: SearchQuery, minConfidence: Float): SimpleSQLiteQuery {
        val w = where(q, minConfidence)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM media ${w.where}", w.args.toTypedArray())
    }
}
