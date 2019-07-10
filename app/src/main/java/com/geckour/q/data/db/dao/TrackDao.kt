package com.geckour.q.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.data.db.model.Track

@Dao
interface TrackDao {

    @Insert
    fun insert(track: Track): Long

    @Update
    fun update(track: Track): Int

    @Query("delete from track where id = :id")
    fun delete(id: Long): Int

    @Query("select * from track where ignored != :ignore")
    fun getAllAsync(ignore: Bool = Bool.UNDEFINED): LiveData<List<Track>>

    @Query("select * from track where ignored != :ignore")
    fun getAll(ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where id = :id")
    fun get(id: Long): Track?

    @Query("select * from track where mediaId = :trackId")
    fun getByMediaId(trackId: Long): Track?

    @Query("select * from track where title like :title and ignored != :ignore")
    fun findByTitle(title: String, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    fun findByAlbum(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    fun findByAlbumAsync(albumId: Long, ignore: Bool = Bool.UNDEFINED): LiveData<List<Track>>

    @Query("select * from track where artistId = :artistId and ignored != :ignore")
    fun findByArtist(artistId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("update track set playbackCount = (select playbackCount from track where id = :trackId) + 1 where id = :trackId")
    fun increasePlaybackCount(trackId: Long)

    @Query("update track set ignored = :ignored where id = :trackId")
    fun setIgnored(trackId: Long, ignored: Bool)

    @Query("select count(*) from track")
    fun count(): Int
}

fun Track.upsert(db: DB) {
    db.trackDao().getByMediaId(this.mediaId).let {
        if (it != null) {
            db.trackDao().update(this.copy(id = it.id, playbackCount = it.playbackCount, ignored = it.ignored))
            it.id
        } else db.trackDao().insert(this)
    }
}