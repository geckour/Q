package com.geckour.q.data.db.dao

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import com.geckour.q.data.db.model.Album

@Dao
interface AlbumDao {
    @Insert
    fun insert(album: Album)

    @Update
    fun update(album: Album)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(album: Album)

    @Query("select * from album")
    fun getAllAsync(): LiveData<List<Album>>

    @Query("select * from album where :id")
    fun get(id: Long): Album
}