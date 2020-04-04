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
import com.geckour.q.data.db.model.Artist

@Dao
interface ArtistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(artist: Artist): Long

    @Update
    fun update(artist: Artist): Int

    @Query("delete from artist where id = :id")
    fun delete(id: Long): Int

    @Query("delete from album where artistId = :artistId")
    fun deleteAlbumByArtist(artistId: Long)

    @Query("delete from track where artistId = :artistId")
    fun deleteTrackByArtist(artistId: Long)

    @Query("select * from artist where title like :title")
    fun findLikeTitle(title: String): List<Artist>

    @Query("select * from artist where title = :title")
    fun findArtist(title: String): List<Artist>

    @Query("select * from artist")
    fun getAll(): List<Artist>

    @Query("select * from artist")
    fun getAllAsync(): LiveData<List<Artist>>

    @Query("select * from artist where id in (select artistId from album group by artistId)")
    fun getAllOrientedAlbumAsync(): LiveData<List<Artist>>

    @Query("select * from artist where id = :id")
    fun get(id: Long): Artist?

    @Query("update artist set playbackCount = (select playbackCount from artist where id = :artistId) + 1 where id = :artistId")
    fun increasePlaybackCount(artistId: Long)

    @Transaction
    fun deleteRecursively(context: Context, id: Long) {
        deleteTrackByArtist(id)
        deleteAlbumByArtist(id)
        delete(id)
    }

    fun upsert(artist: Artist): Long {
        val toInsert = findArtist(artist.title).firstOrNull()?.let {
            artist.copy(
                id = it.id,
                playbackCount = it.playbackCount,
                totalDuration = it.totalDuration + artist.totalDuration
            )
        } ?: artist

        return insert(toInsert)
    }
}