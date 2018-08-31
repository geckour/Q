package com.geckour.q.data.dao

import android.arch.persistence.room.*
import com.geckour.q.data.model.Artist

@Dao
interface ArtistDao {
    @Insert
    fun insert(artist: Artist)

    @Update
    fun update(artist: Artist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(artist: Artist)

    @Query("select exists(select * from artist where title = :title)")
    fun findArtist(title: String): Artist?
}