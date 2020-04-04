package com.geckour.q.data.db.dao

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("select * from track where id = :id")
    fun get(id: Long): Track?

    @Query("select * from track where mediaId = :trackId")
    fun getByMediaId(trackId: Long): Track?

    @Query("select * from track where ignored != :ignore")
    fun getAll(ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where ignored != :ignore")
    fun getAllAsync(ignore: Bool = Bool.UNDEFINED): LiveData<List<Track>>

    @Query("select * from track where title like :title and ignored != :ignore")
    fun getAllByTitle(title: String, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    fun getAllByAlbum(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore order by trackNum")
    fun getAllByAlbumSorted(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    fun getAllByAlbumAsync(albumId: Long, ignore: Bool = Bool.UNDEFINED): LiveData<List<Track>>

    @Query("select * from track where artistId = :artistId and ignored != :ignore")
    fun getAllByArtist(artistId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("update track set playbackCount = (select playbackCount from track where id = :trackId) + 1 where id = :trackId")
    fun increasePlaybackCount(trackId: Long)

    @Query("update track set ignored = :ignored where id = :trackId")
    fun setIgnored(trackId: Long, ignored: Bool)

    @Query("select count(*) from track")
    fun count(): Int

    @Transaction
    fun deleteIncludingRootIfEmpty(context: Context, id: Long) {
        val track = get(id) ?: return

        delete(track.id)

        if (getAllByAlbum(track.albumId, Bool.UNDEFINED).isEmpty()) {
            DB.getInstance(context).albumDao().deleteIncludingRootIfEmpty(context, track.albumId)
        }
    }

    fun upsert(track: Track): Long {
        val toInsert = getByMediaId(track.mediaId)?.let {
            track.copy(id = it.id, playbackCount = it.playbackCount, ignored = it.ignored)
        } ?: track

        return insert(toInsert)
    }
}