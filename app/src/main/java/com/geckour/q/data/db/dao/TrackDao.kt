package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track): Int

    @Query("update track set sourcePath = '' where id in (:ids)")
    suspend fun clearAllSourcePaths(ids: List<Long>)

    @Query("select id from track where dropboxPath is not null and sourcePath like '%/com.geckour.q%/cache/audio/id%%3A%'")
    suspend fun getAllDownloadedIds(): List<Long>

    @Query("delete from track where id = :id")
    suspend fun delete(id: Long): Int

    @Query("delete from track where id in (:ids)")
    suspend fun deleteAllByIds(ids: List<Long>): Int

    @Transaction
    @Query("select * from track where id = :id")
    suspend fun get(id: Long): JoinedTrack?

    @Transaction
    @Query("select * from track where id in (:ids)")
    suspend fun getAllByIds(ids: List<Long>): List<JoinedTrack>

    @Transaction
    @Query("select sourcePath from track where id in (:ids)")
    suspend fun getAllSourcePathsByIds(ids: List<Long>): List<String>

    @Transaction
    @Query("select * from track where sourcePath = :sourcePath")
    suspend fun getBySourcePath(sourcePath: String): JoinedTrack?

    @Transaction
    @Query("select * from track where sourcePath in (:sourcePaths)")
    suspend fun getAllBySourcePaths(sourcePaths: List<String>): List<JoinedTrack>

    @Transaction
    @Query("select * from track where dropboxPath = :dropboxPath")
    suspend fun getByDropboxPath(dropboxPath: String): JoinedTrack?

    @Transaction
    @Query("select * from track where title = :title and albumId = :albumId and artistId = :artistId")
    suspend fun getByTitles(title: String, albumId: Long, artistId: Long): JoinedTrack?

    @Transaction
    @Query("select * from track where mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): JoinedTrack?

    @Transaction
    @Query("select * from track where mediaId in (:mediaIds)")
    suspend fun getAllByMediaIds(mediaIds: List<Long>): List<JoinedTrack>

    @Query("select duration from track where mediaId = :mediaId")
    suspend fun getDurationWithMediaId(mediaId: Long): Long?

    @Transaction
    @Query("select * from track where ignored != :ignore")
    suspend fun getAll(ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Query("select mediaId from track")
    suspend fun getAllMediaIds(): List<Long>

    @Query("select mediaId from track where dropboxPath is null")
    suspend fun getAllLocalMediaIds(): List<Long>

    @Transaction
    @Query("select * from track where ignored != :ignore order by titleSort collate nocase")
    fun getAllAsync(ignore: Bool = Bool.UNDEFINED): Flow<List<JoinedTrack>>

    @Transaction
    @Query("select * from track where title like :title")
    suspend fun getAllByTitle(title: String): List<JoinedTrack>

    @Transaction
    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    suspend fun getAllByAlbum(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Transaction
    @Query("select * from track where albumId = :albumId and ignored != :ignore order by trackNum")
    suspend fun getAllByAlbumSorted(albumId: Long, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Transaction
    @Query("select * from track where albumId = :albumId and ignored != :ignore order by discNum, trackNum")
    fun getAllByAlbumAsync(
        albumId: Long,
        ignore: Bool = Bool.UNDEFINED
    ): Flow<List<JoinedTrack>>

    @Transaction
    @Query("select * from track where genre = :genreName")
    suspend fun getAllByGenreName(genreName: String): List<JoinedTrack>

    @Transaction
    @Query("select * from track where genre = :genreName")
    fun getAllByGenreNameAsync(genreName: String): Flow<List<JoinedTrack>>

    @Transaction
    @Query("select * from track where artistId = :artistId and ignored != :ignore")
    suspend fun getAllByArtist(artistId: Long, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Transaction
    @Query("select distinct genre from track where genre is not null")
    suspend fun getAllGenre(): List<String>

    @Transaction
    @Query("select distinct genre from track where genre is not null")
    fun getAllGenreAsync(): Flow<List<String>>

    @Transaction
    @Query("select distinct genre from track where genre is not null and genre like :name")
    suspend fun getAllGenreByName(name: String): List<String>

    @Query("update track set playbackCount = (select playbackCount from track where id = :trackId) + 1 where id = :trackId")
    suspend fun increasePlaybackCount(trackId: Long)

    @Query("update track set ignored = :ignored where id = :trackId")
    suspend fun setIgnored(trackId: Long, ignored: Bool)

    @Query("select count(*) from track")
    suspend fun count(): Int

    @Query("select count(*) from track")
    fun countAsync(): Flow<Int>

    @Query("select lastModified from track order by lastModified desc limit 1")
    suspend fun getLatestModifiedEpochTime(): Long?

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(db: DB, vararg trackIds: Long) {
        val tracks = getAllByIds(trackIds.toList())
        deleteAllByIds(trackIds.toList())

        tracks.forEach {
            if (getAllByAlbum(it.track.albumId, Bool.UNDEFINED).isEmpty()) {
                db.albumDao().deleteIncludingRootIfEmpty(db, it.track.albumId)
            } else {
                db.albumDao()
                    .update(
                        it.album.copy(
                            totalDuration = it.album.totalDuration - it.track.duration
                        )
                    )
                db.artistDao()
                    .update(
                        it.artist.copy(
                            totalDuration = it.artist.totalDuration - it.track.duration
                        )
                    )
            }
        }
    }

    @Transaction
    suspend fun getDurationWithTitles(
        title: String,
        albumTitle: String?,
        artistTitle: String?
    ): Long? =
        getAllByTitle(title)
            .firstOrNull { it.album.title == albumTitle && it.artist.title == artistTitle }
            ?.track
            ?.duration
}