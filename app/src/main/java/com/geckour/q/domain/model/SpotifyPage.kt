package com.geckour.q.domain.model

import com.geckour.q.data.db.model.SpotifyTrack

data class SpotifyTrackPage(
    val items: List<SpotifyTrack>,
    val nextOffset: Int?,
)

data class SpotifyContainerPage(
    val items: List<SpotifyContainer>,
    val nextOffset: Int?,
)

data class SpotifySearchPage(
    val tracks: List<SpotifyTrack>,
    val albums: List<SpotifyContainer>,
    val artists: List<SpotifyContainer>,
    val playlists: List<SpotifyContainer>,
    val nextOffset: Int?,
)
