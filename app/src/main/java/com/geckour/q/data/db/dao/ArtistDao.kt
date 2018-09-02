package com.geckour.q.data.db.dao

import android.arch.persistence.room.*
import com.geckour.q.data.db.model.Artist

@Dao
interface ArtistDao {
    @Insert
    fun insert(artist: Artist)

    @Update
    fun update(artist: Artist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(artist: Artist)

    @Query("select * from artist where title = :title")
    fun findArtist(title: String): List<Artist>

    @Query("select * from artist")
    fun getAll(): List<Artist>

    @Query("select * from artist where id = :id")
    fun get(id: Long): Artist?
}