package com.geckour.q.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.JoinedSavedQueueTrack
import com.geckour.q.data.db.model.SavedQueue
import com.geckour.q.data.db.model.SavedQueueSummary
import com.geckour.q.data.db.model.SavedQueueTrack
import com.geckour.q.data.db.model.TrackRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface SavedQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQueue(savedQueue: SavedQueue): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQueueTracks(savedQueueTracks: List<SavedQueueTrack>)

    @Query("select * from savedQueue where id = :id")
    suspend fun get(id: Long): SavedQueue?

    @Query(
        "select ifnull((select seq from sqlite_sequence where name = 'SavedQueue'), 0) + 1"
    )
    suspend fun getNextId(): Long

    fun getNextIdAsFlow(): Flow<Long> = countAsFlow().map { getNextId() }

    @Query(
        "select savedQueue.*, " +
                "(select count(*) from savedQueueTrack " +
                "where savedQueueTrack.savedQueueId = savedQueue.id) as trackCount, " +
                "(select ifnull(sum(coalesce(track.duration, spotifytrack.duration)), 0) " +
                "from savedQueueTrack " +
                "left join track on track.id = savedQueueTrack.trackId " +
                "left join spotifytrack on spotifytrack.uri = savedQueueTrack.spotifyUri " +
                "where savedQueueTrack.savedQueueId = savedQueue.id) as totalDuration " +
                "from savedQueue order by savedQueue.updatedAt desc"
    )
    fun getAllAsPagingSource(): PagingSource<Int, SavedQueueSummary>

    @Query("select count(*) from savedQueue")
    fun countAsFlow(): Flow<Int>

    @Query(
        "select coalesce(track.artworkUriString, album.artworkUriString, spotifytrack.artworkUrl) " +
                "as artworkUriString " +
                "from savedQueueTrack " +
                "left join track on track.id = savedQueueTrack.trackId " +
                "left join album on album.id = track.albumId " +
                "left join spotifytrack on spotifytrack.uri = savedQueueTrack.spotifyUri " +
                "where savedQueueTrack.savedQueueId = :savedQueueId " +
                "and (track.id is not null or spotifytrack.uri is not null) " +
                "order by savedQueueTrack.sortIndex " +
                "limit :limit"
    )
    suspend fun getArtworkUriStrings(
        savedQueueId: Long,
        limit: Int = -1,
    ): List<String?>

    @Transaction
    @Query(
        "select * from savedQueueTrack " +
                "where savedQueueId = :savedQueueId " +
                "and (trackId in (select id from track) " +
                "or spotifyUri in (select uri from spotifytrack)) " +
                "order by sortIndex " +
                "limit :limit"
    )
    suspend fun getTracks(savedQueueId: Long, limit: Int = -1): List<JoinedSavedQueueTrack>

    @Transaction
    @Query(
        "select * from savedQueueTrack " +
                "where savedQueueId = :savedQueueId " +
                "and (trackId in (select id from track) " +
                "or spotifyUri in (select uri from spotifytrack)) " +
                "order by sortIndex " +
                "limit :limit"
    )
    fun getTracksAsFlow(savedQueueId: Long, limit: Int = -1): Flow<List<JoinedSavedQueueTrack>>

    @Query("update savedQueue set title = :title, updatedAt = :now where id = :id")
    suspend fun updateTitle(id: Long, title: String?, now: Long = System.currentTimeMillis()): Int

    @Query("delete from savedQueueTrack where savedQueueId = :savedQueueId")
    suspend fun deleteSavedQueueTracks(savedQueueId: Long)

    @Query("delete from savedQueueTrack where trackId in (:trackIds)")
    suspend fun deleteSavedQueueTracksByTrackIds(trackIds: List<Long>)

    @Query("delete from savedQueue where id = :id")
    suspend fun deleteSavedQueue(id: Long): Int

    @Query("delete from savedQueue where id not in (select savedQueueId from savedQueueTrack)")
    suspend fun deleteEmptySavedQueues(): Int

    @Transaction
    suspend fun save(
        savedQueueId: Long? = null,
        trackRefs: List<TrackRef>,
        title: String,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        if (trackRefs.isEmpty()) return null

        val existing = savedQueueId?.let { get(it) }
        val id = insertSavedQueue(
            SavedQueue(
                id = existing?.id ?: 0,
                title = title,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )

        deleteSavedQueueTracks(id)
        insertSavedQueueTracks(
            trackRefs.mapIndexed { index, trackRef ->
                SavedQueueTrack(
                    id = 0,
                    savedQueueId = id,
                    trackId = trackRef.trackId,
                    sortIndex = index,
                    spotifyUri = trackRef.spotifyUri,
                )
            }
        )

        return id
    }

    @Transaction
    suspend fun delete(savedQueueId: Long) {
        deleteSavedQueueTracks(savedQueueId)
        deleteSavedQueue(savedQueueId)
    }

    @Transaction
    suspend fun deleteByTrackIds(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return

        deleteSavedQueueTracksByTrackIds(trackIds)
        deleteEmptySavedQueues()
    }
}
