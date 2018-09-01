package com.geckour.q.data.db.dao

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import com.geckour.q.data.db.model.Track

@Dao
interface TrackDao {
    @Insert
    fun insert(track: Track)

    @Update
    fun update(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(track: Track)

    @Query("select * from track")
    fun getAll(): LiveData<List<Track>>
}