package com.geckour.q.util

import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.data.db.model.shiftedBy
import java.io.File

fun File.parseLrc(): List<LyricLine> =
    if (extension != "lrc") emptyList()
    else readText().parseLrc()

fun String.parseLrc(): List<LyricLine> {
    val offset = lines().firstNotNullOfOrNull { line ->
        Regex("^\\[offset:\\s*([+-]?\\d+)\\s*]$", RegexOption.IGNORE_CASE)
            .find(line.trim())
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    } ?: 0

    return lines()
        .map { line ->
            if (line.matches(Regex("^(\\[\\d+?:\\d+?(\\.\\d+?)?])+.*$"))
                    .not()
            ) return@map emptyList()

            val timings = Regex("^((\\[\\d+?:\\d+?(\\.\\d+?)?])+).*$").find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { timingStrings ->
                    Regex("\\[\\d+?:\\d+?(\\.\\d+?)?]").findAll(timingStrings)
                        .map { timingString ->
                            timingString.value
                                .split("[", ":", ".", "]")
                                .let {
                                    (it[1].toLongOrNull() ?: 0) * 60000 +
                                            (it[2].toLongOrNull() ?: 0) * 1000 +
                                            (it[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0)
                                }
                        }
                }
                ?.toList()
                .orEmpty()
            val sentence = line
                .replace(Regex("^(\\[\\d+?:\\d+?(\\.\\d+?)?])+(.*)$"), "$3")
                .trim()
            timings.map { LyricLine(it, sentence) }
        }.flatten()
        .shiftedBy(-offset)
}

fun Lyric.toLrcString(): String = lines.joinToString("\n") {
    "[%s.%02d]%s".format(it.timing.getTimeString(), it.timing % 1000 / 10, it.sentence)
}
