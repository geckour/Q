package com.geckour.q.ui.main

import androidx.navigation.NavHostController
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.domain.model.SpotifyRecommendedRoot
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.main.dialog.DialogEvent
import com.geckour.q.ui.main.dialog.DialogState
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.encodeUrlSafe
import kotlinx.collections.immutable.toImmutableList

interface MainActions {

    fun onSelectNav(nav: Nav?)

    fun onTapBar()

    fun onTapTopBarTitle()

    suspend fun onSearchSpotify(query: String): List<SearchItem>

    fun onToggleTheme()

    fun onChangeTopBarTitle(title: String)

    fun onSetOptionMediaItem(mediaItem: MediaItem?)

    fun onSetOptionArtist(artistId: Long)

    fun onSetOptionAlbum(albumId: Long)

    fun onToggleFavorite(mediaItem: MediaItem?): MediaItem?

    fun onShowDialog(dialogState: DialogState?)

    fun onDialogEvent(event: DialogEvent)

    fun onInvalidateDownloadedArtist(artistId: Long)

    fun onInvalidateDownloadedAlbum(albumId: Long)

    fun onRetrieveMedia(onlyAdded: Boolean)

    fun onStartBilling()

    fun onDeleteSavedQueue(savedQueueId: Long)

    fun onTogglePlayPause()

    fun onPrev()

    fun onNext()

    fun onRewind()

    fun onFastForward()

    fun resetPlaybackButton()

    fun onNewProgress(newProgress: Long)

    fun rotateRepeatMode()

    fun shuffleQueue(actionType: ShuffleActionType?)

    fun resetShuffleQueue()

    fun moveToCurrentIndex()

    fun clearQueue()

    fun onToggleShowLyrics()

    fun onQueueMove(from: Int, to: Int)

    fun onChangeIndexRequested(index: Int)

    fun onRemoveTrackFromQueue(index: Int)
}

fun MainActions.onSelectTrack(track: UiTrack?) =
    onShowDialog(track?.let { DialogState.TrackOption(it) })

fun MainActions.onSelectAlbum(album: Album?) =
    onShowDialog(album?.let { DialogState.AlbumOption(it) })

fun MainActions.onSelectArtist(artist: Artist?) =
    onShowDialog(artist?.let { DialogState.ArtistOption(it) })

fun MainActions.onSelectAllArtists(allArtists: AllArtists?) =
    onShowDialog(allArtists?.let { DialogState.AllArtistsOption })

fun MainActions.onSelectSpotifyContainer(container: SpotifyContainer) =
    onShowDialog(DialogState.SpotifyContainerOption(container))

fun MainActions.onSelectSpotifyRecommendedRoot(root: SpotifyRecommendedRoot) =
    onShowDialog(DialogState.SpotifyRecommendedOption(root.isFlattened))

fun MainActions.onSelectGenre(genre: Genre?) =
    onShowDialog(genre?.let { DialogState.GenreOption(it) })

fun MainActions.onSelectSavedQueueForOption(savedQueue: UiSavedQueue?) =
    onShowDialog(savedQueue?.let { DialogState.SavedQueueOption(it) })

fun MainActions.onSelectSavedQueueForModify(savedQueue: UiSavedQueue?) =
    onShowDialog(savedQueue?.let { DialogState.SavedQueueModify(it) })

fun MainActions.onShowDropboxDialog() = onShowDialog(DialogState.Dropbox())

fun MainActions.onConfirmSpotifySignOut() = onShowDialog(DialogState.ConfirmSpotifySignOut)

fun MainActions.onEnablePauseOnCurrentTrackEnd() =
    onShowDialog(DialogState.EnablePauseOnCurrentTrackEnd)

fun MainActions.onShowSaveQueueDialog() = onShowDialog(DialogState.SaveQueue())

fun MainActions.onDownload(targets: List<String>) {
    if (targets.isEmpty()) return

    onShowDialog(DialogState.ConfirmDownload(targets.toImmutableList()))
}

fun MainActions.onInvalidateDownloaded(targets: List<String>) {
    if (targets.isEmpty()) return

    onShowDialog(DialogState.ConfirmInvalidateDownloaded(targets.toImmutableList()))
}

fun MainActions.onSearchItemClicked(item: SearchItem, navController: NavHostController) {
    when (item.type) {
        SearchItem.SearchItemType.TRACK, SearchItem.SearchItemType.LYRIC -> {
            onSelectTrack(item.data as UiTrack)
        }

        SearchItem.SearchItemType.ALBUM -> {
            navController.navigate("tracks?albumId=${(item.data as Album).id}")
        }

        SearchItem.SearchItemType.ARTIST -> {
            navController.navigate("albums?artistId=${(item.data as Artist).id}")
        }

        SearchItem.SearchItemType.GENRE -> {
            navController.navigate("tracks?genreName=${(item.data as Genre).name.encodeUrlSafe()}")
        }

        SearchItem.SearchItemType.SPOTIFY_TRACK -> {
            onShowDialog(DialogState.SpotifyTrackOption(item.data as SpotifyTrack))
        }

        SearchItem.SearchItemType.SPOTIFY_ALBUM,
        SearchItem.SearchItemType.SPOTIFY_ARTIST,
        SearchItem.SearchItemType.SPOTIFY_PLAYLIST -> {
            onShowDialog(DialogState.SpotifyContainerOption(item.data as SpotifyContainer))
        }

        else -> Unit
    }
}

fun MainActions.onSearchItemLongClicked(item: SearchItem) {
    when (item.type) {
        SearchItem.SearchItemType.TRACK, SearchItem.SearchItemType.LYRIC -> {
            onSelectTrack(item.data as UiTrack)
        }

        SearchItem.SearchItemType.ALBUM -> {
            onSelectAlbum(item.data as Album)
        }

        SearchItem.SearchItemType.ARTIST -> {
            onSelectArtist(item.data as Artist)
        }

        SearchItem.SearchItemType.GENRE -> {
            onSelectGenre(item.data as Genre)
        }

        SearchItem.SearchItemType.SPOTIFY_TRACK -> {
            onShowDialog(DialogState.SpotifyTrackOption(item.data as SpotifyTrack))
        }

        SearchItem.SearchItemType.SPOTIFY_ALBUM,
        SearchItem.SearchItemType.SPOTIFY_ARTIST,
        SearchItem.SearchItemType.SPOTIFY_PLAYLIST -> {
            onShowDialog(DialogState.SpotifyContainerOption(item.data as SpotifyContainer))
        }

        else -> Unit
    }
}
