package com.geckour.q.ui.main

import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed interface DialogState {

    data class TrackOption(val track: UiTrack) : DialogState

    data class AlbumOption(val album: Album) : DialogState

    data class ArtistOption(val artist: Artist) : DialogState

    data object AllArtistsOption : DialogState

    data class GenreOption(val genre: Genre) : DialogState

    data class SavedQueueOption(val savedQueue: UiSavedQueue) : DialogState

    data class SavedQueueModify(val savedQueue: UiSavedQueue) : DialogState

    data class Dropbox(
        val hasAlreadyShownSyncAlert: Boolean = false,
        val itemList: Triple<String, ImmutableList<FolderMetadata>, ImmutableList<FileMetadata>> =
            Triple("", persistentListOf(), persistentListOf()),
    ) : DialogState

    data class ConfirmDownload(val targets: ImmutableList<String>) : DialogState

    data class ConfirmInvalidateDownloaded(val targets: ImmutableList<String>) : DialogState

    data object EnablePauseOnCurrentTrackEnd : DialogState

    data object SaveQueue : DialogState
}
