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

    @Query("delete from album where id = :id")
    fun delete(id: Long): Int

    @Query("select * from album")
    fun getAll(): List<Album>

    @Query("select * from album")
    fun getAllAsync(): LiveData<List<Album>>

    @Query("select * from album where id = :id")
    fun get(id: Long): Album?

    @Query("select * from album where mediaId = :albumId")
    fun getByMediaId(albumId: Long): Album?

    @Query("select * from album where artistId = :id")
    fun findByArtistId(id: Long): List<Album>

    @Query("select * from album where artistId = :id")
    fun findByArtistIdAsync(id: Long): LiveData<List<Album>>

    @Query("select * from album where title like :title")
    fun findByTitle(title: String): List<Album>
}

fun Album.upsert(db: DB): Long =
        db.albumDao().getByMediaId(this.mediaId)?.let {
            if (this.title != null) db.albumDao().update(this.copy(id = it.id))
            it.id
        } ?: db.albumDao().insert(this)