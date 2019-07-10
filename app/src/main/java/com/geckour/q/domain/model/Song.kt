package com.geckour.q.domain.model

import android.os.Parcelable
import com.geckour.q.util.getTimeString
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Song(
        val id: Long,
        val mediaId: Long,
        val albumId: Long,
        val name: String?,
        val artist: String,
        val composer: String?,
        val thumbUriString: String?,
        val duration: Long,
        val trackNum: Int?,
        val discNum: Int?,
        val genreId: Long?,
        val playlistId: Long?,
        val sourcePath: String,
        val ignored: Boolean?,
        val nowPlaying: Boolean = false
) : Parcelable, MediaItem {

    val durationString: String get() = duration.getTimeString()
}