package com.geckour.q.ui.main

sealed interface TrackSource {

    data class OfAlbum(val albumId: Long) : TrackSource

    data class OfArtist(val artistId: Long) : TrackSource

    data object All : TrackSource

    data class OfGenre(val genreName: String) : TrackSource
}
