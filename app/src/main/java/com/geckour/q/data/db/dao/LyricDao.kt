package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.shiftedBy
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyric(lyric: Lyric): Long

    @Query("delete from lyric where trackId = :trackId")
    suspend fun deleteLyricByTrackId(trackId: Long)

    @Query("select * from lyric where trackId = :trackId")
    fun getLyricFlowByTrackId(trackId: Long): Flow<Lyric?>

    @Query("select * from lyric where trackId = :trackId")
    suspend fun getLyricByTrackId(trackId: Long): Lyric?

    @Query("select id from lyric where trackId = :trackId")
    suspend fun getLyricIdByTrackId(trackId: Long): Long?

    @Query("select * from lyric where spotifyUri = :spotifyUri")
    fun getLyricFlowBySpotifyUri(spotifyUri: String): Flow<Lyric?>

    @Query("select * from lyric where spotifyUri = :spotifyUri")
    suspend fun getLyricBySpotifyUri(spotifyUri: String): Lyric?

    @Query("select id from lyric where spotifyUri = :spotifyUri")
    suspend fun getLyricIdBySpotifyUri(spotifyUri: String): Long?

    @Query("delete from lyric where spotifyUri is not null and spotifyUri not in (:urisToKeep)")
    suspend fun deleteUnusedSpotifyLyrics(urisToKeep: List<String>)

    @Transaction
    suspend fun shiftTimingsByTrackId(trackId: Long, delta: Long) {
        val lyric = getLyricByTrackId(trackId) ?: return
        upsertLyric(lyric.copy(lines = lyric.lines.shiftedBy(delta)))
    }

    @Transaction
    suspend fun shiftTimingsBySpotifyUri(spotifyUri: String, delta: Long) {
        val lyric = getLyricBySpotifyUri(spotifyUri) ?: return
        upsertLyric(lyric.copy(lines = lyric.lines.shiftedBy(delta)))
    }
}