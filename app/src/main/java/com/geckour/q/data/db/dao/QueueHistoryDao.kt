package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.CoQueuedTrack
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.QueueHistory
import com.geckour.q.data.db.model.QueueHistoryTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueHistoryDao {

    companion object {

        const val DEFAULT_KEEP_HISTORY_COUNT = 500
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueHistory(queueHistory: QueueHistory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueueHistoryTracks(queueHistoryTracks: List<QueueHistoryTrack>)

    @Query("select id from queueHistory order by id desc limit 1")
    suspend fun getLatestQueueHistoryId(): Long?

    @Query("select trackId from queueHistoryTrack where queueHistoryId = :queueHistoryId")
    suspend fun getTrackIdsByQueueHistoryId(queueHistoryId: Long): List<Long>

    @Transaction
    suspend fun saveQueue(
        trackIds: List<Long>,
        createdAt: Long = System.currentTimeMillis(),
        keepHistoryCount: Int = DEFAULT_KEEP_HISTORY_COUNT,
    ) {
        val distinctTrackIds = trackIds.distinct()
        if (distinctTrackIds.isEmpty()) return

        val latestTrackIds = getLatestQueueHistoryId()
            ?.let { getTrackIdsByQueueHistoryId(it).toSet() }
        if (latestTrackIds == distinctTrackIds.toSet()) return

        val queueHistoryId = insertQueueHistory(
            QueueHistory(id = 0, createdAt = createdAt)
        )
        insertQueueHistoryTracks(
            distinctTrackIds.map {
                QueueHistoryTrack(id = 0, queueHistoryId = queueHistoryId, trackId = it)
            }
        )

        deleteOldQueueHistories(keepHistoryCount)
    }

    @Transaction
    @Query(
        "select track.* from queueHistoryTrack as target " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = target.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where target.trackId = :trackId and other.trackId != :trackId " +
                "group by other.trackId " +
                "order by count(*) desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    suspend fun getCoQueuedTracks(trackId: Long, limit: Int = -1): List<JoinedTrack>

    @Transaction
    @Query(
        "select track.* from queueHistoryTrack as target " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = target.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where target.trackId = :trackId and other.trackId != :trackId " +
                "group by other.trackId " +
                "order by count(*) desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    fun getCoQueuedTracksFlow(trackId: Long, limit: Int = -1): Flow<List<JoinedTrack>>

    @Transaction
    @Query(
        "select track.*, count(*) as coQueuedCount from queueHistoryTrack as target " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = target.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where target.trackId = :trackId and other.trackId != :trackId " +
                "group by other.trackId " +
                "order by coQueuedCount desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    suspend fun getCoQueuedTrackCounts(trackId: Long, limit: Int = -1): List<CoQueuedTrack>

    @Transaction
    @Query(
        "select track.*, count(*) as coQueuedCount from queueHistoryTrack as target " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = target.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where target.trackId = :trackId and other.trackId != :trackId " +
                "group by other.trackId " +
                "order by coQueuedCount desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    fun getCoQueuedTrackCountsFlow(trackId: Long, limit: Int = -1): Flow<List<CoQueuedTrack>>

    @Query(
        "delete from queueHistoryTrack " +
                "where queueHistoryId not in (select id from queueHistory order by id desc limit :keepHistoryCount)"
    )
    suspend fun deleteOldQueueHistoryTracks(keepHistoryCount: Int)

    @Query("delete from queueHistory where id not in (select id from queueHistory order by id desc limit :keepHistoryCount)")
    suspend fun deleteOldQueueHistoryRows(keepHistoryCount: Int)

    @Transaction
    suspend fun deleteOldQueueHistories(keepHistoryCount: Int = DEFAULT_KEEP_HISTORY_COUNT) {
        deleteOldQueueHistoryTracks(keepHistoryCount)
        deleteOldQueueHistoryRows(keepHistoryCount)
    }

    @Query("delete from queueHistoryTrack where trackId in (:trackIds)")
    suspend fun deleteQueueHistoryTracksByTrackIds(trackIds: List<Long>)

    @Query("delete from queueHistory where id not in (select queueHistoryId from queueHistoryTrack)")
    suspend fun deleteEmptyQueueHistories()

    @Transaction
    suspend fun deleteByTrackIds(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return

        deleteQueueHistoryTracksByTrackIds(trackIds)
        deleteEmptyQueueHistories()
    }

    @Query("delete from queueHistoryTrack")
    suspend fun deleteAllQueueHistoryTracks()

    @Query("delete from queueHistory")
    suspend fun deleteAllQueueHistoryRows()

    @Transaction
    suspend fun clear() {
        deleteAllQueueHistoryTracks()
        deleteAllQueueHistoryRows()
    }
}
