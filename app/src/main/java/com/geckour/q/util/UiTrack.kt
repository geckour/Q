package com.geckour.q.util

import androidx.media3.common.MediaItem
import com.geckour.q.core.util.releaseDates
import com.geckour.q.data.db.BoolConverter
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedSavedQueueTrack
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.JoinedTrackHistory
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.data.db.model.TrackRef
import com.geckour.q.domain.model.UiTrack
import java.util.Locale
import kotlin.random.Random

private val random = Random(System.currentTimeMillis())

fun JoinedTrack.toUiTrack(
    trackNum: Int? = null,
    nowPlaying: Boolean = false
): UiTrack {
    val (year, month, day) = track.releaseDate.releaseDates
    return UiTrack(
        "${random.nextLong()}-${track.id}",
        track.id,
        track.mediaId,
        track.codec.uppercase(Locale.getDefault()),
        track.bitrate,
        track.sampleRate / 1000f,
        album,
        track.title,
        track.titleSort,
        artist,
        albumArtist,
        track.composer,
        track.composerSort,
        album.artworkUriString,
        track.duration,
        trackNum ?: track.trackNum,
        track.trackTotal,
        track.discNum,
        track.discTotal,
        year,
        month,
        day,
        track.genre,
        track.sourcePath,
        track.dropboxPath,
        track.dropboxExpiredAt,
        track.artworkUriString,
        BoolConverter().toBoolean(track.ignored),
        nowPlaying,
        isFavorite = track.isFavorite
    )
}

fun SpotifyTrack.toUiTrack(nowPlaying: Boolean = false): UiTrack {
    val (year, month, day) = releaseDate.releaseDates
    val album = Album(
        id = 0,
        artistId = 0,
        title = albumName,
        titleSort = albumName,
        artworkUriString = artworkUrl,
        hasAlbumArtist = albumArtistName != null,
        playbackCount = 0,
        totalDuration = 0,
    )
    return UiTrack(
        key = "${random.nextLong()}-$uri",
        id = 0,
        mediaId = -1,
        codec = "Spotify",
        bitrate = 0,
        sampleRate = 0f,
        album = album,
        title = title,
        titleSort = title,
        artist = spotifyArtist(artistName),
        albumArtist = albumArtistName?.let { spotifyArtist(it) },
        composer = null,
        composerSort = null,
        thumbUriString = artworkUrl,
        duration = duration,
        trackNum = trackNum,
        trackTotal = trackTotal,
        discNum = discNum,
        discTotal = null,
        releaseYear = year,
        releaseMonth = month,
        releaseDay = day,
        genreName = null,
        sourcePath = uri,
        dropboxPath = null,
        dropboxExpiredAt = null,
        artworkUriString = artworkUrl,
        ignored = false,
        nowPlaying = nowPlaying,
        isFavorite = isFavorite,
    )
}

fun JoinedTrackHistory.toUiTrack(): UiTrack? =
    joinedTrack?.toUiTrack() ?: spotifyTrack?.toUiTrack()

fun JoinedSavedQueueTrack.toUiTrack(): UiTrack? =
    joinedTrack?.toUiTrack() ?: spotifyTrack?.toUiTrack()

val UiTrack.trackRef: TrackRef
    get() = if (isSpotify) TrackRef.ofSpotify(sourcePath) else TrackRef(id)

private fun SpotifyTrack.spotifyArtist(name: String): Artist =
    Artist(
        id = 0,
        title = name,
        titleSort = name,
        playbackCount = 0,
        totalDuration = 0,
        artworkUriString = artworkUrl,
    )

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun MediaItem.toUiTrack(db: DB): UiTrack? =
    (localConfiguration?.uri ?: mediaId).toString().toUiTrack(db)

suspend fun String.toUiTrack(db: DB): UiTrack? =
    if (isSpotifySourcePath) db.spotifyTrackDao().get(this)?.toUiTrack()
    else db.trackDao().getBySourcePath(this)?.toUiTrack()

suspend fun List<String>.toDomainTracks(db: DB): List<UiTrack> {
    val (spotifyUris, sourcePaths) = partition { it.isSpotifySourcePath }
    return db.trackDao().getAllBySourcePaths(sourcePaths).map { it.toUiTrack() } +
            db.spotifyTrackDao().getAllByUris(spotifyUris).map { it.toUiTrack() }
}
