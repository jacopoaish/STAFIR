package com.stafir.core.search

import com.stafir.domain.ItalianQueryParser
import com.stafir.domain.PlaceDirectory
import com.stafir.domain.PlaceFilter
import com.stafir.domain.PlaceLevel
import com.stafir.domain.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SearchSqlBuilderTest {
    private val parser = ItalianQueryParser(
        PlaceDirectory.of(listOf(PlaceFilter(PlaceLevel.CITY, "Roma"))),
        ZoneId.of("Europe/Rome"),
    ) { LocalDate.of(2026, 11, 6) }

    @Test fun emptyQueryHasNoWhere() {
        assertEquals("", SearchSqlBuilder.where(SearchQuery(), 0.5f).where)
    }

    @Test fun fullQuery() {
        val sql = SearchSqlBuilder.where(parser.parse("video al mare a Roma nel 2024 whatsapp marco"), 0.6f)
        assertTrue(sql.where.contains("isVideo = 1"))
        assertTrue(sql.where.contains("dateTaken >= ?"))
        assertTrue(sql.where.contains("source IN (?)"))
        assertTrue(sql.where.contains("city = ?"))
        assertTrue(sql.where.contains("media_label"))
        assertTrue(sql.where.contains("MATCH ?"))
        assertEquals("marco*", sql.args.last())
        assertTrue("Roma" in sql.args)
        assertTrue("WHATSAPP" in sql.args)
    }
}
