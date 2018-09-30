package com.geckour.q.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track

@Dao
interface TrackDao {
    @Insert
    fun insert(track: Track): Long

    @Update
    fun update(track: Track): Int

    @Query("delete from track where id = :id")
    fun delete(id: Long): Int

    @Query("select * from track")
    fun getAllAsync(): LiveData<List<Track>>

    @Query("select * from track")
    fun getAll(): List<Track>

    @Query("select * from track where id = :id")
    fun get(id: Long): Track?

    @Query("select * from track where mediaId = :trackId")
    fun getByMediaId(trackId: Long): Track?

    @Query("select * from track where title like :title")
    fun searchByTitle(title: String): List<Track>

    @Query("select * from track where albumId = :albumId")
    fun findByAlbum(albumId: Long): List<Track>

    @Query("select * from track where albumId = :albumId")
    fun findByAlbumAsync(albumId: Long): LiveData<List<Track>>

    @Query("select * from track where artistId = :artistId")
    fun findByArtist(artistId: Long): List<Track>

    @Query("select count(*) from track")
    fun count(): Int
}

fun Track.upsert(db: DB) {
    db.trackDao().getByMediaId(this.mediaId).let {
        if (it != null) {
            db.trackDao().update(this.copy(id = it.id))
            it.id
        } else db.trackDao().insert(this)
    }
}