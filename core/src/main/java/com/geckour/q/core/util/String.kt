package com.geckour.q.core.util

import java.text.Normalizer
import java.util.Locale

const val UNKNOWN: String = "UNKNOWN"

/**
 * The suffixes the iTunes Store adds to the album names of singles and EPs, which other services
 * such as Spotify leave out.
 */
private val storeAlbumSuffix = Regex("""\s+-\s+(Single|EP)\s*$""", RegexOption.IGNORE_CASE)

/**
 * Removes the suffix such as " - Single" or " - EP" that the iTunes Store adds to an album name.
 */
fun String.withoutStoreAlbumSuffix(): String = replace(storeAlbumSuffix, "")

/**
 * Normalizes a name to compare it loosely with others: the width, the case, spaces and symbols do
 * not matter.
 */
fun String.normalizedForMatch(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    return buildString {
        normalized.codePoints().forEach { if (Character.isLetterOrDigit(it)) appendCodePoint(it) }
    }
}

/**
 * Normalizes a text to search it loosely: on top of what [normalizedForMatch] ignores, katakana and
 * hiragana do not matter either.
 */
fun String.normalizedForSearch(): String = normalizedForMatch().hiraganized

fun String.getExtension(): String = replace(Regex("^.+\\.(.+?)$"), "$1")

val String.escapeSql: String
    get() = replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
