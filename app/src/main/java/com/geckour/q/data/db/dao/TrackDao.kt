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
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track): Int

    @Query("delete from track where id = :id")
    suspend fun delete(id: Long): Int

    @Query("select * from track where id = :id")
    suspend fun get(id: Long): Track?

    @Query("select * from track where mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): Track?

    @Query("select * from track where mediaId in (:mediaIds)")
    suspend fun getByMediaIds(mediaIds: List<Long>): List<Track>

    @Query("select * from track where ignored != :ignore")
    suspend fun getAll(ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select mediaId from track where ignored != :ignore")
    suspend fun getAllMediaIds(ignore: Bool = Bool.UNDEFINED): List<Long>

    @Query("select * from track where ignored != :ignore")
    fun getAllAsync(ignore: Bool = Bool.UNDEFINED): LiveData<List<Track>>

    @Query("select * from track where title like :title and ignored != :ignore")
    suspend fun getAllByTitle(title: String, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    suspend fun getAllByAlbum(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore order by trackNum")
    suspend fun getAllByAlbumSorted(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    fun getAllByAlbumAsync(
        albumId: Long,
        ignore: Bool = Bool.UNDEFINED
    ): LiveData<List<Track>>

    @Query("select * from track where artistId = :artistId and ignored != :ignore")
    suspend fun getAllByArtist(artistId: Long, ignore: Bool = Bool.UNDEFINED): List<Track>

    @Query("update track set playbackCount = (select playbackCount from track where id = :trackId) + 1 where id = :trackId")
    suspend fun increasePlaybackCount(trackId: Long)

    @Query("update track set ignored = :ignored where id = :trackId")
    suspend fun setIgnored(trackId: Long, ignored: Bool)

    @Query("select count(*) from track")
    suspend fun count(): Int

    @Query("select lastModified from track order by lastModified desc limit 1")
    suspend fun getLatestModifiedEpochTime(): Long

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(context: Context, track: Track) {
        delete(track.id)

        if (getAllByAlbum(track.albumId, Bool.UNDEFINED).isEmpty()) {
            DB.getInstance(context).albumDao().deleteIncludingRootIfEmpty(context, track.albumId)
        }
    }

    suspend fun deleteIncludingRootIfEmpty(context: Context, id: Long) {
        val track = get(id) ?: return

        deleteIncludingRootIfEmpty(context, track)
    }

    suspend fun upsert(track: Track): Long {
        val toInsert = getByMediaId(track.mediaId)?.let {
            track.copy(id = it.id, playbackCount = it.playbackCount, ignored = it.ignored)
        } ?: track

        return insert(toInsert)
    }
}