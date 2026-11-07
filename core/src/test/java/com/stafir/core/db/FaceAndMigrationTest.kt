package com.stafir.core.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.stafir.core.search.SearchSqlBuilder
import com.stafir.domain.FaceMath
import com.stafir.domain.ItalianQueryParser
import com.stafir.domain.PersonDirectory
import com.stafir.domain.PersonFilter
import com.stafir.domain.PlaceDirectory
import com.stafir.domain.SearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaceAndMigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun media(id: Long) = MediaEntity(
        id = id, isVideo = false, displayName = "IMG_$id.jpg", relativePath = "DCIM/Camera/", bucketName = "Camera",
        mimeType = "image/jpeg", sizeBytes = 1000, width = 100, height = 100, durationMs = 0, dateTaken = id * 1000,
        dateModified = id, generation = id, isFavorite = false, source = "CAMERA", analysisState = 1,
    )

    private fun vec(seed: Int) = FaceMath.normalize(FloatArray(128) { ((it * 31 + seed * 17) % 13 - 6).toFloat() })

    @Test fun peopleSearchAndMerge() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, StafirDatabase::class.java).allowMainThreadQueries().build()
        val md = db.mediaDao(); val fd = db.faceDao()
        md.insertAll(listOf(media(1), media(2), media(3)))
        val marco = fd.insertPerson(PersonEntity(name = "Marco", centroid = FaceMath.toBytes(vec(1)), faceCount = 2, coverFaceId = null, hidden = false, updatedAt = 0))
        val anna = fd.insertPerson(PersonEntity(name = null, centroid = FaceMath.toBytes(vec(2)), faceCount = 1, coverFaceId = null, hidden = false, updatedAt = 0))
        fun face(m: Long, p: Long?) = FaceEntity(mediaId = m, frameMs = -1, boxLeft = .1f, boxTop = .1f, boxRight = .3f, boxBottom = .3f,
            embedding = FaceMath.toBytes(vec(p?.toInt() ?: 0)), quality = 1f, personId = p, confirmed = false, rejectedPersonId = null)
        val f1 = fd.insertFace(face(1, marco)); fd.insertFace(face(2, marco)); fd.insertFace(face(2, anna)); fd.insertFace(face(3, anna))
        fd.updatePersonStats(marco, FaceMath.toBytes(vec(1)), 2, f1)

        val parser = ItalianQueryParser(PlaceDirectory.EMPTY, ZoneId.of("Europe/Rome"), PersonDirectory.of(listOf(PersonFilter(marco, "Marco"))))
        assertEquals(2, md.countRaw(SearchSqlBuilder.count(parser.parse("Marco"), .6f)))
        val both = SearchQuery(people = listOf(PersonFilter(marco, "Marco"), PersonFilter(anna, "Anna")))
        assertEquals(1, md.countRaw(SearchSqlBuilder.count(both, .6f)))

        val people = fd.people(minFaces = 2).first()
        assertEquals("Marco", people.first().name)
        assertEquals(1L, people.first().coverMediaId)
        assertEquals(2, people.first().mediaCount)
        assertEquals(listOf("Marco"), fd.namedPeople().map { it.name })
        assertEquals(listOf("Marco", null), fd.facesWithPerson(2).map { it.personName }.sortedBy { it ?: "~" })

        fd.mergePersons(marco, anna)
        assertNull(fd.person(anna))
        assertEquals(4, fd.facesOfPerson(marco).size)
        assertEquals(3, md.countRaw(SearchSqlBuilder.count(parser.parse("Marco"), .6f)))

        md.deleteCompletely(listOf(3L))
        assertEquals(3, fd.facesOfPerson(marco).size)
        db.close()
    }

    @Test fun pendingIncludesFaceBackfill() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, StafirDatabase::class.java).allowMainThreadQueries().build()
        val md = db.mediaDao()
        md.insertAll(listOf(media(1), media(2), media(3).copy(analysisState = 0)))
        assertEquals(1, md.pendingCount(includeVideos = true))
        assertEquals(3, md.pendingCount(includeVideos = true, facesVersion = 1))
        assertEquals(2, md.facesPendingCount(1, true))
        db.faceDao().setFacesVersion(1, 1)
        assertEquals(2, md.pendingCount(includeVideos = true, facesVersion = 1))
        assertEquals(setOf(2L, 3L), md.pendingBatch(10, true, 1).map { it.id }.toSet())
        db.close()
    }

    /** Un database della versione 2 (Fase 1) viene aggiornato senza perdere i file già analizzati. */
    @Test fun migrationFrom2KeepsData() {
        val name = "migr_test.db"
        ctx.deleteDatabase(name)
        // 1) crea il database alla versione attuale e lo riporta "indietro" alla struttura della versione 2
        Room.databaseBuilder(ctx, StafirDatabase::class.java, name).allowMainThreadQueries().build().apply {
            runBlocking { mediaDao().insertAll(listOf(media(1), media(2))) }
            val w: SupportSQLiteDatabase = openHelper.writableDatabase
            w.execSQL("DROP TABLE face"); w.execSQL("DROP TABLE person")
            // toglie la colonna facesVersion ricreando la tabella (DROP COLUMN non c'è nelle versioni di SQLite più vecchie)
            fun strings(sql: String) = w.query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            val create = strings("SELECT sql FROM sqlite_master WHERE type='table' AND name='media'").single()
                .replace(Regex(",\\s*`facesVersion`[^,)]*"), "")
            val indices = strings("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='media' AND sql IS NOT NULL")
            val cols = w.query("PRAGMA table_info(media)").use { c -> buildList { while (c.moveToNext()) add(c.getString(1)) } }
                .filter { it != "facesVersion" }.joinToString(", ") { "`$it`" }
            assertTrue("facesVersion" !in create)
            w.execSQL("ALTER TABLE media RENAME TO media_old")
            w.execSQL(create)
            w.execSQL("INSERT INTO media ($cols) SELECT $cols FROM media_old")
            w.execSQL("DROP TABLE media_old")
            indices.forEach { w.execSQL(it) }
            w.execSQL("PRAGMA user_version = 2")
            close()
        }
        // 2) riapre con la migrazione: Room verifica che la struttura risultante sia identica a quella attesa
        val db = Room.databaseBuilder(ctx, StafirDatabase::class.java, name).addMigrations(MIGRATION_2_3).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(2, db.mediaDao().count())
            assertEquals(0, db.mediaDao().byId(1)!!.facesVersion)
            assertTrue(db.faceDao().allPersons().isEmpty())
        }
        db.close()
        ctx.deleteDatabase(name)
    }
}
