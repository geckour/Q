package com.geckour.q.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.SavedQueue
import com.geckour.q.data.db.model.SavedQueueSummary
import com.geckour.q.data.db.model.SavedQueueTrack
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
                "(select ifnull(sum(track.duration), 0) from savedQueueTrack " +
                "inner join track on track.id = savedQueueTrack.trackId " +
                "where savedQueueTrack.savedQueueId = savedQueue.id) as totalDuration " +
                "from savedQueue order by savedQueue.updatedAt desc"
    )
    fun getAllAsPagingSource(): PagingSource<Int, SavedQueueSummary>

    @Query("select count(*) from savedQueue")
    fun countAsFlow(): Flow<Int>

    @Query(
        "select coalesce(track.artworkUriString, album.artworkUriString) as artworkUriString " +
                "from savedQueueTrack " +
                "inner join track on track.id = savedQueueTrack.trackId " +
                "left join album on album.id = track.albumId " +
                "where savedQueueTrack.savedQueueId = :savedQueueId " +
                "and coalesce(track.artworkUriString, album.artworkUriString) is not null " +
                "order by savedQueueTrack.sortIndex " +
                "limit :limit"
    )
    suspend fun getArtworkUriStrings(
        savedQueueId: Long,
        limit: Int = -1,
    ): List<String>

    @Transaction
    @Query(
        "select track.* from savedQueueTrack " +
                "inner join track on track.id = savedQueueTrack.trackId " +
                "where savedQueueTrack.savedQueueId = :savedQueueId " +
                "order by savedQueueTrack.sortIndex " +
                "limit :limit"
    )
    suspend fun getTracks(savedQueueId: Long, limit: Int = -1): List<JoinedTrack>

    @Transaction
    @Query(
        "select track.* from savedQueueTrack " +
                "inner join track on track.id = savedQueueTrack.trackId " +
                "where savedQueueTrack.savedQueueId = :savedQueueId " +
                "order by savedQueueTrack.sortIndex " +
                "limit :limit"
    )
    fun getTracksAsFlow(savedQueueId: Long, limit: Int = -1): Flow<List<JoinedTrack>>

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
        trackIds: List<Long>,
        savedQueueId: Long? = null,
        title: String,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        if (trackIds.isEmpty()) return null

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
            trackIds.mapIndexed { index, trackId ->
                SavedQueueTrack(
                    id = 0,
                    savedQueueId = id,
                    trackId = trackId,
                    sortIndex = index,
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
