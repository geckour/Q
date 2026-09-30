package com.geckour.q.spotify.library

import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.spotify.model.SpotifyContainer

private const val SPOTIFY_LIBRARY_URI_PREFIX = "q:spotify-library:"

private const val SPOTIFY_LIBRARY_ARTIST_URI_PREFIX = "${SPOTIFY_LIBRARY_URI_PREFIX}artist:"

private const val SPOTIFY_LIBRARY_ALBUM_URI_PREFIX = "${SPOTIFY_LIBRARY_URI_PREFIX}album:"

private val spotifyLibraryTrackComparator =
    compareBy<SpotifyTrack> { it.albumName.lowercase() }
        .thenBy(nullsLast<Int>()) { it.discNum }
        .thenBy(nullsLast<Int>()) { it.trackNum }
        .thenBy { it.title.lowercase() }

val SpotifyContainer.Kind.isInLibrary: Boolean
    get() = this == SpotifyContainer.Kind.LIBRARY_ARTIST ||
            this == SpotifyContainer.Kind.LIBRARY_ALBUM

val String.isSpotifyLibraryUri: Boolean get() = startsWith(SPOTIFY_LIBRARY_URI_PREFIX)

fun List<SpotifyTrack>.toSpotifyLibraryArtists(): List<SpotifyContainer> =
    groupBy { it.spotifyLibraryArtistUri }
        .map { (uri, tracks) ->
            val artistName = tracks.first().spotifyLibraryArtistName
            SpotifyContainer(
                kind = SpotifyContainer.Kind.LIBRARY_ARTIST,
                id = uri,
                uri = uri,
                name = artistName,
                creatorName = null,
                artworkUrl = tracks.firstNotNullOfOrNull { it.artworkUrl },
                releaseDate = null,
                totalTracks = tracks.size,
                totalDuration = tracks.sumOf { it.duration },
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<SpotifyTrack>.toSpotifyLibraryAlbums(): List<SpotifyContainer> =
    groupBy { it.spotifyLibraryAlbumUri }
        .map { (uri, tracks) ->
            val track = tracks.first()
            SpotifyContainer(
                kind = SpotifyContainer.Kind.LIBRARY_ALBUM,
                id = uri,
                uri = uri,
                name = track.albumName,
                creatorName = track.spotifyLibraryArtistName,
                artworkUrl = tracks.firstNotNullOfOrNull { it.artworkUrl },
                releaseDate = tracks.firstNotNullOfOrNull { it.releaseDate },
                totalTracks = tracks.size,
                totalDuration = tracks.sumOf { it.duration },
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<SpotifyTrack>.filterInSpotifyLibraryContainer(
    container: SpotifyContainer,
): List<SpotifyTrack> =
    filter { track ->
        when (container.kind) {
            SpotifyContainer.Kind.LIBRARY_ARTIST -> track.spotifyLibraryArtistUri == container.uri
            SpotifyContainer.Kind.LIBRARY_ALBUM -> track.spotifyLibraryAlbumUri == container.uri
            else -> false
        }
    }.sortedWith(spotifyLibraryTrackComparator)

private val SpotifyTrack.spotifyLibraryArtistName: String
    get() = albumArtistName ?: artistName

private val SpotifyTrack.spotifyLibraryArtistUri: String
    get() = SPOTIFY_LIBRARY_ARTIST_URI_PREFIX + spotifyLibraryArtistName.hexEncoded

private val SpotifyTrack.spotifyLibraryAlbumUri: String
    get() = SPOTIFY_LIBRARY_ALBUM_URI_PREFIX +
            "${spotifyLibraryArtistName.hexEncoded}:${albumName.hexEncoded}"

private val String.hexEncoded: String
    get() = toByteArray().joinToString("") { "%02x".format(it) }
