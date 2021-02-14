package com.geckour.q.data.db.dao

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.JoinedAlbum
import kotlinx.coroutines.flow.Flow

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
    suspend fun get(id: Long): JoinedAlbum?

    @Query("select * from album where title like :title")
    suspend fun getByTitle(title: String): JoinedAlbum?

    @Query("select * from album")
    fun getAllAsync(): Flow<List<JoinedAlbum>>

    @Query("select * from album where artistId = :artistId")
    suspend fun getAllByArtist(artistId: Long): List<JoinedAlbum>

    @Query("select * from album where artistId = :artistId")
    fun getAllByArtistAsync(artistId: Long): Flow<List<JoinedAlbum>>

    @Query("select * from album where title like :title")
    suspend fun getAllByTitle(title: String): List<JoinedAlbum>

    @Query("update album set playbackCount = (select playbackCount from album where id = :albumId) + 1 where id = :albumId")
    suspend fun increasePlaybackCount(albumId: Long)

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(context: Context, albumId: Long) {
        get(albumId)?.let {
            delete(albumId)

            val db = DB.getInstance(context)
            if (getAllByArtist(it.album.artistId).isEmpty()) {
                db.artistDao().delete(it.album.artistId)
            } else {
                db.artistDao()
                    .update(
                        it.artist.copy(
                            totalDuration = it.artist.totalDuration - it.album.totalDuration
                        )
                    )
            }
        }
    }

    suspend fun upsert(album: Album, pastSongDuration: Long = 0): Long {
        val toInsert = getByTitle(album.title)?.let {
            val duration = it.album.totalDuration - pastSongDuration + album.totalDuration
            album.copy(
                id = it.album.id,
                totalDuration = duration,
                artworkUriString = album.artworkUriString ?: it.album.artworkUriString
            )
        } ?: album

        return insert(toInsert)
    }
}