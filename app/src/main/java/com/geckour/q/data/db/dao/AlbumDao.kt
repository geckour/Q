package com.geckour.q.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.data.db.model.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: Album): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<Album>)

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
    @Query("select * from album order by titleSort collate nocase")
    fun getAllAsPagingSource(): PagingSource<Int, JoinedAlbum>

    @Transaction
    @Query("select * from album order by titleSort collate nocase")
    suspend fun getAll(): List<JoinedAlbum>

    @Transaction
    @Query("select * from album where artistId = :artistId")
    suspend fun getAllByArtistId(artistId: Long): List<JoinedAlbum>

    @Transaction
    @Query("select * from album where album.artistId = :artistId order by titleSort collate nocase")
    fun getAllByArtistIdAsPagingSource(artistId: Long): PagingSource<Int, JoinedAlbum>

    @Transaction
    @Query("select * from album where title = :title and artistId = :artistId")
    suspend fun getAllByTitleAndArtistId(title: String, artistId: Long): List<JoinedAlbum>

    @Transaction
    @Query("select * from album where title like ('%'||:title||'%') escape '\\'")
    suspend fun findAllByTitle(title: String): List<JoinedAlbum>

    @Query("update album set playbackCount = (select playbackCount from album where id = :albumId) + 1, artworkUriString = (select artworkUriString from track where albumId = :albumId order by playbackCount desc limit 1) where id = :albumId")
    suspend fun increasePlaybackCount(albumId: Long)

    @Transaction
    suspend fun deleteIncludingRootIfEmpty(db: DB, vararg albumIds: Long) {
        val albums = getAllByIds(albumIds.toList())
        deleteAllByIds(albumIds.toList())

        albums.forEach {
            if (getAllByArtistId(it.album.artistId).isEmpty()) {
                db.artistDao().delete(it.album.artistId)
            } else {
                db.artistDao().refreshTotalDurations(listOf(it.album.artistId))
            }
        }
    }

    @Query(
        "update album set totalDuration = " +
                "(select coalesce(sum(track.duration), 0) from track " +
                "where track.albumId = album.id) " +
                "where id in (:albumIds)"
    )
    suspend fun refreshTotalDurations(albumIds: List<Long>)

    @Transaction
    suspend fun refreshTotalDurationsIncludingArtists(db: DB, albumIds: List<Long>) {
        refreshTotalDurations(albumIds)
        db.artistDao().refreshTotalDurations(getAllByIds(albumIds).map { it.album.artistId })
    }

    @Query("select sourcePath from track where albumId = :albumId")
    suspend fun getContainTrackIds(albumId: Long): List<String>

    @Transaction
    suspend fun deleteRecursively(db: DB, albumId: Long) {
        deleteTrackByAlbum(albumId)
        deleteIncludingRootIfEmpty(db, albumId)
    }

    @Transaction
    suspend fun upsert(db: DB, newAlbum: Album): Long {
        val existingAlbums = getAllByTitleAndArtistId(newAlbum.title, newAlbum.artistId)
        existingAlbums.forEach {
            val target = newAlbum.copy(
                id = it.album.id,
                playbackCount = it.album.playbackCount,
                totalDuration = it.album.totalDuration,
                artworkUriString = newAlbum.artworkUriString ?: it.album.artworkUriString
            )
            update(target)
        }

        return if (existingAlbums.isEmpty()) {
            insert(newAlbum.copy(totalDuration = 0))
        } else existingAlbums.first().album.id
    }
}