package com.geckour.q.util

import android.content.Context
import androidx.media3.common.MediaItem
import com.geckour.q.core.util.dailyRandom
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.data.db.model.TrackRef
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.spotify.isSpotifyConfigured
import kotlinx.coroutines.flow.first
import kotlin.random.Random

private const val MAX_DAILY_PICK_ATTEMPTS = 10

sealed interface DailyPick {

    data class Local(val track: JoinedTrack) : DailyPick

    data class Spotify(val track: SpotifyTrack) : DailyPick
}

val DailyPick.trackRef: TrackRef
    get() = when (this) {
        is DailyPick.Local -> TrackRef(track.track.id)
        is DailyPick.Spotify -> TrackRef.ofSpotify(track.uri)
    }

fun DailyPick.toUiTrack(): UiTrack = when (this) {
    is DailyPick.Local -> track.toUiTrack()
    is DailyPick.Spotify -> track.toUiTrack()
}

fun DailyPick.toMediaItem(): MediaItem = when (this) {
    is DailyPick.Local -> track.getMediaItem()
    is DailyPick.Spotify -> track.getMediaItem()
}

suspend fun Context.canPickSpotifyTracks(): Boolean =
    isSpotifyConfigured &&
            getIsSpotifyUnlocked().first() &&
            getSpotifyCredential().first() != null

suspend fun pickDailyTrack(context: Context, random: Random = dailyRandom): DailyPick? {
    val db = DB.getInstance(context)
    val spotifyTracks =
        if (context.canPickSpotifyTracks()) db.spotifyTrackDao().getAllInLibrary()
        else emptyList()
    if (spotifyTracks.isEmpty()) {
        return db.trackDao().getByRandom(db, random)?.let { DailyPick.Local(it) }
    }

    repeat(MAX_DAILY_PICK_ATTEMPTS) {
        pickDailyTrack(db, spotifyTracks, random)?.let { return it }
    }

    return null
}

private suspend fun pickDailyTrack(
    db: DB,
    spotifyTracks: List<SpotifyTrack>,
    random: Random,
): DailyPick? {
    val localArtistIdsByName = db.artistDao().getAllOrientedAlbum()
        .groupBy({ it.title }, { it.id })
    val spotifyTracksByArtistName = spotifyTracks.groupBy { it.albumArtistName ?: it.artistName }

    return pickByArtistAndAlbum(
        random = random,
        artistNames = localArtistIdsByName.keys + spotifyTracksByArtistName.keys,
        albumGroupsOf = { artistName ->
            val localAlbumIdsByTitle = localArtistIdsByName[artistName].orEmpty()
                .flatMap { db.albumDao().getAllByArtistId(it) }
                .groupBy({ it.album.title }, { it.album.id })
            val spotifyTracksByAlbumName =
                spotifyTracksByArtistName[artistName].orEmpty().groupBy { it.albumName }

            (localAlbumIdsByTitle.keys + spotifyTracksByAlbumName.keys).associateWith { title ->
                suspend {
                    localAlbumIdsByTitle[title].orEmpty()
                        .flatMap { db.trackDao().getAllByAlbum(it) }
                        .map { DailyPick.Local(it) } +
                            spotifyTracksByAlbumName[title].orEmpty()
                                .sortedWith(
                                    compareBy({ it.discNum }, { it.trackNum }, { it.title })
                                )
                                .map { DailyPick.Spotify(it) }
                }
            }
        },
    )
}

internal suspend fun <T> pickByArtistAndAlbum(
    random: Random,
    artistNames: Set<String>,
    albumGroupsOf: suspend (artistName: String) -> Map<String, suspend () -> List<T>>,
): T? {
    if (artistNames.isEmpty()) return null

    val sortedArtistNames = artistNames.sorted()
    val artistName = sortedArtistNames[random.nextInt(sortedArtistNames.size)]

    val albumGroups = albumGroupsOf(artistName)
    if (albumGroups.isEmpty()) return null

    val albumTitles = albumGroups.keys.sorted()
    val candidates = albumGroups.getValue(albumTitles[random.nextInt(albumTitles.size)])()
    if (candidates.isEmpty()) return null

    return candidates[random.nextInt(candidates.size)]
}
