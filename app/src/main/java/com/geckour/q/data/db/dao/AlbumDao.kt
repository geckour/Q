package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.JoinedAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: Album): Long

    @Update
    suspend fun update(album: Album): Int

    @Query("delete from album where id = :id")
    suspend fun delete(id: Long): Int

    @Transaction
    @Query("delete from album where id in (:ids)")
    suspend fun deleteAllByIds(ids: List<Long>): Int

    @Query("delete from track where albumId = :albumId")
    suspend fun deleteTrackByAlbum(albumId: Long): Int

    @Transaction
    @Query("select * from album where id = :id")
    suspend fun get(id: Long): JoinedAlbum?

    @Transaction
    @Query("select * from album where id in (:ids)")
    suspend fun getAllByIds(ids: List<Long>): List<JoinedAlbum>

    @Transaction
    @Query("select * from album where title like :title")
    suspend fun findByTitle(title: String): JoinedAlbum?

    @Transaction
    @Query("select * from album")
    fun getAllAsync(): Flow<List<JoinedAlbum>>

    @Transaction
    @Query("select * from album where artistId = :artistId")
    suspend fun getAllByArtistId(artistId: Long): List<JoinedAlbum>

    @Transaction
    @Query("select * from album where artistId = :artistId")
    fun getAllByArtistIdAsync(artistId: Long): Flow<List<JoinedAlbum>>

    @Transaction
    @Query("select * from album where title = :title and artistId = :artistId")
    suspend fun getAllByTitleAndArtistId(title: String, artistId: Long): List<JoinedAlbum>

    @Transaction
    @Query("select * from album where title like :title")
    suspend fun findAllByTitle(title: String): List<JoinedAlbum>

    @Query("update album set playbackCount = (select playbackCount from album where id = :albumId) + 1 where id = :albumId")
    suspend fun increasePlaybackCount(albumId: Long)

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(db: DB, vararg albumIds: Long) {
        val albums = getAllByIds(albumIds.toList())
        deleteAllByIds(albumIds.toList())

        albums.forEach {
            if (getAllByArtistId(it.album.artistId).isEmpty()) {
                db.artistDao().delete(it.album.artistId)
            } else {
                db.artistDao()
                    .update(
                        it.artist.copy(
                            totalDuration = it.artist.totalDuration - it.album.totalDuration
                        )
                    )
            }
        }
    }

    @Transaction
    suspend fun deleteRecursively(db: DB, albumId: Long) {
        deleteTrackByAlbum(albumId)
        deleteIncludingRootIfEmpty(db, albumId)
    }

    @Transaction
    suspend fun upsert(db: DB, album: Album, pastTrackDuration: Long = 0): Long {
        val toInsert = getAllByTitleAndArtistId(album.title, album.artistId).let { albums ->
            val firstAlbum = albums.firstOrNull() ?: return@let null
            albums.drop(1).asSequence().forEach {
                val duration =
                    firstAlbum.album.totalDuration - pastTrackDuration + it.album.totalDuration
                val target = it.album.copy(
                    id = firstAlbum.album.id,
                    totalDuration = duration,
                    artworkUriString = it.album.artworkUriString
                        ?: firstAlbum.album.artworkUriString
                )
                insert(target).apply {
                    db.trackDao().getAllByAlbum(it.album.id).asSequence()
                        .forEach { joinedTrack ->
                            db.trackDao().update(joinedTrack.track.copy(albumId = this))
                        }
                    delete(it.album.id)
                }
            }
            val duration = firstAlbum.album.totalDuration - pastTrackDuration + album.totalDuration
            album.copy(
                id = firstAlbum.album.id,
                totalDuration = duration,
                artworkUriString = album.artworkUriString ?: firstAlbum.album.artworkUriString
            )
        } ?: album

        return insert(toInsert).apply {
            if (album.id > 0 && this != album.id) {
                db.trackDao().getAllByAlbum(album.id).asSequence().forEach {
                    db.trackDao().update(it.track.copy(albumId = this))
                }
                delete(album.id)
            }
        }
    }
}