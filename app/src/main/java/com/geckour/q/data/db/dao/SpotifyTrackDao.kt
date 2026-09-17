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

    @Query("select * from spotifytrack where uri = :uri")
    suspend fun get(uri: String): SpotifyTrack?

    @Query("select * from spotifytrack where uri in (:uris)")
    suspend fun getAllByUris(uris: List<String>): List<SpotifyTrack>

    @Query("select * from spotifytrack")
    fun getAllAsFlow(): Flow<List<SpotifyTrack>>

    @Query("delete from spotifytrack where uri not in (:urisToKeep)")
    suspend fun deleteUnused(urisToKeep: List<String>)
}
