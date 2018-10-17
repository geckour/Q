package com.geckour.q.util

import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import kotlin.math.abs
import com.geckour.q.data.db.model.Album as DBAlbum
import com.geckour.q.data.db.model.Artist as DBArtist

fun Float.getReadableString(digitToKeep: Int = 3): String {
    fun Float.format(suffix: String): String =
            String.format("${if (this@getReadableString < 0) "-" else ""}%.${digitToKeep}f", this)
                    .replace(Regex("^(.+\\..*?)0+$"), "$1")
                    .replace(Regex("^(.+)\\.$"), "$1") + suffix

    var returnValue = abs(this)
    var count = 0

    return if (returnValue < 1) {
        val suffixList: List<String> = listOf("", "m", "μ", "n", "p", "f", "a", "z", "y")
        while (returnValue < 1) {
            returnValue *= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        returnValue.format(suffix)
    } else {
        val suffixList: List<String> = listOf("", "k", "M", "G", "T", "P", "E", "Z", "Y")
        while (returnValue >= 1000) {
            returnValue /= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        returnValue.format(suffix)
    }
}

fun DBArtist.toDomainModel(): Artist =
        Artist(this.id, this.title ?: UNKNOWN, null, 0)

fun DBAlbum.toDomainModel(): Album =
        Album(this.id, this.mediaId, this.title, null, this.artworkUriString, 0)