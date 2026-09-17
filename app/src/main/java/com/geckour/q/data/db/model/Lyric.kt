package com.geckour.q.data.db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
data class Lyric(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val trackId: Long,
    val lines: List<LyricLine>,
    @ColumnInfo(defaultValue = "LOCAL") val source: LyricSource = LyricSource.LOCAL
)

@Serializable
data class LyricLine(
    val timing: Long,
    val sentence: String
)

fun List<LyricLine>.shiftedBy(delta: Long): List<LyricLine> =
    sortedBy { it.timing }.map {
        if (it.timing == 0L) it
        else it.copy(timing = (it.timing + delta).coerceAtLeast(1))
    }

enum class LyricSource {
    LOCAL,
    LRCLIB
}
