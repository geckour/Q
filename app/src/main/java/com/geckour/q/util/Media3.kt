package com.geckour.q.util

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.geckour.q.core.util.releaseDates
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.spotify.isSpotifySourcePath

suspend fun String.getMediaItem(context: Context): MediaItem =
    getMediaItemOrNull(context) ?: this.getMediaItem()

suspend fun String.getMediaItemOrNull(context: Context): MediaItem? {
    val db = DB.getInstance(context)
    return if (isSpotifySourcePath) db.spotifyTrackDao().get(this)?.getMediaItem()
    else db.trackDao().getBySourcePath(this)?.getMediaItem()
}

private fun String.getMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(this)
    .setUri(this.toUri())
    .build()

fun JoinedTrack.getMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.sourcePath)
        .setUri(track.sourcePath.toUri())
        .setMediaMetadata(getMediaMetadata())
        .build()

fun SpotifyTrack.getMediaItem(): MediaItem {
    val (year, month, day) = releaseDate.releaseDates

    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setSubtitle(artistName)
                .setDescription(albumName)
                .setArtist(artistName)
                .setAlbumArtist(albumArtistName)
                .setAlbumTitle(albumName)
                .setReleaseYear(year)
                .setReleaseMonth(month)
                .setReleaseDay(day)
                .setArtworkUri(artworkUrl?.toUri())
                .setDurationMs(duration)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply {
                    trackNum?.let { setTrackNumber(it) }
                    trackTotal?.let { setTotalTrackCount(it) }
                    discNum?.let { setDiscNumber(it) }
                }
                .build()
        )
        .build()
}

private fun JoinedTrack.getMediaMetadata(): MediaMetadata {
    val (year, month, day) = track.releaseDate.releaseDates

    return MediaMetadata.Builder()
        .setTitle(track.title)
        .setDisplayTitle(track.title)
        .setSubtitle(artist.title)
        .setDescription(album.title)
        .setArtist(artist.title)
        .setAlbumArtist(albumArtist?.title)
        .setAlbumTitle(album.title)
        .setComposer(track.composer)
        .setReleaseYear(year)
        .setReleaseMonth(month)
        .setReleaseDay(day)
        .setArtworkUri((track.artworkUriString ?: album.artworkUriString)?.toUri())
        .setGenre(track.genre)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply {
            track.trackNum?.let { setTrackNumber(it) }
            track.trackTotal?.let { setTotalTrackCount(it) }
            track.discNum?.let { setDiscNumber(it) }
            track.discTotal?.let { setTotalDiscCount(it) }
        }
        .build()
}

val ExoPlayer.currentSourcePaths: List<String>
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) get() = List(this.mediaItemCount) { index ->
        this.getMediaItemAt(index).localConfiguration?.uri?.toString()
    }.filterNotNull()
