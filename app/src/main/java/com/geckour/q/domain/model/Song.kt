package com.geckour.q.domain.model

import com.geckour.q.util.getTimeString

data class Song(
        val id: Long,
        val mediaId: Long,
        val albumId: Long,
        val name: String?,
        val artist: String,
        val thumbUriString: String?,
        val duration: Long,
        val trackNum: Int?,
        val discNum: Int?,
        val genreId: Long?,
        val playlistId: Long?,
        val sourcePath: String,
        val nowPlaying: Boolean = false
) {
    val durationString: String get() = duration.getTimeString()
}