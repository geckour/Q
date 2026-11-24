package com.geckour.q.data.db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geckour.q.core.util.normalizedForSearch
import kotlinx.serialization.Serializable

/**
 * @property searchText The text the lyric is searched by, made of [lines] by [toSearchText].
 * [com.geckour.q.data.db.dao.LyricDao.upsertLyric] keeps it in sync with [lines].
 */
@Entity
data class Lyric(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val trackId: Long,
    val spotifyUri: String? = null,
    val lines: List<LyricLine>,
    @ColumnInfo(defaultValue = "LOCAL") val source: LyricSource = LyricSource.LOCAL,
    @ColumnInfo(defaultValue = "") val searchText: String = "",
)

@Serializable
data class LyricLine(
    val timing: Long,
    val sentence: String
)

/**
 * Makes the text to search the lyric by: the sentences normalized by [normalizedForSearch], one per
 * line. As the normalized sentences keep letters and digits only, a keyword normalized in the same
 * way never matches across lines, and holds no wildcards of `like`.
 */
internal fun List<LyricLine>.toSearchText(): String =
    joinToString("\n") { it.sentence.normalizedForSearch() }

fun List<LyricLine>.shiftedBy(delta: Long): List<LyricLine> =
    sortedBy { it.timing }.map {
        if (it.timing == 0L) it
        else it.copy(timing = (it.timing + delta).coerceAtLeast(1))
    }

enum class LyricSource {
    LOCAL,
    LRCLIB
}
