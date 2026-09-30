package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class EqualizerPreset(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val label: String,
)

@Entity
data class EqualizerLevelRatio(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val presetId: Long,
    val centerFrequency: Int,
    val ratio: Float,
)
