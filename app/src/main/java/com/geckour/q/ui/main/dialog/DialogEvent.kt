package com.geckour.q.ui.main.dialog

import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType

sealed interface DialogEvent {

    data object Dismiss : DialogEvent

    data class NewQueue(
        val sourcePaths: List<String>,
        val actionType: InsertActionType,
        val classType: OrientedClassType,
        val needSorted: Boolean? = null,
    ) : DialogEvent

    data class NewQueueFromSource(
        val source: TrackSource,
        val favoriteOnly: Boolean,
        val actionType: InsertActionType,
        val classType: OrientedClassType,
    ) : DialogEvent

    data class GenerateQueue(
        val track: UiTrack,
        val actionType: InsertActionType,
        val classType: OrientedClassType,
    ) : DialogEvent

    data class DeleteTrack(val track: UiTrack) : DialogEvent

    data class DeleteTracksFromSource(
        val source: TrackSource,
        val favoriteOnly: Boolean,
    ) : DialogEvent

    data class ExportLyric(val track: UiTrack) : DialogEvent

    data class AttachLyric(val trackId: Long) : DialogEvent

    data class DetachLyric(val trackId: Long) : DialogEvent

    data object AcknowledgeDropboxSyncAlert : DialogEvent

    data object StartDropboxAuth : DialogEvent

    data class ShowDropboxFolder(val folder: FolderMetadata?) : DialogEvent

    data class StartDropboxSync(
        val rootFolderPath: String?,
        val needDownloaded: Boolean,
    ) : DialogEvent

    data class StartDownload(val targets: List<String>) : DialogEvent

    data class StartInvalidateDownloaded(val targets: List<String>) : DialogEvent

    data object EnablePauseOnCurrentTrackEnd : DialogEvent

    data class SaveQueue(val title: String, val trackIds: List<Long>) : DialogEvent

    data class ModifySavedQueue(
        val savedQueueId: Long,
        val title: String,
        val trackIds: List<Long>,
    ) : DialogEvent

    data class DeleteSavedQueue(val savedQueueId: Long) : DialogEvent

    data class RespondSyncSizeConfirmation(val approved: Boolean) : DialogEvent

    data object DismissSyncSizeExceeded : DialogEvent
}
