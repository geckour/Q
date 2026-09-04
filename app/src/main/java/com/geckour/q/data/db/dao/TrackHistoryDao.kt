package com.geckour.q.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.JoinedTrackHistory
import com.geckour.q.data.db.model.TrackHistory

@Dao
interface TrackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trackHistory: TrackHistory): Long

    @Transaction
    @Query("select trackhistory.* from trackhistory inner join track on track.id = trackhistory.trackId order by trackhistory.createdAt desc")
    fun getAllAsPagingSource(): PagingSource<Int, JoinedTrackHistory>

    @Query("select * from trackhistory order by id desc limit 1")
    suspend fun getLatest(): TrackHistory?
}
