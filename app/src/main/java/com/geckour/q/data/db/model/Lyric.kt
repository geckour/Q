package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
data class Lyric(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val trackId: Long,
    val lines: List<LyricLine>
)

@Serializable
data class LyricLine(
    val timing: Long,
    val sentence: String
)
