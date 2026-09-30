package com.geckour.q.domain.model

import com.geckour.q.core.model.MediaItem

data class SearchItem(
    val title: String,
    val data: MediaItem,
    val type: SearchItemType
) {
    enum class SearchItemType {
        CATEGORY,
        ARTIST,
        ALBUM,
        TRACK,
        GENRE,
        LYRIC,
        SPOTIFY_TRACK,
        SPOTIFY_ALBUM,
        SPOTIFY_ARTIST,
        SPOTIFY_PLAYLIST,
    }
}