package com.stafir.core.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ---------- Sincronizzazione con MediaStore ----------

    @Query("SELECT id, dateModified, sizeBytes, isFavorite FROM media")
    suspend fun allSyncKeys(): List<SyncKey>

    @Query("SELECT id FROM media")
    suspend fun allIds(): List<Long>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Query(
        """UPDATE media SET displayName = :displayName, relativePath = :relativePath, bucketName = :bucketName,
        mimeType = :mimeType, sizeBytes = :sizeBytes, width = :width, height = :height, durationMs = :durationMs,
        dateTaken = CASE WHEN :dateEstimated AND dateEstimated = 0 AND NOT :resetAnalysis THEN dateTaken ELSE :dateTaken END,
        dateEstimated = CASE WHEN :dateEstimated AND dateEstimated = 0 AND NOT :resetAnalysis THEN 0 ELSE :dateEstimated END,
        dateModified = :dateModified, generation = :generation, isFavorite = :isFavorite,
        source = :source, analysisState = CASE WHEN :resetAnalysis THEN 0 ELSE analysisState END
        WHERE id = :id""",
    )
    suspend fun updateMetadata(
        id: Long, displayName: String, relativePath: String, bucketName: String?, mimeType: String?,
        sizeBytes: Long, width: Int, height: Int, durationMs: Long, dateTaken: Long, dateEstimated: Boolean,
        dateModified: Long, generation: Long, isFavorite: Boolean, source: String, resetAnalysis: Boolean,
    )

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteMedia(ids: List<Long>)

    @Query("DELETE FROM media_label WHERE mediaId IN (:ids)")
    suspend fun deleteLabelsFor(ids: List<Long>)

    @Query("DELETE FROM media_fts WHERE rowid IN (:ids)")
    suspend fun deleteFtsFor(ids: List<Long>)

    @Transaction
    suspend fun deleteCompletely(ids: List<Long>) {
        ids.chunked(500).forEach { chunk ->
            deleteLabelsFor(chunk)
            deleteFtsFor(chunk)
            deleteMedia(chunk)
        }
    }

    // ---------- Analisi ----------

    @Query("SELECT * FROM media WHERE analysisState = 0 AND (:includeVideos OR isVideo = 0) ORDER BY dateTaken DESC LIMIT :limit")
    suspend fun pendingBatch(limit: Int, includeVideos: Boolean): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media WHERE analysisState = 0 AND (:includeVideos OR isVideo = 0)")
    suspend fun pendingCount(includeVideos: Boolean): Int

    @Query(
        """UPDATE media SET analysisState = :state, analysisVersion = :version, analysisError = :error,
        analyzedAt = :at, latitude = :lat, longitude = :lon, city = :city, region = :region, country = :country,
        dateTaken = :dateTaken, dateEstimated = :dateEstimated
        WHERE id = :id""",
    )
    suspend fun updateAnalysis(
        id: Long, state: Int, version: Int, error: String?, at: Long,
        lat: Double?, lon: Double?, city: String?, region: String?, country: String?,
        dateTaken: Long, dateEstimated: Boolean,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabels(labels: List<MediaLabelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: MediaFtsEntity)

    @Transaction
    suspend fun saveAnalysis(
        media: MediaEntity,
        labels: List<MediaLabelEntity>,
        ftsContent: String,
        version: Int,
    ) {
        val ids = listOf(media.id)
        deleteLabelsFor(ids)
        deleteFtsFor(ids)
        if (labels.isNotEmpty()) insertLabels(labels)
        insertFts(MediaFtsEntity(media.id, ftsContent))
        updateAnalysis(
            id = media.id, state = 1, version = version, error = null, at = System.currentTimeMillis(),
            lat = media.latitude, lon = media.longitude, city = media.city, region = media.region, country = media.country,
            dateTaken = media.dateTaken, dateEstimated = media.dateEstimated,
        )
    }

    @Query("UPDATE media SET analysisState = -1, analysisError = :error, analyzedAt = :at WHERE id = :id")
    suspend fun markFailed(id: Long, error: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE media SET analysisState = 0 WHERE analysisState = -1")
    suspend fun retryFailed()

    @Query("UPDATE media SET analysisState = 0")
    suspend fun resetAllAnalysis()

    // ---------- Lettura ----------

    /** Osserva solo [UiVersionEntity]: la griglia si aggiorna quando chiamiamo [bumpUi]. */
    @RawQuery(observedEntities = [UiVersionEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, MediaEntity>

    @Query("INSERT OR REPLACE INTO ui_version (id, version) VALUES (0, :version)")
    suspend fun bumpUi(version: Long = System.currentTimeMillis())

    @RawQuery
    suspend fun countRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun byId(id: Long): MediaEntity?

    @Query("SELECT * FROM media_label WHERE mediaId = :id ORDER BY confidence DESC")
    suspend fun labelsFor(id: Long): List<MediaLabelEntity>

    @Query("SELECT isVideo, COUNT(*) AS count, COALESCE(SUM(sizeBytes), 0) AS bytes FROM media GROUP BY isVideo")
    suspend fun kindStats(): List<KindStat>

    @Query("SELECT analysisState, COUNT(*) AS count FROM media GROUP BY analysisState")
    suspend fun stateStats(): List<StateStat>

    @Query("SELECT * FROM media WHERE analysisState = -1 ORDER BY dateTaken DESC LIMIT 300")
    fun failed(): Flow<List<MediaEntity>>

    @Query("SELECT source AS value, COUNT(*) AS count FROM media GROUP BY source ORDER BY count DESC")
    fun sourceStats(): Flow<List<ValueCount>>

    @Query("SELECT city AS value, COUNT(*) AS count FROM media WHERE city IS NOT NULL GROUP BY city ORDER BY count DESC")
    suspend fun cities(): List<ValueCount>

    @Query("SELECT region AS value, COUNT(*) AS count FROM media WHERE region IS NOT NULL GROUP BY region ORDER BY count DESC")
    suspend fun regions(): List<ValueCount>

    @Query("SELECT country AS value, COUNT(*) AS count FROM media WHERE country IS NOT NULL GROUP BY country ORDER BY count DESC")
    suspend fun countries(): List<ValueCount>

    @Query(
        """SELECT strftime('%Y', dateTaken / 1000, 'unixepoch', 'localtime') AS value, COUNT(*) AS count
        FROM media GROUP BY value ORDER BY value DESC""",
    )
    suspend fun years(): List<ValueCount>

    @Query("SELECT label AS value, COUNT(*) AS count FROM media_label GROUP BY label ORDER BY count DESC LIMIT :limit")
    suspend fun topLabels(limit: Int): List<ValueCount>
}
