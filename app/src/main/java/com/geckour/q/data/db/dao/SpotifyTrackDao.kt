package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geckour.q.data.db.model.SpotifyTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotifyTrackDao {

    @Upsert
    suspend fun upsertAll(spotifyTracks: List<SpotifyTrack>)

    @Transaction
    suspend fun save(spotifyTracks: List<SpotifyTrack>) {
        if (spotifyTracks.isEmpty()) return

        val stored = getAllByUris(spotifyTracks.map { it.uri }).associateBy { it.uri }
        upsertAll(
            spotifyTracks.map { track ->
                stored[track.uri]
                    ?.let { track.copy(playbackCount = it.playbackCount, isFavorite = it.isFavorite) }
                    ?: track
            }
        )
    }

    @Query("select * from spotifytrack where uri = :uri")
    suspend fun get(uri: String): SpotifyTrack?

    @Query("select * from spotifytrack where uri in (:uris)")
    suspend fun getAllByUris(uris: List<String>): List<SpotifyTrack>

    @Query("select * from spotifytrack where uri in (:uris)")
    fun getAllByUrisAsFlow(uris: List<String>): Flow<List<SpotifyTrack>>

    @Query("select * from spotifytrack where createdAt > 0")
    suspend fun getAllInLibrary(): List<SpotifyTrack>

    @Query("update spotifytrack set isFavorite = :isFavorite where uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("update spotifytrack set playbackCount = playbackCount + 1 where uri = :uri")
    suspend fun increasePlaybackCount(uri: String)

    @Query("update spotifytrack set createdAt = 0 where uri in (:uris)")
    suspend fun removeFromLibrary(uris: List<String>)

    @Query("update spotifytrack set createdAt = 0")
    suspend fun clearLibrary()

    @Query(
        "delete from spotifytrack where createdAt = 0 and isFavorite = 0 " +
                "and uri not in (:urisToKeep) " +
                "and uri not in (select spotifyUri from trackhistory where spotifyUri is not null) " +
                "and uri not in (select spotifyUri from queuehistorytrack where spotifyUri is not null) " +
                "and uri not in (select spotifyUri from savedqueuetrack where spotifyUri is not null)"
    )
    suspend fun deleteUnusedOutsideLibrary(urisToKeep: List<String>)
}
