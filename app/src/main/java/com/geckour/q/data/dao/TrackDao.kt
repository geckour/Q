package com.geckour.q.data.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Update
import com.geckour.q.data.model.Track

@Dao
interface TrackDao {
    @Insert
    fun insert(track: Track)

    @Update
    fun update(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(track: Track)
}