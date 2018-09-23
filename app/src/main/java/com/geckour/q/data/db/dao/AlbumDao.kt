package com.geckour.q.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album

@Dao
interface AlbumDao {
    @Insert
    fun insert(album: Album): Long

    @Update
    fun update(album: Album): Int

    @Query("select * from album")
    fun getAllAsync(): LiveData<List<Album>>

    @Query("select * from album where id = :id")
    fun get(id: Long): Album?

    @Query("select * from album where mediaId = :albumId")
    fun getByMediaId(albumId: Long): Album?
}

fun Album.upsert(db: DB): Long =
        db.albumDao().getByMediaId(this.mediaId)?.let {
            db.albumDao().update(this)
            it.id
        } ?: db.albumDao().insert(this)