package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geckour.q.data.db.model.SpotifyTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotifyTrackDao {

    @Upsert
    suspend fun upsert(spotifyTrack: SpotifyTrack)

    @Upsert
    suspend fun upsertAll(spotifyTracks: List<SpotifyTrack>)

    @Query("select * from spotifytrack where uri = :uri")
    suspend fun get(uri: String): SpotifyTrack?

    @Query("select * from spotifytrack where uri in (:uris)")
    suspend fun getAllByUris(uris: List<String>): List<SpotifyTrack>

    @Query("select * from spotifytrack where uri in (:uris)")
    fun getAllByUrisAsFlow(uris: List<String>): Flow<List<SpotifyTrack>>

    @Query("select * from spotifytrack where createdAt > 0")
    suspend fun getAllInLibrary(): List<SpotifyTrack>

    @Query("update spotifytrack set createdAt = 0 where uri in (:uris)")
    suspend fun removeFromLibrary(uris: List<String>)

    @Query("update spotifytrack set createdAt = 0")
    suspend fun clearLibrary()

    @Query("delete from spotifytrack where createdAt = 0 and uri not in (:urisToKeep)")
    suspend fun deleteUnusedOutsideLibrary(urisToKeep: List<String>)
}
