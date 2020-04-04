package com.geckour.q.data.db.dao

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(album: Album): Long

    @Update
    fun update(album: Album): Int

    @Query("delete from album where id = :id")
    fun delete(id: Long): Int

    @Query("delete from track where albumId = :albumId")
    fun deleteTrackByAlbum(albumId: Long): Int

    @Query("select * from album")
    fun getAll(): List<Album>

    @Query("select * from album")
    fun getAllAsync(): LiveData<List<Album>>

    @Query("select * from album where id = :id")
    fun get(id: Long): Album?

    @Query("select * from album where artistId = :id")
    fun findByArtistId(id: Long): List<Album>

    @Query("select * from album where artistId = :id")
    fun findByArtistIdAsync(id: Long): LiveData<List<Album>>

    @Query("select * from album where title like :title")
    fun findByTitle(title: String): List<Album>

    @Query("update album set playbackCount = (select playbackCount from album where id = :albumId) + 1 where id = :albumId")
    fun increasePlaybackCount(albumId: Long)

    @Transaction
    fun deleteIncludingRootIfEmpty(context: Context, id: Long) {
        val album = get(id) ?: return

        delete(album.id)

        if (findByArtistId(album.artistId).isEmpty()) {
            DB.getInstance(context).artistDao().delete(album.artistId)
        }
    }

    @Transaction
    fun deleteRecursively(id: Long) {
        deleteTrackByAlbum(id)
        delete(id)
    }

    fun upsert(album: Album): Long {
        val toInsert = findByTitle(album.title).firstOrNull()?.let {
            album.copy(id = it.id, totalDuration = it.totalDuration + album.totalDuration)
        } ?: album

        return insert(toInsert)
    }
}