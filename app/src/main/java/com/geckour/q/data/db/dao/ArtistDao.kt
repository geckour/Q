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

    @Query("select * from artist where title = :title")
    fun findArtist(title: String): List<Artist>

    @Query("select * from artist")
    fun getAll(): List<Artist>

    @Query("select * from artist where id = :id")
    fun get(id: Long): Artist?

    @Query("select * from artist where mediaId = :artistId")
    fun getByMediaId(artistId: Long): Artist?
}

fun Artist.upsert(db: DB): Long? =
        this.mediaId?.let {
            val artist = db.artistDao().getByMediaId(it)
            if (artist != null) {
                db.artistDao().update(this)
                artist.id
            } else db.artistDao().insert(this)
        } ?: run {
            this.title?.let {
                db.artistDao().findArtist(it).let {
                    if (it.isEmpty()) db.artistDao().insert(this)
                    else it.first().id
                }
            }
        }