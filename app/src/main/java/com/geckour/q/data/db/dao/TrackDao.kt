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
    fun insert(track: Track)

    @Update
    fun update(track: Track)

    @Query("select * from track")
    fun getAllAsync(): LiveData<List<Track>>

    @Query("select * from track")
    fun getAll(): List<Track>

    @Query("select * from track where id = :id")
    fun get(id: Long): Track?

    @Query("select * from track where mediaId = :trackId")
    fun getByMediaId(trackId: Long): Track?

    @Query("delete from track where id = :id")
    fun delete(id: Long)

    @Query("select * from track where albumId = :albumId")
    fun findByAlbum(albumId: Long): List<Track>

    @Query("select * from track where artistId = :artistId")
    fun findByArtist(artistId: Long): List<Track>

    @Query("select count(*) from track")
    fun count(): Int
}

fun Track.upsert(db: DB) {
    if (db.trackDao().getByMediaId(this.mediaId) != null) db.trackDao().update(this)
    else db.trackDao().insert(this)
}