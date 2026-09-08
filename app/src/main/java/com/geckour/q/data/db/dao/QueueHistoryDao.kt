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

        const val DEFAULT_PICK_DENOMINATOR = 3

        const val DEFAULT_MAX_TOTAL_DURATION = 4 * 60 * 60 * 1000L

        const val DEFAULT_ADJACENT_WINDOW = 30 * 60 * 1000L

        const val DEFAULT_FAVORITE_SCORE_FACTOR = 1.5
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
        "select track.* from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where origin.trackId = :originTrackId and other.trackId != :originTrackId " +
                "group by other.trackId " +
                "order by count(*) * 1.0 / (select count(*) from queueHistoryTrack where trackId = other.trackId) desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    suspend fun getCoQueuedTracks(originTrackId: Long, limit: Int = -1): List<JoinedTrack>

    @Transaction
    @Query(
        "select track.* from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where origin.trackId = :originTrackId and other.trackId != :originTrackId " +
                "group by other.trackId " +
                "order by count(*) * 1.0 / (select count(*) from queueHistoryTrack where trackId = other.trackId) desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    fun getCoQueuedTracksFlow(originTrackId: Long, limit: Int = -1): Flow<List<JoinedTrack>>

    @Transaction
    @Query(
        "select track.*, count(*) as coQueuedCount from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where origin.trackId = :originTrackId and other.trackId != :originTrackId " +
                "group by other.trackId " +
                "order by count(*) * 1.0 / (select count(*) from queueHistoryTrack where trackId = other.trackId) desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    suspend fun getCoQueuedTrackCounts(originTrackId: Long, limit: Int = -1): List<CoQueuedTrack>

    @Transaction
    @Query(
        "select track.*, count(*) as coQueuedCount from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "inner join track on track.id = other.trackId " +
                "where origin.trackId = :originTrackId and other.trackId != :originTrackId " +
                "group by other.trackId " +
                "order by count(*) * 1.0 / (select count(*) from queueHistoryTrack where trackId = other.trackId) desc, max(other.queueHistoryId) desc " +
                "limit :limit"
    )
    fun getCoQueuedTrackCountsFlow(originTrackId: Long, limit: Int = -1): Flow<List<CoQueuedTrack>>

    @Query(
        "select track.sourcePath from track " +
                "inner join (" +
                "select merged.trackId as trackId, merged.tier as tier, " +
                "merged.score * (case when track.isFavorite then :favoriteScoreFactor else 1.0 end) as score, " +
                "sum(track.duration) over (" +
                "order by merged.tier, merged.score * (case when track.isFavorite then :favoriteScoreFactor else 1.0 end) desc " +
                "rows between unbounded preceding and current row" +
                ") as cumulativeDuration " +
                "from (" +
                "select candidate.trackId as trackId, " +
                "min(candidate.tier) as tier, " +
                "max(candidate.score) as score " +
                "from (" +
                "select other.trackId as trackId, 0 as tier, " +
                "count(*) * 1.0 / (select count(*) from queueHistoryTrack where trackId = other.trackId) as score " +
                "from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "where origin.trackId = :originTrackId and other.trackId != :originTrackId " +
                "group by other.trackId " +
                "union all " +
                "select other.trackId as trackId, 1 as tier, count(*) * 1.0 as score " +
                "from trackHistory as origin " +
                "inner join trackHistory as other on other.trackId != origin.trackId " +
                "and other.createdAt between origin.createdAt - :window and origin.createdAt + :window " +
                "where other.trackId != :originTrackId " +
                "and origin.trackId in (" +
                "select :originTrackId " +
                "union " +
                "select other2.trackId from queueHistoryTrack as origin2 " +
                "inner join queueHistoryTrack as other2 on other2.queueHistoryId = origin2.queueHistoryId " +
                "where origin2.trackId = :originTrackId and other2.trackId != :originTrackId " +
                "group by other2.trackId" +
                ") " +
                "group by other.trackId" +
                ") as candidate " +
                "group by candidate.trackId" +
                ") as merged " +
                "inner join track on track.id = merged.trackId " +
                "where random() % :pickDenominator = 0" +
                ") as cutoff on track.id = cutoff.trackId " +
                "where cutoff.cumulativeDuration <= :maxTotalDuration " +
                "order by cutoff.tier, cutoff.score desc"
    )
    suspend fun getSourcePathsToEnqueueAtRandomWithinDuration(
        originTrackId: Long,
        maxTotalDuration: Long = DEFAULT_MAX_TOTAL_DURATION,
        window: Long = DEFAULT_ADJACENT_WINDOW,
        pickDenominator: Int = DEFAULT_PICK_DENOMINATOR,
        favoriteScoreFactor: Double = DEFAULT_FAVORITE_SCORE_FACTOR,
    ): List<String>

    @Query("select sourcePath from track where id = :originTrackId")
    suspend fun getOriginSourcePath(originTrackId: Long): String?

    @Transaction
    suspend fun generateQueue(
        originTrackId: Long,
        maxTotalDuration: Long = DEFAULT_MAX_TOTAL_DURATION,
        window: Long = DEFAULT_ADJACENT_WINDOW,
        pickDenominator: Int = DEFAULT_PICK_DENOMINATOR,
        favoriteScoreFactor: Double = DEFAULT_FAVORITE_SCORE_FACTOR,
    ): List<String> {
        val originSourcePath = getOriginSourcePath(originTrackId)
        val others = getSourcePathsToEnqueueAtRandomWithinDuration(
            originTrackId = originTrackId,
            maxTotalDuration = maxTotalDuration,
            window = window,
            pickDenominator = pickDenominator,
            favoriteScoreFactor = favoriteScoreFactor,
        )

        return listOfNotNull(originSourcePath) + others.shuffled()
    }

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
