package com.geckour.q.util

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.geckour.q.domain.model.LyricLine
import timber.log.Timber
import java.io.File

fun File.parseLrc(): List<LyricLine> =
    if (extension != "lrc") emptyList()
    else readLines()
        .map { line ->
            if (line.matches(Regex("^(\\[\\d+?:\\d+?\\.\\d+?])+.*$")).not()) return@map emptyList()

            val timings = Regex("^((\\[\\d+?:\\d+?\\.\\d+?])+).*$").find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { timingStrings ->
                    Regex("\\[\\d+?:\\d+?\\.\\d+?]").findAll(timingStrings)
                        .map { timingString ->
                            timingString.value
                                .split("[", ":", ".", "]")
                                .let { it[1].toLong() * 60000 + it[2].toLong() * 1000 + it[3].toLong() * 10 }
                        }
                }
                ?.toList()
                .orEmpty()
            val sentence = line.replace(Regex("^(\\[\\d+?:\\d+?\\.\\d+?])+(.*)$"), "$2")
            timings.map { LyricLine(it, sentence) }
        }.flatten()
        .sortedBy { it.timing }

private val AppCompatActivity.getContent
    get() = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val dir = File(cacheDir, "lrc")
        val file = File(dir, "sample.lrc")

        if (file.exists()) file.delete()
        if (dir.exists().not()) dir.mkdirs()

        contentResolver.openInputStream(uri)?.use {
            file.writeBytes(it.readBytes())
        }
        val lrc = file.parseLrc()
    }