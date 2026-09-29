package com.geckour.q.domain.model

data class SpotifyContainer(
    val kind: Kind,
    val id: String,
    val uri: String,
    val name: String,
    val creatorName: String?,
    val artworkUrl: String?,
    val releaseDate: String?,
    val totalTracks: Int?,
    val totalDuration: Long? = null,
) : MediaItem {

    enum class Kind {
        ALBUM,
        ARTIST,
        PLAYLIST,
        SAVED,
        CONTENT,
        LIBRARY_ARTIST,
        LIBRARY_ALBUM,
    }
}
