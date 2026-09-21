package com.geckour.q.domain.model

data class SpotifyContentItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

data class SpotifyContentPage(
    val items: List<SpotifyContentItem>,
    val nextOffset: Int?,
)
