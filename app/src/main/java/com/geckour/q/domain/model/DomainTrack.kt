package com.geckour.q.domain.model

import android.os.Parcelable
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.util.getTimeString
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class DomainTrack(
    val id: Long,
    val mediaId: Long,
    val codec: String,
    val bitrate: Long,
    val sampleRate: Float,
    val album: Album,
    val title: String,
    val titleSort: String,
    val artist: Artist,
    val composer: String?,
    val composerSort: String?,
    val thumbUriString: String?,
    val duration: Long,
    val trackNum: Int?,
    val trackTotal: Int?,
    val discNum: Int?,
    val discTotal: Int?,
    val releaseDate: String?,
    val genreId: Long?,
    val sourcePath: String,
    val dropboxPath: String?,
    val dropboxExpiredAt: Long?,
    val artworkUriString: String?,
    val ignored: Boolean?,
    val nowPlaying: Boolean = false
) : Parcelable, MediaItem {

    val durationString: String get() = duration.getTimeString()
}