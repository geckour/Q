package com.geckour.q.data.db.dao

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

    @Query("select * from artist where id in (select artistId from album group by id) order by titleSort collate nocase")
    fun getAllOrientedAlbumAsync(): Flow<List<Artist>>

    @Query("select artworkUriString from album where artistId = :artistId and artworkUriString is not null order by playbackCount limit 1")
    suspend fun getThumbnailUriString(artistId: Long): String

    @Query("update artist set playbackCount = (select playbackCount from artist where id = :artistId) + 1 where id = :artistId")
    suspend fun increasePlaybackCount(artistId: Long)

    @Transaction
    suspend fun deleteRecursively(artistId: Long) {
        deleteTrackByArtist(artistId)
        deleteAlbumByArtist(artistId)
        delete(artistId)
    }

    @Transaction
    suspend fun upsert(db: DB, artist: Artist, durationToAdd: Long = 0): Long {
        val existingArtist = getByTitle(artist.title)
        existingArtist?.let {
            update(
                artist.copy(
                    id = it.id,
                    playbackCount = it.playbackCount,
                    totalDuration = it.totalDuration + durationToAdd,
                    artworkUriString = artist.artworkUriString ?: it.artworkUriString
                )
            )
        }

        return existingArtist?.id ?: insert(artist.copy(totalDuration = durationToAdd))
    }
}