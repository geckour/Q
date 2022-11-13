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
import com.geckour.q.util.searchTrackByFuzzyTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track): Int

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
    @Query("select * from track where sourcePath = :sourcePath")
    suspend fun getBySourcePath(sourcePath: String): JoinedTrack?

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

    @Query("select mediaId from track where dropboxPath == null")
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
    @Query("select * from track where albumId = :albumId and ignored != :ignore")
    fun getAllByAlbumAsync(
        albumId: Long,
        ignore: Bool = Bool.UNDEFINED
    ): Flow<List<JoinedTrack>>

    @Transaction
    @Query("select * from track where genre = :genreName")
    suspend fun getAllByGenreName(genreName: String): List<JoinedTrack>

    @Transaction
    @Query("select * from track where artistId = :artistId and ignored != :ignore")
    suspend fun getAllByArtist(artistId: Long, ignore: Bool = Bool.UNDEFINED): List<JoinedTrack>

    @Transaction
    @Query("select distinct genre from track where genre is not null")
    suspend fun getAllGenre(): List<String>

    @Transaction
    @Query("select distinct genre from track where genre is not null and genre like :name")
    suspend fun getAllGenreByName(name: String): List<String>

    @Query("update track set playbackCount = (select playbackCount from track where id = :trackId) + 1 where id = :trackId")
    suspend fun increasePlaybackCount(trackId: Long)

    @Query("update track set ignored = :ignored where id = :trackId")
    suspend fun setIgnored(trackId: Long, ignored: Bool)

    @Query("select count(*) from track")
    suspend fun count(): Int

    @Query("select lastModified from track order by lastModified desc limit 1")
    suspend fun getLatestModifiedEpochTime(): Long?

    @Transaction
    suspend fun getAllByTitles(
        db: DB,
        title: String?,
        albumTitle: String?,
        artistTitle: String?
    ): List<JoinedTrack> {
        val album = albumTitle?.let { db.albumDao().findByTitle(it) }?.album
        val artist = artistTitle?.let { db.artistDao().findByTitle(it) }
        return when {
            artist != null -> {
                when {
                    album != null -> {
                        if (title == null) {
                            db.trackDao().getAllByArtist(artist.id)
                                .filter { it.album.id == album.id }
                        } else listOfNotNull(db.trackDao().getByTitles(title, album.id, artist.id))
                    }
                    title != null -> {
                        db.trackDao().getAllByArtist(artist.id)
                            .filter { it.track.title.contains(title) }
                    }
                    else -> {
                        db.trackDao().getAllByArtist(artist.id)
                    }
                }
            }
            album != null -> {
                if (title == null) {
                    db.trackDao().getAllByAlbum(album.id)
                } else {
                    db.trackDao().getAllByAlbum(album.id).filter { it.track.title.contains(title) }
                }
            }
            title != null -> {
                db.searchTrackByFuzzyTitle(title)
            }
            else -> emptyList()
        }
    }

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
    suspend fun upsert(
        track: Track,
        albumId: Long,
        artistId: Long,
        newDuration: Long? = null
    ): Long {
        val existingTrack =
            if (track.mediaId < 0) get(track.id)
            else getByMediaId(track.mediaId)
        existingTrack?.track?.let {
            update(
                track.copy(
                    id = it.id,
                    playbackCount = it.playbackCount,
                    ignored = it.ignored,
                    duration = newDuration ?: it.duration,
                    artworkUriString = track.artworkUriString ?: it.artworkUriString
                )
            )
        }

        return existingTrack?.track?.id ?: insert(track)
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