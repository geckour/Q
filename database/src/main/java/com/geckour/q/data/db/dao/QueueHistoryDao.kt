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

    /**
     * Picks the tracks to queue after the origin, from the ones queued or played along with it.
     *
     * A local track and the Spotify track it is linked to count as one song: a song is the Spotify
     * URI for a Spotify track or a linked local track, and `'local:' || id` for an unlinked local
     * track. A song is queued as a lossless local track if it has one, then as the Spotify track,
     * and then as a lossy local track.
     *
     * `+spotifyUri is null` keeps SQLite from looking up the rows of local tracks by the index of
     * `spotifyUri`, which has most of the rows under null, instead of the one of `trackId`.
     */
    @Query(
        "with originSong(song) as (" +
                "select coalesce(:originSpotifyUri, " +
                "(select linkedSpotifyUri from track where id = :originTrackId), " +
                "'local:' || :originTrackId)" +
                "), " +
                // The queues that have the origin song in.
                "originQueue(queueHistoryId) as (" +
                "select queueHistoryId from queueHistoryTrack " +
                "where spotifyUri in (select song from originSong) " +
                "union " +
                "select queueHistoryId from queueHistoryTrack " +
                "where +spotifyUri is null and trackId in (" +
                "select cast(substr(song, 7) as integer) from originSong where song like 'local:%' " +
                "union all " +
                "select track.id from track " +
                "inner join originSong on track.linkedSpotifyUri = originSong.song" +
                ")" +
                "), " +
                // The songs queued along with the origin song, with the number of such queues.
                "coQueued(song, queueCount) as (" +
                "select song, count(*) from (" +
                "select distinct other.queueHistoryId as queueHistoryId, " +
                "coalesce(other.spotifyUri, otherTrack.linkedSpotifyUri, 'local:' || other.trackId) as song " +
                "from originQueue " +
                "inner join queueHistoryTrack as other on other.queueHistoryId = originQueue.queueHistoryId " +
                "left join track as otherTrack on otherTrack.id = other.trackId" +
                ") " +
                "where song != (select song from originSong) " +
                "group by song" +
                "), " +
                // Every queue that has each of the co-queued songs in.
                "coQueuedSongQueue(song, queueHistoryId) as (" +
                "select spotifyUri, queueHistoryId from queueHistoryTrack " +
                "where spotifyUri in (select song from coQueued) " +
                "union " +
                "select coalesce(queuedTrack.linkedSpotifyUri, 'local:' || queued.trackId), queued.queueHistoryId " +
                "from queueHistoryTrack as queued " +
                "left join track as queuedTrack on queuedTrack.id = queued.trackId " +
                "where +queued.spotifyUri is null and queued.trackId in (" +
                "select cast(substr(song, 7) as integer) from coQueued where song like 'local:%' " +
                "union all " +
                "select track.id from track " +
                "inner join coQueued on track.linkedSpotifyUri = coQueued.song" +
                ")" +
                "), " +
                "originSet(song) as (" +
                "select song from originSong union select song from coQueued" +
                "), " +
                // The plays of the origin song and its co-queued songs.
                "originPlay(createdAt, song) as (" +
                "select played.createdAt, played.spotifyUri from trackHistory as played " +
                "where played.spotifyUri in (select song from originSet) " +
                "union all " +
                "select played.createdAt, coalesce(playedTrack.linkedSpotifyUri, 'local:' || played.trackId) " +
                "from trackHistory as played " +
                "left join track as playedTrack on playedTrack.id = played.trackId " +
                "where +played.spotifyUri is null and played.trackId in (" +
                "select cast(substr(song, 7) as integer) from originSet where song like 'local:%' " +
                "union all " +
                "select track.id from track " +
                "inner join originSet on track.linkedSpotifyUri = originSet.song" +
                ")" +
                "), " +
                "candidate(song, tier, score) as (" +
                "select coQueued.song, 0, max(coQueued.queueCount) * 1.0 / count(*) " +
                "from coQueued " +
                "inner join coQueuedSongQueue on coQueuedSongQueue.song = coQueued.song " +
                "group by coQueued.song " +
                "union all " +
                "select adjacent.song, 1, count(*) * 1.0 from (" +
                "select coalesce(other.spotifyUri, otherTrack.linkedSpotifyUri, 'local:' || other.trackId) as song, " +
                "originPlay.song as originPlaySong " +
                "from originPlay " +
                "inner join trackHistory as other " +
                "on other.createdAt between originPlay.createdAt - :window and originPlay.createdAt + :window " +
                "left join track as otherTrack on otherTrack.id = other.trackId" +
                ") as adjacent " +
                "where adjacent.song != adjacent.originPlaySong " +
                "and adjacent.song != (select song from originSong) " +
                "group by adjacent.song" +
                "), " +
                "merged(song, tier, score) as (" +
                "select song, min(tier), max(score) from candidate group by song" +
                "), " +
                // A song is left out when any of its tracks is, and favored when any of them is.
                "resolved(sourcePath, duration, tier, score) as (" +
                "select coalesce(unlinked.sourcePath, lossless.sourcePath, spotifytrack.uri, lossy.sourcePath), " +
                "coalesce(unlinked.duration, lossless.duration, spotifytrack.duration, lossy.duration), " +
                "merged.tier, " +
                "merged.score * (case when coalesce(unlinked.isFavorite, 0) or coalesce(spotifytrack.isFavorite, 0) " +
                "or exists (select 1 from track where track.linkedSpotifyUri = merged.song and track.isFavorite) " +
                "then :favoriteScoreFactor else 1.0 end) " +
                "from merged " +
                "left join track as unlinked on merged.song like 'local:%' " +
                "and unlinked.id = cast(substr(merged.song, 7) as integer) " +
                "left join track as lossless on lossless.id = (" +
                "select track.id from track where track.linkedSpotifyUri = merged.song " +
                "and (track.codec like 'FLAC%' or track.codec like 'Alac' or track.codec like 'WAV%') " +
                "order by track.bitrate desc, track.id limit 1" +
                ") " +
                "left join spotifytrack on spotifytrack.uri = merged.song " +
                "left join track as lossy on lossy.id = (" +
                "select track.id from track where track.linkedSpotifyUri = merged.song " +
                "order by track.bitrate desc, track.id limit 1" +
                ") " +
                "where coalesce(unlinked.ignored, 0) != 1 and not coalesce(spotifytrack.ignored, 0) " +
                "and not exists (select 1 from track where track.linkedSpotifyUri = merged.song and track.ignored = 1)" +
                ") " +
                "select cutoff.sourcePath from (" +
                "select sourcePath, tier, score, " +
                "sum(duration) over (" +
                "order by tier, score desc rows between unbounded preceding and current row" +
                ") as cumulativeDuration " +
                "from resolved " +
                "where sourcePath is not null and random() % :pickDenominator = 0" +
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
