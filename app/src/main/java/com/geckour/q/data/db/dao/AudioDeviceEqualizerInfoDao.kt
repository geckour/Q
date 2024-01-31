package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.geckour.q.data.db.model.AudioDeviceEqualizerInfo
import com.geckour.q.domain.model.QAudioDeviceInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDeviceEqualizerInfoDao {

    @Query("select * from audioDeviceEqualizerInfo where routeId = :routeId and ((:deviceAddress is not null and deviceAddress = :deviceAddress) or deviceId = :deviceId)")
    fun get(
        routeId: String,
        deviceId: Int,
        deviceAddress: String?
    ): Flow<AudioDeviceEqualizerInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audioDeviceEqualizerInfo: AudioDeviceEqualizerInfo)

    @Query("delete from audioDeviceEqualizerInfo where routeId = :routeId and ((:deviceAddress is not null and deviceAddress = :deviceAddress) or deviceId = :deviceId)")
    suspend fun deleteBy(
        routeId: String,
        deviceId: Int,
        deviceAddress: String?
    )
}