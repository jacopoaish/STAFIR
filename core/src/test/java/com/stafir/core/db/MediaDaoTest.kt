package com.stafir.core.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stafir.core.search.SearchSqlBuilder
import com.stafir.domain.ItalianQueryParser
import com.stafir.domain.PlaceDirectory
import com.stafir.domain.PlaceFilter
import com.stafir.domain.PlaceLevel
import com.stafir.domain.SearchQuery
import com.stafir.domain.Source
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Verifica le query vere sul database SQLite (ricerca, testo libero, paginazione, cancellazioni). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaDaoTest {
    private lateinit var db: StafirDatabase
    private lateinit var dao: MediaDao
    private val zone = ZoneId.of("Europe/Rome")
    private val parser = ItalianQueryParser(
        PlaceDirectory.of(listOf(PlaceFilter(PlaceLevel.CITY, "Roma"), PlaceFilter(PlaceLevel.REGION, "Sicilia"))),
        zone,
    ) { LocalDate.of(2026, 11, 6) }

    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun media(id: Long, date: Long, video: Boolean = false, source: Source = Source.CAMERA, city: String? = null, region: String? = null) =
        MediaEntity(
            id = id, isVideo = video, displayName = "IMG_$id.jpg", relativePath = "DCIM/Camera/", bucketName = "Camera",
            mimeType = if (video) "video/mp4" else "image/jpeg", sizeBytes = 1000L * id, width = 4000, height = 3000,
            durationMs = if (video) 5000 else 0, dateTaken = date, dateModified = date / 1000, generation = id,
            isFavorite = id % 2L == 0L, source = source.name, city = city, region = region, country = "Italia",
        )

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, StafirDatabase::class.java).allowMainThreadQueries().build()
        dao = db.mediaDao()
        runBlocking {
            dao.insertAll(
                listOf(
                    media(1, ms(2024, 8, 10), city = "Roma"),
                    media(2, ms(2024, 8, 11), video = true, region = "Sicilia"),
                    media(3, ms(2023, 1, 5), source = Source.WHATSAPP),
                    media(4, ms(2025, 3, 1), source = Source.SCREENSHOT),
                ),
            )
            dao.saveAnalysis(media(1, ms(2024, 8, 10), city = "Roma"), listOf(MediaLabelEntity(1, "beach", 0.9f, "t"), MediaLabelEntity(1, "sky", 0.7f, "t")), "spiaggia beach cielo Roma marco", 1)
            dao.saveAnalysis(media(2, ms(2024, 8, 11), video = true, region = "Sicilia"), listOf(MediaLabelEntity(2, "mountain", 0.8f, "t"), MediaLabelEntity(2, "snow", 0.65f, "t")), "montagna neve Sicilia", 1)
            dao.saveAnalysis(media(3, ms(2023, 1, 5), source = Source.WHATSAPP), listOf(MediaLabelEntity(3, "receipt", 0.85f, "t")), "scontrino ristorante", 1)
        }
    }

    @After fun tearDown() = db.close()

    private fun count(text: String, minConf: Float = 0.6f) = runBlocking {
        dao.countRaw(SearchSqlBuilder.count(parser.parse(text), minConf))
    }

    @Test fun searchByConceptPlaceDateTypeAndSource() {
        assertEquals(4, runBlocking { dao.countRaw(SearchSqlBuilder.count(SearchQuery(), 0.6f)) })
        assertEquals(1, count("mare"))
        assertEquals(1, count("mare a Roma"))
        assertEquals(0, count("mare in Sicilia"))
        assertEquals(1, count("video in montagna con la neve"))
        assertEquals(0, count("foto in montagna"))
        assertEquals(2, count("agosto 2024"))
        assertEquals(1, count("whatsapp"))
        assertEquals(1, count("screenshot"))
        assertEquals(1, count("scontrini"))
        assertEquals(2, count("preferiti"))
    }

    @Test fun thresholdFiltersWeakLabels() {
        assertEquals(1, count("neve", 0.6f))
        assertEquals(0, count("neve", 0.7f))
    }

    @Test fun freeTextUsesFullTextIndexWithPrefix() {
        assertEquals(1, count("marco"))
        assertEquals(1, count("mar"))
        assertEquals(1, count("ristorante"))
        assertEquals(0, count("giulia"))
    }

    @Test fun pendingAndDeletion() = runBlocking {
        assertEquals(1, dao.pendingCount(includeVideos = true))
        assertEquals(4L, dao.pendingBatch(10, includeVideos = true).single().id)
        dao.markFailed(4, "File danneggiato")
        assertEquals(0, dao.pendingCount(includeVideos = true))
        dao.deleteCompletely(listOf(1L))
        assertNull(dao.byId(1))
        assertTrue(dao.labelsFor(1).isEmpty())
        assertEquals(0, count("marco"))
        dao.retryFailed()
        assertEquals(1, dao.pendingCount(includeVideos = true))
    }

    @Test fun metadataUpdateResetsAnalysisOnlyWhenContentChanged() = runBlocking {
        val m = dao.byId(1)!!
        dao.updateMetadata(m.id, m.displayName, m.relativePath, m.bucketName, m.mimeType, m.sizeBytes, m.width, m.height,
            m.durationMs, m.dateTaken, m.dateModified, m.generation, true, m.source, resetAnalysis = false)
        assertEquals(1, dao.byId(1)!!.analysisState)
        dao.updateMetadata(m.id, m.displayName, m.relativePath, m.bucketName, m.mimeType, m.sizeBytes + 1, m.width, m.height,
            m.durationMs, m.dateTaken, m.dateModified + 10, m.generation, true, m.source, resetAnalysis = true)
        assertEquals(0, dao.byId(1)!!.analysisState)
    }

    @Test fun pagingReturnsNewestFirst() = runBlocking {
        dao.bumpUi()
        val source = dao.pagingSource(SearchSqlBuilder.select(SearchQuery(), 0.6f))
        val page = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = true))
        page as PagingSource.LoadResult.Page
        assertEquals(listOf(4L, 2L, 1L, 3L), page.data.map { it.id })
    }

    @Test fun statsQueries() = runBlocking {
        val kinds = dao.kindStats()
        assertEquals(3, kinds.first { !it.isVideo }.count)
        assertEquals(1, kinds.first { it.isVideo }.count)
        assertEquals(listOf("Roma"), dao.cities().map { it.value })
        assertEquals(listOf("Sicilia"), dao.regions().map { it.value })
        assertTrue(dao.years().map { it.value }.containsAll(listOf("2023", "2024", "2025")))
        assertEquals(3, dao.stateStats().first { it.analysisState == 1 }.count)
    }
}
