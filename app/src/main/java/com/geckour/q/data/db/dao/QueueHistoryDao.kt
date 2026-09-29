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
import com.geckour.q.data.db.model.TrackRef
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

    @Query(
        "select trackId, spotifyUri from queueHistoryTrack where queueHistoryId = :queueHistoryId"
    )
    suspend fun getTrackRefsByQueueHistoryId(queueHistoryId: Long): List<TrackRef>

    @Transaction
    suspend fun saveQueue(
        trackRefs: List<TrackRef>,
        createdAt: Long = System.currentTimeMillis(),
        keepHistoryCount: Int = DEFAULT_KEEP_HISTORY_COUNT,
    ) {
        val distinctTrackRefs = trackRefs.distinct()
        if (distinctTrackRefs.isEmpty()) return

        val latestTrackRefs = getLatestQueueHistoryId()
            ?.let { getTrackRefsByQueueHistoryId(it).toSet() }
        if (latestTrackRefs == distinctTrackRefs.toSet()) return

        val queueHistoryId = insertQueueHistory(
            QueueHistory(id = 0, createdAt = createdAt)
        )
        insertQueueHistoryTracks(
            distinctTrackRefs.map {
                QueueHistoryTrack(
                    id = 0,
                    queueHistoryId = queueHistoryId,
                    trackId = it.trackId,
                    spotifyUri = it.spotifyUri,
                )
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
        "with originSet(trackId, spotifyUri) as (" +
                "select :originTrackId, :originSpotifyUri " +
                "union " +
                "select other.trackId, other.spotifyUri from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "where origin.trackId = :originTrackId and origin.spotifyUri is :originSpotifyUri" +
                ") " +
                "select cutoff.sourcePath from (" +
                "select coalesce(track.sourcePath, spotifytrack.uri) as sourcePath, merged.tier as tier, " +
                "merged.score * (case when coalesce(track.isFavorite, spotifytrack.isFavorite) then :favoriteScoreFactor else 1.0 end) as score, " +
                "sum(coalesce(track.duration, spotifytrack.duration)) over (" +
                "order by merged.tier, merged.score * (case when coalesce(track.isFavorite, spotifytrack.isFavorite) then :favoriteScoreFactor else 1.0 end) desc " +
                "rows between unbounded preceding and current row" +
                ") as cumulativeDuration " +
                "from (" +
                "select candidate.trackId as trackId, candidate.spotifyUri as spotifyUri, " +
                "min(candidate.tier) as tier, " +
                "max(candidate.score) as score " +
                "from (" +
                "select other.trackId as trackId, other.spotifyUri as spotifyUri, 0 as tier, " +
                "count(*) * 1.0 / (" +
                "select count(*) from queueHistoryTrack " +
                "where trackId = other.trackId and spotifyUri is other.spotifyUri" +
                ") as score " +
                "from queueHistoryTrack as origin " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = origin.queueHistoryId " +
                "where origin.trackId = :originTrackId and origin.spotifyUri is :originSpotifyUri " +
                "and not (other.trackId = :originTrackId and other.spotifyUri is :originSpotifyUri) " +
                "group by other.trackId, other.spotifyUri " +
                "union all " +
                "select other.trackId as trackId, other.spotifyUri as spotifyUri, 1 as tier, " +
                "count(*) * 1.0 as score " +
                "from originSet " +
                "inner join trackHistory as origin on origin.trackId = originSet.trackId " +
                "and origin.spotifyUri is originSet.spotifyUri " +
                "inner join trackHistory as other on not (other.trackId = origin.trackId and other.spotifyUri is origin.spotifyUri) " +
                "and other.createdAt between origin.createdAt - :window and origin.createdAt + :window " +
                "where not (other.trackId = :originTrackId and other.spotifyUri is :originSpotifyUri) " +
                "group by other.trackId, other.spotifyUri" +
                ") as candidate " +
                "group by candidate.trackId, candidate.spotifyUri" +
                ") as merged " +
                "left join track on track.id = merged.trackId " +
                "left join spotifytrack on spotifytrack.uri = merged.spotifyUri " +
                "where (track.id is not null or spotifytrack.uri is not null) " +
                "and random() % :pickDenominator = 0" +
                ") as cutoff " +
                "where cutoff.cumulativeDuration <= :maxTotalDuration " +
                "order by cutoff.tier, cutoff.score desc"
    )
    suspend fun getSourcePathsToEnqueueAtRandomWithinDuration(
        originTrackId: Long,
        originSpotifyUri: String?,
        maxTotalDuration: Long = DEFAULT_MAX_TOTAL_DURATION,
        window: Long = DEFAULT_ADJACENT_WINDOW,
        pickDenominator: Int = DEFAULT_PICK_DENOMINATOR,
        favoriteScoreFactor: Double = DEFAULT_FAVORITE_SCORE_FACTOR,
    ): List<String>

    @Query("select sourcePath from track where id = :originTrackId")
    suspend fun getOriginSourcePath(originTrackId: Long): String?

    @Transaction
    suspend fun generateQueue(
        origin: TrackRef,
        maxTotalDuration: Long = DEFAULT_MAX_TOTAL_DURATION,
        window: Long = DEFAULT_ADJACENT_WINDOW,
        pickDenominator: Int = DEFAULT_PICK_DENOMINATOR,
        favoriteScoreFactor: Double = DEFAULT_FAVORITE_SCORE_FACTOR,
    ): List<String> {
        val originSourcePath = origin.spotifyUri ?: getOriginSourcePath(origin.trackId)
        val others = getSourcePathsToEnqueueAtRandomWithinDuration(
            originTrackId = origin.trackId,
            originSpotifyUri = origin.spotifyUri,
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
