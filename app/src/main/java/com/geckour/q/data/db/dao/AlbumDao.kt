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
    suspend fun insert(album: Album): Long

    @Update
    suspend fun update(album: Album): Int

    @Query("delete from album where id = :id")
    suspend fun delete(id: Long): Int

    @Query("delete from track where albumId = :albumId")
    suspend fun deleteTrackByAlbum(albumId: Long): Int

    @Query("select * from album where id = :id")
    suspend fun get(id: Long): Album?

    @Query("select * from album where title like :title")
    suspend fun getByTitle(title: String): Album?

    @Query("select * from album")
    suspend fun getAll(): List<Album>

    @Query("select * from album")
    fun getAllAsync(): LiveData<List<Album>>

    @Query("select * from album where artistId = :id")
    suspend fun getAllByArtistId(id: Long): List<Album>

    @Query("select * from album where artistId = :id")
    fun getAllByArtistIdAsync(id: Long): LiveData<List<Album>>

    @Query("select * from album where title like :title")
    suspend fun getAllByTitle(title: String): List<Album>

    @Query("update album set playbackCount = (select playbackCount from album where id = :albumId) + 1 where id = :albumId")
    suspend fun increasePlaybackCount(albumId: Long)

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(context: Context, id: Long) {
        val album = get(id) ?: return

        delete(album.id)

        if (getAllByArtistId(album.artistId).isEmpty()) {
            DB.getInstance(context).artistDao().delete(album.artistId)
        }
    }

    @Transaction
    suspend fun deleteRecursively(id: Long) {
        deleteTrackByAlbum(id)
        delete(id)
    }

    suspend fun upsert(album: Album): Long {
        val toInsert = getAllByTitle(album.title).firstOrNull()?.let {
            album.copy(id = it.id, totalDuration = it.totalDuration + album.totalDuration)
        } ?: album

        return insert(toInsert)
    }
}