package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.AudioDeviceEqualizerInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDeviceEqualizerInfoDao {

    @Query("select * from audioDeviceEqualizerInfo where routeId = :routeId and ((:deviceAddress is not null and deviceAddress = :deviceAddress) or deviceId = :deviceId) order by id desc")
    fun getAsFlow(
        routeId: String,
        deviceId: Int,
        deviceAddress: String?
    ): Flow<AudioDeviceEqualizerInfo>

    @Query("select * from audioDeviceEqualizerInfo where routeId = :routeId and ((:deviceAddress is not null and deviceAddress = :deviceAddress) or deviceId = :deviceId) order by id desc")
    suspend fun get(
        routeId: String,
        deviceId: Int,
        deviceAddress: String?
    ): AudioDeviceEqualizerInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audioDeviceEqualizerInfo: AudioDeviceEqualizerInfo)

    @Transaction
    suspend fun upsertByCustomConflictDetection(audioDeviceEqualizerInfo: AudioDeviceEqualizerInfo) {
        val existing = get(
            audioDeviceEqualizerInfo.routeId,
            audioDeviceEqualizerInfo.deviceId,
            audioDeviceEqualizerInfo.deviceAddress
        )

        upsert(
            existing?.copy(defaultEqualizerPresetId = audioDeviceEqualizerInfo.defaultEqualizerPresetId)
                ?: audioDeviceEqualizerInfo
        )
    }

    @Query("delete from audioDeviceEqualizerInfo where routeId = :routeId and ((:deviceAddress is not null and deviceAddress = :deviceAddress) or deviceId = :deviceId)")
    suspend fun deleteBy(
        routeId: String,
        deviceId: Int,
        deviceAddress: String?
    )
}