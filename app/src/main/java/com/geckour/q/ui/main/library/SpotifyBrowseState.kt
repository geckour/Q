package com.geckour.q.ui.main.library

import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.SpotifyContainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class SpotifyBrowseSource {
    SAVED,
    PLAYLISTS,
    RECOMMENDED,
}

sealed interface SpotifyBrowseItem {

    data class Track(val track: SpotifyTrack) : SpotifyBrowseItem

    data class Container(val container: SpotifyContainer) : SpotifyBrowseItem
}

data class SpotifyBrowseState(
    val source: SpotifyBrowseSource? = null,
    val items: ImmutableList<SpotifyBrowseItem> = persistentListOf(),
    val nextOffset: Int? = null,
    val containerStack: ImmutableList<SpotifyContainer> = persistentListOf(),
    val containerItems: ImmutableList<SpotifyBrowseItem> = persistentListOf(),
    val containerNextOffset: Int? = null,
    val isFlattened: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastAddedTitle: String? = null,
)

val SpotifyBrowseState.container: SpotifyContainer? get() = containerStack.lastOrNull()

val SpotifyBrowseItem.artworkUrl: String?
    get() = when (this) {
        is SpotifyBrowseItem.Track -> track.artworkUrl
        is SpotifyBrowseItem.Container -> container.artworkUrl
    }

val SpotifyBrowseItem.key: String
    get() = when (this) {
        is SpotifyBrowseItem.Track -> track.uri
        is SpotifyBrowseItem.Container -> container.uri
    }
