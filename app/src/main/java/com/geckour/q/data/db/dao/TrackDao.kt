package com.geckour.q.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.geckour.q.data.db.model.Track

@Dao
interface TrackDao {
    @Insert
    fun insert(track: Track)

    @Update
    fun update(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(track: Track)

    @Query("select * from track")
    fun getAllAsync(): LiveData<List<Track>>

    @Query("select * from track")
    fun getAll(): List<Track>

    @Query("select * from track where id = :id")
    fun get(id: Long): Track?

    @Query("delete from track where id = :id")
    fun delete(id: Long)

    @Query("select * from track where albumId = :albumId")
    fun findByAlbum(albumId: Long): List<Track>

    @Query("select * from track where artistId = :artistId")
    fun findByArtist(artistId: Long): List<Track>
}