package com.geckour.q.domain.model

data class SearchItem(
    val title: String,
    val data: MediaItem,
    val type: SearchItemType
) {
    enum class SearchItemType {
        CATEGORY,
        ARTIST,
        ALBUM,
        TRACK
    }
}