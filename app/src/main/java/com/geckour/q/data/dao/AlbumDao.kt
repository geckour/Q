package com.geckour.q.data.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Update
import com.geckour.q.data.model.Album

@Dao
interface AlbumDao {
    @Insert
    fun insert(album: Album)

    @Update
    fun update(album: Album)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(album: Album)
}