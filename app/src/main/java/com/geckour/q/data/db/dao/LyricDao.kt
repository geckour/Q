package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.geckour.q.data.db.model.Lyric
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyric(lyric: Lyric): Long

    @Query("delete from lyric where trackId = :trackId")
    suspend fun deleteLyricByTrackId(trackId: Long)

    @Query("select * from lyric where trackId = :trackId")
    fun getLyricByTrackId(trackId: Long): Flow<Lyric>

    @Query("select id from lyric where trackId = :trackId")
    suspend fun getLyricIdByTrackId(trackId: Long): Long?
}