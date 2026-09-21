package com.geckour.q.ui.main.dialog

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.geckour.q.R
import com.geckour.q.domain.model.SyncSizeAlert
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.component.QConfirmDialog
import com.geckour.q.ui.component.QOption
import com.geckour.q.ui.component.QOptionDialog
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.isSpotify
import kotlinx.collections.immutable.ImmutableList

@Composable
fun BoxScope.Dialogs(
    dialogState: DialogState?,
    syncSizeAlert: SyncSizeAlert?,
    currentQueue: ImmutableList<UiTrack>,
    navController: NavHostController,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val innerIsFavoriteOnly = remember { mutableStateOf(isFavoriteOnly.value) }
    LaunchedEffect(isFavoriteOnly.value) {
        innerIsFavoriteOnly.value = isFavoriteOnly.value
    }

    when (dialogState) {
        is DialogState.TrackOption -> {
            TrackOptionDialog(
                uiTrack = dialogState.track,
                navController = navController,
                onDialogEvent = { event ->
                    if (event is DialogEvent.NewQueue || event is DialogEvent.GenerateQueue) {
                        isSearchActive.value = false
                    }
                    onDialogEvent(event)
                },
            )
        }

        is DialogState.AlbumOption -> {
            AlbumOptionDialog(
                album = dialogState.album,
                isFavoriteOnly = innerIsFavoriteOnly,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.ArtistOption -> {
            ArtistOptionDialog(
                artist = dialogState.artist,
                isFavoriteOnly = innerIsFavoriteOnly,
                onDialogEvent = onDialogEvent,
            )
        }

        DialogState.AllArtistsOption -> {
            AllArtistOptionDialog(
                isFavoriteOnly = innerIsFavoriteOnly,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.GenreOption -> {
            GenreOptionDialog(
                genre = dialogState.genre,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.SavedQueueOption -> {
            SavedQueueOptionDialog(
                uiSavedQueue = dialogState.savedQueue,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.SavedQueueModify -> {
            SavedQueueModifyDialog(
                uiSavedQueue = dialogState.savedQueue,
                currentQueue = currentQueue,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.Dropbox -> {
            DropboxDialog(
                state = dialogState,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.SpotifyTrackOption -> {
            SpotifyTrackOptionDialog(
                track = dialogState.track,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.SpotifyContainerOption -> {
            SpotifyContainerOptionDialog(
                container = dialogState.container,
                onDialogEvent = onDialogEvent,
            )
        }

        is DialogState.SpotifyRecommendedOption -> {
            QOptionDialog(
                options = listOf(
                    QOption(
                        if (dialogState.isFlattened) R.string.spotify_menu_sectioned
                        else R.string.spotify_menu_flatten
                    ) {
                        onDialogEvent(
                            DialogEvent.ChangeSpotifyFlatten(dialogState.isFlattened.not())
                        )
                        onDialogEvent(DialogEvent.Dismiss)
                    }
                ),
                onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
            )
        }

        DialogState.ConfirmSpotifyAuth -> {
            QConfirmDialog(
                title = stringResource(id = R.string.spotify_title),
                message = stringResource(id = R.string.spotify_message_auth),
                onPositive = {
                    onDialogEvent(DialogEvent.StartSpotifyAuth)
                    onDialogEvent(DialogEvent.Dismiss)
                },
                onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
            )
        }

        DialogState.ConfirmSpotifySignOut -> {
            QConfirmDialog(
                title = stringResource(id = R.string.spotify_title),
                message = stringResource(id = R.string.spotify_message_sign_out),
                onPositive = {
                    onDialogEvent(DialogEvent.SignOutSpotify)
                    onDialogEvent(DialogEvent.Dismiss)
                },
                onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
            )
        }

        is DialogState.ConfirmDownload -> {
            QConfirmDialog(
                title = stringResource(id = R.string.dialog_title_dropbox_download),
                message = stringResource(id = R.string.dialog_desc_dropbox_download),
                onPositive = {
                    onDialogEvent(DialogEvent.StartDownload(dialogState.targets))
                    onDialogEvent(DialogEvent.Dismiss)
                },
                onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
            )
        }

        is DialogState.ConfirmInvalidateDownloaded -> {
            QConfirmDialog(
                title = stringResource(id = R.string.dialog_title_dropbox_invalidate_downloaded),
                message = stringResource(id = R.string.dialog_desc_dropbox_invalidate_downloaded),
                onPositive = {
                    onDialogEvent(DialogEvent.StartInvalidateDownloaded(dialogState.targets))
                    onDialogEvent(DialogEvent.Dismiss)
                },
                onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
            )
        }

        DialogState.EnablePauseOnCurrentTrackEnd -> {
            QConfirmDialog(
                message = stringResource(id = R.string.dialog_message_enable_pause_on_current_track_end),
                onPositive = {
                    onDialogEvent(DialogEvent.EnablePauseOnCurrentTrackEnd)
                    onDialogEvent(DialogEvent.Dismiss)
                },
                onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
            )
        }

        is DialogState.SaveQueue -> {
            SaveQueueDialog(
                nextId = dialogState.nextId,
                onCancel = { onDialogEvent(DialogEvent.Dismiss) },
                onPositive = { title ->
                    onDialogEvent(
                        DialogEvent.SaveQueue(
                            title,
                            currentQueue.filterNot { it.isSpotify }.map { it.id },
                        )
                    )
                    onDialogEvent(DialogEvent.Dismiss)
                },
            )
        }

        null -> Unit
    }

    when (syncSizeAlert) {
        is SyncSizeAlert.Confirmation -> {
            QConfirmDialog(
                title = stringResource(id = R.string.dialog_title_dropbox_sync_size),
                message = stringResource(
                    id = R.string.dialog_desc_dropbox_sync_size,
                    "${syncSizeAlert.downloadSize.toFloat().getReadableStringWithUnit()}B",
                    "${syncSizeAlert.availableSize.toFloat().getReadableStringWithUnit()}B"
                ),
                onPositive = {
                    onDialogEvent(DialogEvent.RespondSyncSizeConfirmation(true))
                },
                onDismissRequest = {
                    onDialogEvent(DialogEvent.RespondSyncSizeConfirmation(false))
                },
            )
        }

        is SyncSizeAlert.Exceeded -> {
            QConfirmDialog(
                title = stringResource(id = R.string.dialog_title_dropbox_sync_size_exceeded),
                message = stringResource(
                    id = R.string.dialog_desc_dropbox_sync_size_exceeded,
                    "${syncSizeAlert.downloadSize.toFloat().getReadableStringWithUnit()}B",
                    "${syncSizeAlert.availableSize.toFloat().getReadableStringWithUnit()}B"
                ),
                onPositive = { onDialogEvent(DialogEvent.DismissSyncSizeExceeded) },
                onDismissRequest = { onDialogEvent(DialogEvent.DismissSyncSizeExceeded) },
                onNegative = null,
            )
        }

        null -> Unit
    }
}
