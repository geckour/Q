package com.geckour.q.ui.main

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.SyncSizeAlert
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.getReadableStringWithUnit
import kotlinx.collections.immutable.ImmutableList

@Composable
fun BoxScope.Dialogs(
    selectedTrack: UiTrack?,
    selectedAlbum: Album?,
    selectedArtist: Artist?,
    selectedGenre: Genre?,
    selectedAllArtists: AllArtists?,
    selectedSavedQueueForOption: UiSavedQueue?,
    selectedSavedQueueForModify: UiSavedQueue?,
    currentQueue: List<UiTrack>,
    navController: NavHostController,
    isSearchActive: MutableState<Boolean>,
    currentDropboxItemList: Triple<String, ImmutableList<FolderMetadata>, ImmutableList<FileMetadata>>,
    downloadTargets: ImmutableList<String>,
    invalidateDownloadedTargets: ImmutableList<String>,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    isFavoriteOnly: MutableState<Boolean>,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAllArtists: (allArtists: AllArtists?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
    onSelectSavedQueueForOption: (uiSavedQueue: UiSavedQueue?) -> Unit,
    onSelectSavedQueueForModify: (uiSavedQueue: UiSavedQueue?) -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit,
    onExportLyric: (uiTrack: UiTrack) -> Unit,
    onAttachLyric: (trackId: Long) -> Unit,
    onDetachLyric: (trackId: Long) -> Unit,
    onNewQueue: (
        queue: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean?,
    ) -> Unit,
    onGenerateQueue: (
        track: UiTrack,
        actionType: InsertActionType,
        classType: OrientedClassType,
    ) -> Unit,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (selectedFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (targetFolderPath: String?, needDownloaded: Boolean) -> Unit,
    hideResetShuffleDialog: () -> Unit,
    onShuffle: (actionType: ShuffleActionType) -> Unit,
    onResetShuffle: () -> Unit,
    onCancelDownload: () -> Unit,
    onStartDownloader: () -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
    onCancelEnablePauseOnCurrentTrackEnd: () -> Unit,
    onPositiveEnablePauseOnCurrentTrackEnd: () -> Unit,
    onDeclineSyncSize: () -> Unit,
    onApproveSyncSize: () -> Unit,
    onDismissSyncSizeExceeded: () -> Unit,
    onSaveQueue: (title: String) -> Unit,
    showEnablePauseOnCurrentTrackEndDialog: Boolean,
    syncSizeAlert: SyncSizeAlert?,
    showSaveQueueDialog: MutableState<Boolean>,
    onDeleteSavedQueue: (savedQueueId: Long) -> Unit,
    onModifySavedQueue: (savedQueueId: Long, newTitle: String, newTrackIds: List<Long>) -> Unit,
) {
    val innerIsFavoriteOnly = remember { mutableStateOf(isFavoriteOnly.value) }
    LaunchedEffect(isFavoriteOnly.value) {
        innerIsFavoriteOnly.value = isFavoriteOnly.value
    }

    selectedTrack?.let { domainTrack ->
        TrackOptionDialog(
            uiTrack = domainTrack,
            navController = navController,
            onSelectTrack = onSelectTrack,
            onNewQueue = { queue, actionType, classType ->
                isSearchActive.value = false
                onNewQueue(queue, actionType, classType, null)
            },
            onGenerateQueue = { track, actionType, classType ->
                isSearchActive.value = false
                onGenerateQueue(track, actionType, classType)
            },
            onExportLyric = onExportLyric,
            onAttachLyric = onAttachLyric,
            onDetachLyric = onDetachLyric,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedAlbum?.let { album ->
        AlbumOptionDialog(
            album = album,
            isFavoriteOnly = innerIsFavoriteOnly,
            onSelectAlbum = onSelectAlbum,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedArtist?.let { artist ->
        ArtistOptionDialog(
            artist = artist,
            isFavoriteOnly = innerIsFavoriteOnly,
            onSelectArtist = onSelectArtist,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedAllArtists?.let {
        AllArtistOptionDialog(
            isFavoriteOnly = innerIsFavoriteOnly,
            onSelected = onSelectAllArtists,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    selectedGenre?.let { genre ->
        GenreOptionDialog(
            genre = genre,
            onSelectGenre = onSelectGenre,
            onNewQueue = onNewQueue,
            onDeleteTrack = onDeleteTrack
        )
    }
    if (showDropboxDialog) {
        DropboxDialog(
            hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
            currentDropboxItemList = currentDropboxItemList,
            onStartAuthDropbox = onStartAuthDropbox,
            onShowDropboxFolderChooser = onShowDropboxFolderChooser,
            hideDropboxDialog = hideDropboxDialog,
            startDropboxSync = startDropboxSync
        )
    }
    if (showResetShuffleDialog) {
        ShuffleResetOptionDialog(
            hideResetShuffleDialog = hideResetShuffleDialog,
            onShuffle = onShuffle,
            onResetShuffle = onResetShuffle
        )
    }
    if (downloadTargets.isNotEmpty()) {
        QConfirmDialog(
            title = stringResource(id = R.string.dialog_title_dropbox_download),
            message = stringResource(id = R.string.dialog_desc_dropbox_download),
            onPositive = onStartDownloader,
            onDismissRequest = onCancelDownload,
        )
    }
    if (invalidateDownloadedTargets.isNotEmpty()) {
        QConfirmDialog(
            title = stringResource(id = R.string.dialog_title_dropbox_invalidate_downloaded),
            message = stringResource(id = R.string.dialog_desc_dropbox_invalidate_downloaded),
            onPositive = onStartInvalidateDownloaded,
            onDismissRequest = onCancelInvalidateDownloaded,
        )
    }
    if (showEnablePauseOnCurrentTrackEndDialog) {
        QConfirmDialog(
            message = stringResource(id = R.string.dialog_message_enable_pause_on_current_track_end),
            onPositive = onPositiveEnablePauseOnCurrentTrackEnd,
            onDismissRequest = onCancelEnablePauseOnCurrentTrackEnd,
        )
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
                onPositive = onApproveSyncSize,
                onDismissRequest = onDeclineSyncSize,
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
                onPositive = onDismissSyncSizeExceeded,
                onDismissRequest = onDismissSyncSizeExceeded,
                onNegative = null,
            )
        }

        null -> Unit
    }
    if (showSaveQueueDialog.value) {
        SaveQueueDialog(
            onCancel = { showSaveQueueDialog.value = false },
            onPositive = {
                showSaveQueueDialog.value = false
                onSaveQueue(it)
            }
        )
    }
    if (selectedSavedQueueForOption != null) {
        SavedQueueOptionDialog(
            uiSavedQueue = selectedSavedQueueForOption,
            onNewQueue = onNewQueue,
            onDelete = onDeleteSavedQueue,
            onDismiss = { onSelectSavedQueueForOption(null) },
        )
    }
    if (selectedSavedQueueForModify != null) {
        SavedQueueModifyDialog(
            uiSavedQueue = selectedSavedQueueForModify,
            currentQueue = currentQueue,
            onModify = onModifySavedQueue,
            onDismiss = { onSelectSavedQueueForModify(null) },
        )
    }
}
