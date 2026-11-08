package com.stafir.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceDao {
    @Insert
    suspend fun insertFace(face: FaceEntity): Long

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Query("SELECT * FROM person")
    suspend fun allPersons(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun person(id: Long): PersonEntity?

    @Query("UPDATE person SET centroid = :centroid, faceCount = :count, coverFaceId = :cover, updatedAt = :at WHERE id = :id")
    suspend fun updatePersonStats(id: Long, centroid: ByteArray, count: Int, cover: Long?, at: Long = System.currentTimeMillis())

    @Query("UPDATE person SET name = :name, updatedAt = :at WHERE id = :id")
    suspend fun rename(id: Long, name: String?, at: Long = System.currentTimeMillis())

    @Query("UPDATE person SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("SELECT * FROM face WHERE mediaId = :mediaId")
    suspend fun facesOf(mediaId: Long): List<FaceEntity>

    @Query("SELECT * FROM face WHERE personId = :personId")
    suspend fun facesOfPerson(personId: Long): List<FaceEntity>

    @Query("SELECT * FROM face WHERE id = :id")
    suspend fun face(id: Long): FaceEntity?

    @Query("DELETE FROM face WHERE mediaId IN (:mediaIds)")
    suspend fun deleteFacesOf(mediaIds: List<Long>)

    @Query("UPDATE face SET personId = :to WHERE personId = :from")
    suspend fun moveFaces(from: Long, to: Long)

    @Query("UPDATE face SET personId = :personId, confirmed = :confirmed, rejectedPersonId = :rejected WHERE id = :faceId")
    suspend fun setFacePerson(faceId: Long, personId: Long?, confirmed: Boolean, rejected: Long?)

    @Query("UPDATE face SET confirmed = 1 WHERE personId = :personId")
    suspend fun confirmAll(personId: Long)

    @Query("UPDATE media SET facesVersion = :version WHERE id = :mediaId")
    suspend fun setFacesVersion(mediaId: Long, version: Int)

    /** Persone da mostrare: almeno [minFaces] volti (le persone con un nome sempre), con il volto di copertina. */
    @Query(
        """SELECT p.id AS id, p.name AS name, p.faceCount AS faceCount,
           (SELECT COUNT(DISTINCT f2.mediaId) FROM face f2 WHERE f2.personId = p.id) AS mediaCount,
           f.mediaId AS coverMediaId, m.isVideo AS coverIsVideo, m.dateModified AS coverDateModified, f.frameMs AS coverFrameMs,
           f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, f.boxBottom AS boxBottom
           FROM person p LEFT JOIN face f ON f.id = p.coverFaceId LEFT JOIN media m ON m.id = f.mediaId
           WHERE p.hidden = 0 AND (p.name IS NOT NULL OR p.faceCount >= :minFaces) AND p.faceCount > 0
           ORDER BY (p.name IS NULL), p.faceCount DESC""",
    )
    fun people(minFaces: Int): Flow<List<PersonWithCover>>

    @Query(
        """SELECT p.id AS id, p.name AS name, p.faceCount AS faceCount,
           (SELECT COUNT(DISTINCT f2.mediaId) FROM face f2 WHERE f2.personId = p.id) AS mediaCount,
           f.mediaId AS coverMediaId, m.isVideo AS coverIsVideo, m.dateModified AS coverDateModified, f.frameMs AS coverFrameMs,
           f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, f.boxBottom AS boxBottom
           FROM person p LEFT JOIN face f ON f.id = p.coverFaceId LEFT JOIN media m ON m.id = f.mediaId WHERE p.id = :id""",
    )
    fun personWithCover(id: Long): Flow<PersonWithCover?>

    @Query("SELECT id, name FROM person WHERE name IS NOT NULL AND hidden = 0 ORDER BY name")
    suspend fun namedPeople(): List<PersonName>

    @Query(
        """SELECT f.id AS id, f.mediaId AS mediaId, f.frameMs AS frameMs, f.boxLeft AS boxLeft, f.boxTop AS boxTop,
           f.boxRight AS boxRight, f.boxBottom AS boxBottom, f.personId AS personId, p.name AS personName, f.confirmed AS confirmed
           FROM face f LEFT JOIN person p ON p.id = f.personId WHERE f.mediaId = :mediaId ORDER BY f.boxLeft""",
    )
    suspend fun facesWithPerson(mediaId: Long): List<FaceWithPerson>

    @Query("SELECT COUNT(*) FROM face")
    fun faceCount(): Flow<Int>

    @Transaction
    suspend fun mergePersons(keep: Long, drop: Long) {
        moveFaces(drop, keep)
        deletePerson(drop)
    }
}

data class PersonName(val id: Long, val name: String)
