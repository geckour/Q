package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AudioDeviceEqualizerInfo(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val routeId: String,
    val deviceAddress: String?,
    val deviceId: Int,
    val defaultEqualizerPresetId: Long?,
)
