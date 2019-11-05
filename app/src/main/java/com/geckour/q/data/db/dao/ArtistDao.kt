package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist

@Dao
interface ArtistDao {

    @Insert
    fun insert(artist: Artist): Long

    @Update
    fun update(artist: Artist): Int

    @Query("delete from artist where id = :id")
    fun delete(id: Long): Int

    @Query("select * from artist where title like :title")
    fun findLikeTitle(title: String): List<Artist>

    @Query("select * from artist where title = :title")
    fun findArtist(title: String): List<Artist>

    @Query("select * from artist")
    fun getAll(): List<Artist>

    @Query("select * from artist where id = :id")
    fun get(id: Long): Artist?

    @Query("update artist set playbackCount = (select playbackCount from artist where id = :artistId) + 1 where id = :artistId")
    fun increasePlaybackCount(artistId: Long)
}

fun Artist.upsert(db: DB): Long = title?.let {
    db.artistDao().findArtist(it).firstOrNull()?.let { artist ->
        db.artistDao().update(this.copy(id = artist.id, playbackCount = playbackCount))
        artist.id
    } ?: db.artistDao().insert(this)
} ?: -1