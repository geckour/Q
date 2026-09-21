package com.geckour.q.ui.main.library

import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.SpotifyContainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

enum class SpotifyBrowseSource {
    SAVED,
    PLAYLISTS,
    RECOMMENDED,
}

sealed interface SpotifyBrowseItem {

    data class Track(val track: SpotifyTrack) : SpotifyBrowseItem

    data class Container(val container: SpotifyContainer) : SpotifyBrowseItem
}

data class SpotifyLevel(
    val items: ImmutableList<SpotifyBrowseItem> = persistentListOf(),
    val nextOffset: Int? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val hasFailed: Boolean = false,
)

data class SpotifyBrowseState(
    val levels: ImmutableMap<String, SpotifyLevel> = persistentMapOf(),
)

fun SpotifyBrowseState.level(key: String): SpotifyLevel = levels[key] ?: SpotifyLevel()

val SpotifyBrowseSource.levelKey: String get() = name

val SpotifyContainer.levelKey: String get() = uri

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
