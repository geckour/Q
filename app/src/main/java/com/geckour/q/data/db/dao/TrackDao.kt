package com.geckour.q.data.db.dao

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track): Int

    @Query("delete from track where id = :id")
    suspend fun delete(id: Long): Int

    @Query("select * from track where id = :id")
    suspend fun get(id: Long): JoinedTrack?

    @Query("select * from track where mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): JoinedTrack?

    @Query("select * from track where mediaId in (:mediaIds)")
    suspend fun getByMediaIds(mediaIds: List<Long>): List<JoinedTrack>

    @Query("select * from track where ignored != :ignore")
    suspend fun getAll(ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Query("select mediaId from track where ignored != :ignore")
    suspend fun getAllMediaIds(ignore: Bool = Bool.UNDEFINED): List<Long>

    @Query("select * from track where ignored != :ignore order by titleSort collate nocase")
    fun getAllAsync(ignore: Bool = Bool.UNDEFINED): Flow<List<JoinedTrack>>

    @Query("select * from track where title like :title and ignored != :ignore")
    suspend fun getAllByTitle(title: String, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    suspend fun getAllByAlbum(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Query("select * from track where albumId = :albumId and ignored != :ignore order by trackNum")
    suspend fun getAllByAlbumSorted(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Query("select * from track where albumId = :albumId and ignored != :ignore order by trackNum")
    fun getAllByAlbumAsyncSorted(
        albumId: Long,
        ignore: Bool = Bool.UNDEFINED
    ): Flow<List<JoinedTrack>>

    @Query("update track set playbackCount = (select playbackCount from track where id = :trackId) + 1 where id = :trackId")
    suspend fun increasePlaybackCount(trackId: Long)

    @Query("update track set ignored = :ignored where id = :trackId")
    suspend fun setIgnored(trackId: Long, ignored: Bool)

    @Query("select count(*) from track")
    suspend fun count(): Int

    @Query("select lastModified from track order by lastModified desc limit 1")
    suspend fun getLatestModifiedEpochTime(): Long?

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(context: Context, trackId: Long) {
        delete(trackId)

        get(trackId)?.let {
            if (getAllByAlbum(it.track.albumId, Bool.UNDEFINED).isEmpty()) {
                DB.getInstance(context).albumDao()
                    .deleteIncludingRootIfEmpty(context, it.track.albumId)
            }
        }
    }

    suspend fun upsert(track: Track): Long {
        val toInsert = getByMediaId(track.mediaId)?.let {
            track.copy(
                id = it.track.id,
                playbackCount = it.track.playbackCount,
                ignored = it.track.ignored
            )
        } ?: track

        return insert(toInsert)
    }
}