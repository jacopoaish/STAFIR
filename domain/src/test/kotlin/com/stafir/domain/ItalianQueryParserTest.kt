package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ItalianQueryParserTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 11, 6)
    private val places = PlaceDirectory.of(
        listOf(
            PlaceFilter(PlaceLevel.CITY, "Roma"),
            PlaceFilter(PlaceLevel.CITY, "Reggio Emilia"),
            PlaceFilter(PlaceLevel.REGION, "Sicilia"),
            PlaceFilter(PlaceLevel.COUNTRY, "Italia"),
        ),
    )
    private val parser = ItalianQueryParser(places, zone) { today }

    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun receiptsInRomeIn2024() {
        val q = parser.parse("scontrini di ristoranti a Roma nel 2024")
        assertEquals(listOf("documents"), q.concepts.map { it.id })
        assertEquals(listOf(PlaceFilter(PlaceLevel.CITY, "Roma")), q.places)
        assertEquals(ms(2024, 1, 1), q.dateRange!!.fromMs)
        assertEquals(ms(2025, 1, 1), q.dateRange!!.toMs)
        assertEquals(listOf("ristoranti"), q.freeText)
    }

    @Test fun personAtTheSea() {
        val q = parser.parse("Marco al mare")
        assertEquals(listOf("sea"), q.concepts.map { it.id })
        assertEquals(listOf("marco"), q.freeText)
    }

    @Test fun videoInMountainsWithSnow() {
        val q = parser.parse("video in montagna con la neve")
        assertEquals(MediaTypeFilter.VIDEOS, q.mediaType)
        assertEquals(setOf("mountain", "snow"), q.concepts.map { it.id }.toSet())
        assertTrue(q.freeText.isEmpty())
    }

    @Test fun commandIsRecognizedAndSelectionParsed() {
        val q = parser.parse("crea un carosello con le 10 foto migliori della Sicilia")
        assertNotNull(q.command)
        assertEquals("crea", q.command!!.verb)
        assertEquals(MediaTypeFilter.PHOTOS, q.mediaType)
        assertEquals(listOf(PlaceFilter(PlaceLevel.REGION, "Sicilia")), q.places)
    }

    @Test fun monthWithoutYearIsMostRecent() {
        val q = parser.parse("foto di dicembre")
        assertEquals(ms(2025, 12, 1), q.dateRange!!.fromMs)
        val q2 = parser.parse("foto di agosto")
        assertEquals(ms(2026, 8, 1), q2.dateRange!!.fromMs)
    }

    @Test fun monthWithYear() {
        val q = parser.parse("agosto 2023 spiaggia")
        assertEquals(ms(2023, 8, 1), q.dateRange!!.fromMs)
        assertEquals(ms(2023, 9, 1), q.dateRange!!.toMs)
        assertEquals("sea", q.concepts.single().id)
    }

    @Test fun relativeDates() {
        assertEquals(ms(2026, 11, 5), parser.parse("ieri").dateRange!!.fromMs)
        assertEquals(ms(2025, 1, 1), parser.parse("l'anno scorso").dateRange!!.fromMs)
        assertEquals(ms(2026, 10, 1), parser.parse("mese scorso").dateRange!!.fromMs)
        assertEquals(ms(2026, 10, 8), parser.parse("negli ultimi 30 giorni").dateRange!!.fromMs)
        assertEquals(ms(2020, 1, 1), parser.parse("dal 2020 al 2022").dateRange!!.fromMs)
        assertEquals(ms(2023, 1, 1), parser.parse("dal 2020 al 2022").dateRange!!.toMs)
    }

    @Test fun sourcesAndFavorites() {
        val q = parser.parse("screenshot preferiti")
        assertEquals(setOf(Source.SCREENSHOT), q.sources)
        assertTrue(q.favoritesOnly)
        assertEquals(setOf(Source.WHATSAPP), parser.parse("foto sfocate di whatsapp").sources)
    }

    @Test fun multiWordPlace() {
        val q = parser.parse("tramonto a reggio emilia")
        assertEquals(listOf(PlaceFilter(PlaceLevel.CITY, "Reggio Emilia")), q.places)
        assertEquals("sunset", q.concepts.single().id)
    }

    @Test fun accentsAndCaseAreIgnored() {
        val q = parser.parse("CITTÀ di notte")
        assertEquals(setOf("city", "night"), q.concepts.map { it.id }.toSet())
    }

    @Test fun pluralsAreUnderstood() {
        assertEquals("dog", parser.parse("cani").concepts.single().id)
        assertEquals("cat", parser.parse("gatti").concepts.single().id)
        assertEquals("mountain", parser.parse("montagne").concepts.single().id)
    }

    @Test fun emptyQuery() {
        assertTrue(parser.parse("   ").isEmpty)
        assertNull(parser.parse("").command)
    }

    @Test fun photosAndVideosMeansAny() {
        assertEquals(MediaTypeFilter.ANY, parser.parse("foto e video del mare").mediaType)
    }
}
