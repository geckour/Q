package com.geckour.q.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: Artist): Long

    @Update
    suspend fun update(artist: Artist): Int

    @Query("delete from artist where id = :id")
    suspend fun delete(id: Long): Int

    @Query("delete from album where artistId = :artistId")
    suspend fun deleteAlbumByArtist(artistId: Long)

    @Query("delete from track where artistId = :artistId")
    suspend fun deleteTrackByArtist(artistId: Long)

    @Query("select * from artist where id = :id")
    suspend fun get(id: Long): Artist?

    @Query("select * from artist where title = :title")
    suspend fun getByTitle(title: String): Artist?

    @Query("select * from artist where title = :title")
    suspend fun getAllByTitle(title: String): List<Artist>

    @Query("select * from artist where title like :title")
    suspend fun findAllByTitle(title: String): List<Artist>

    @Query("select * from artist where artist.id in (select artistId from album group by album.id) order by artist.titleSort collate nocase")
    fun getAllOrientedAlbumAsPagingSource(): PagingSource<Int, Artist>

    @Query("select exists(select 1 from track where track.dropboxPath is not null and track.albumId in (select id from album where album.artistId = :artistId group by album.id))")
    fun containDropboxContentAsFlow(artistId: Long): Flow<Boolean>

    @Query("select not exists(select 1 from track where track.dropboxPath is not null and (sourcePath is '' or sourcePath like 'https://%.dl.dropboxusercontent.com/%') and track.albumId in (select id from album where album.artistId = :artistId group by album.id))")
    fun isAllIncludingTracksDownloadedAsFlow(artistId: Long): Flow<Boolean>

    @Query("select artworkUriString from album where artistId = :artistId and artworkUriString is not null order by playbackCount limit 1")
    suspend fun getThumbnailUriString(artistId: Long): String

    @Query("update artist set playbackCount = (select playbackCount from artist where id = :artistId) + 1, artworkUriString = (select artworkUriString from album where artistId = :artistId order by playbackCount desc limit 1) where id = :artistId")
    suspend fun increasePlaybackCount(artistId: Long)

    @Query("update artist set totalDuration = 0")
    suspend fun resetTotalDurations()

    @Transaction
    suspend fun deleteRecursively(artistId: Long) {
        deleteTrackByArtist(artistId)
        deleteAlbumByArtist(artistId)
        delete(artistId)
    }

    @Query("select sourcePath from track where (artistId = :artistId or albumArtistId = :artistId)")
    suspend fun getContainTrackIds(artistId: Long): List<String>

    @Transaction
    suspend fun upsert(db: DB, newArtist: Artist, durationToAdd: Long = 0): Long {
        val existingArtist = getByTitle(newArtist.title)
        val id = existingArtist?.let { existing ->
            val artworkUriString = db.albumDao()
                .getAllByArtistId(existingArtist.id)
                .maxByOrNull { it.album.playbackCount }
                ?.album
                ?.artworkUriString
                ?: newArtist.artworkUriString
            val target = newArtist.copy(
                id = existing.id,
                playbackCount = existing.playbackCount,
                totalDuration = existing.totalDuration + durationToAdd,
                artworkUriString = artworkUriString
            )
            update(target)
            existing.id
        } ?: insert(newArtist.copy(totalDuration = durationToAdd))

        return id
    }

    @Transaction
    suspend fun refreshTotalDurations(db: DB) {
        resetTotalDurations()
        db.trackDao().getAll().forEach {
            upsert(db, it.artist, it.track.duration)
        }
    }

    @Query("select id from artist")
    suspend fun getAllIds(): List<Long>
}