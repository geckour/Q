package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geckour.q.core.util.normalizedForSearch
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.data.db.model.spotifyMatchKeyOf
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotifyTrackDao {

    companion object {

        /**
         * Orders the Spotify tracks of the same match key to pick the one to link a local track to:
         * the ones in the library first, and then the most played.
         */
        private const val LINK_PREFERENCE_ORDER =
            "order by spotifytrack.createdAt > 0 desc, spotifytrack.playbackCount desc, " +
                    "spotifytrack.uri"

        private const val PICK_SPOTIFY_URI_TO_LINK =
            "(select spotifytrack.uri from spotifytrack " +
                    "where spotifytrack.matchKey = track.matchKey " +
                    LINK_PREFERENCE_ORDER + " " +
                    "limit 1)"

        /**
         * Links every local track not linked yet to a Spotify track of the same match key. A track
         * keeps the link it already has.
         */
        internal const val LINK_ALL_LOCAL_TRACKS =
            "update track set linkedSpotifyUri = " + PICK_SPOTIFY_URI_TO_LINK + " " +
                    "where linkedSpotifyUri is null " +
                    "and matchKey in (select matchKey from spotifytrack)"
    }

    @Upsert
    suspend fun upsertAll(spotifyTracks: List<SpotifyTrack>)

    @Transaction
    suspend fun save(spotifyTracks: List<SpotifyTrack>) {
        if (spotifyTracks.isEmpty()) return

        val uris = spotifyTracks.map { it.uri }
        val stored = getAllByUris(uris).associateBy { it.uri }
        upsertAll(
            spotifyTracks.map { track ->
                val matchKey = spotifyMatchKeyOf(track.title, track.albumName, track.artistName)
                stored[track.uri]
                    ?.let {
                        track.copy(
                            playbackCount = it.playbackCount,
                            isFavorite = it.isFavorite,
                            ignored = it.ignored,
                            matchKey = matchKey,
                        )
                    }
                    ?: track.copy(matchKey = matchKey)
            }
        )
        linkLocalTracks(uris)
    }

    /** Links the local tracks not linked yet to a Spotify track of the same match key as [uris]. */
    @Query(
        "update track set linkedSpotifyUri = " + PICK_SPOTIFY_URI_TO_LINK + " " +
                "where linkedSpotifyUri is null " +
                "and matchKey in (select matchKey from spotifytrack where uri in (:uris))"
    )
    suspend fun linkLocalTracks(uris: List<String>)

    /** The Spotify track to link a local track of [matchKey] to. */
    @Query(
        "select uri from spotifytrack where matchKey = :matchKey " +
                LINK_PREFERENCE_ORDER + " " +
                "limit 1"
    )
    suspend fun getUriToLink(matchKey: String): String?

    @Query("select * from spotifytrack where uri = :uri")
    suspend fun get(uri: String): SpotifyTrack?

    @Query("select * from spotifytrack where uri in (:uris)")
    suspend fun getAllByUris(uris: List<String>): List<SpotifyTrack>

    @Query("select * from spotifytrack where uri in (:uris)")
    fun getAllByUrisAsFlow(uris: List<String>): Flow<List<SpotifyTrack>>

    @Query("select * from spotifytrack where createdAt > 0")
    suspend fun getAllInLibrary(): List<SpotifyTrack>

    @Query("select * from spotifytrack where uri in (select spotifyUri from lyric where searchText like ('%'||:searchText||'%'))")
    suspend fun findAllByLyricSearchText(searchText: String): List<SpotifyTrack>

    /**
     * The Spotify tracks whose lyric has a line containing [keyword], regardless of the width, the
     * case, the kana, spaces and symbols.
     */
    suspend fun findAllByLyricKeyword(keyword: String): List<SpotifyTrack> =
        findAllByLyricSearchText(keyword.normalizedForSearch().ifEmpty { return emptyList() })

    @Query("update spotifytrack set isFavorite = :isFavorite where uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("update spotifytrack set ignored = :ignored where uri = :uri")
    suspend fun updateIgnored(uri: String, ignored: Boolean)

    @Query("update spotifytrack set playbackCount = playbackCount + 1 where uri = :uri")
    suspend fun increasePlaybackCount(uri: String)

    @Query("update spotifytrack set createdAt = 0 where uri in (:uris)")
    suspend fun removeFromLibrary(uris: List<String>)

    @Query("update spotifytrack set createdAt = 0")
    suspend fun clearLibrary()

    @Query(
        "delete from spotifytrack where createdAt = 0 and isFavorite = 0 and ignored = 0 " +
                "and uri not in (:urisToKeep) " +
                "and uri not in (select spotifyUri from trackhistory where spotifyUri is not null) " +
                "and uri not in (select spotifyUri from queuehistorytrack where spotifyUri is not null) " +
                "and uri not in (select spotifyUri from savedqueuetrack where spotifyUri is not null)"
    )
    suspend fun deleteUnusedOutsideLibrary(urisToKeep: List<String>)
}
