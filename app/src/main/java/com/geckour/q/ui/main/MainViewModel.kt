package com.geckour.q.ui.main

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.core.net.toFile
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.work.WorkManager
import androidx.work.await
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.BillingApiClient
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.service.PlayerService
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.setDropboxCredential
import com.geckour.q.util.toDomainTrack
import com.geckour.q.worker.DROPBOX_DOWNLOAD_WORKER_NAME
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(private val app: App) : ViewModel() {

    companion object {

        const val DROPBOX_PATH_ROOT = "/"
    }

    private val db = DB.getInstance(app)
    internal val workManager = WorkManager.getInstance(app)
    internal val workInfoListFlow =
        workManager.getWorkInfosForUniqueWorkFlow(MEDIA_RETRIEVE_WORKER_NAME)
            .combine(workManager.getWorkInfosForUniqueWorkFlow(DROPBOX_DOWNLOAD_WORKER_NAME)) { mediaRetrieveWorkInfo, downloadWorkInfo ->
                mediaRetrieveWorkInfo + downloadWorkInfo
            }

    private var mediaController: MediaController? = null

    internal var isDropboxAuthOngoing = false

    internal val currentSourcePathsFlow =
        MutableStateFlow<ImmutableList<String>>(persistentListOf())
    internal val currentIndexFlow = MutableStateFlow(0)
    internal val currentQueueFlow = DB.getInstance(app).trackDao()
        .getAllAsFlow()
        .combine(currentSourcePathsFlow) { allTracks, currentSourcePaths ->
            allTracks to currentSourcePaths
        }
        .combine(currentIndexFlow) { (allTracks, currentSourcePaths), currentIndex ->
            currentSourcePaths.mapIndexedNotNull { index, sourcePath ->
                allTracks.firstOrNull { it.track.sourcePath == sourcePath }
                    ?.toDomainTrack(nowPlaying = currentIndex == index)
            }
        }
    internal val currentPlaybackPositionFlow = MutableStateFlow(0L)
    internal val currentBufferedPositionFlow = MutableStateFlow(0L)
    internal val currentPlaybackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val currentRepeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val snackBarMessageFlow = MutableStateFlow<String?>(null)

    private var notifyPlaybackPositionJob: Job = Job()
    private var notifyBufferedPositionJob: Job = Job()

    internal val forceLoad = MutableLiveData<Unit>()

    private val dropboxItemListChannel =
        Channel<Pair<String, ImmutableList<FolderMetadata>>>(capacity = Channel.CONFLATED)
    internal val dropboxItemList = dropboxItemListChannel.receiveAsFlow()

    internal val loading = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)

    private val playerListener = object : Player.Listener {

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)

            onSourceChanged()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)

            onSourceChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            onSourceChanged()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int
        ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            onSourceChanged()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)

            currentRepeatModeFlow.value = repeatMode
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            onSourceChanged()
        }
    }

    private val billingApiClient = BillingApiClient(
        app,
        onError = {
            viewModelScope.launch {
                snackBarMessageFlow.value =
                    app.getString(R.string.payment_message_error_failed_to_start)
                delay(2000)
                snackBarMessageFlow.value = null
            }
        },
        onDonateCompleted = { result, client ->
            when (result) {
                BillingApiClient.BillingApiResult.SUCCESS -> {
                    client.requestUpdate()
                    viewModelScope.launch {
                        snackBarMessageFlow.value = app.getString(R.string.payment_message_success)
                        delay(2000)
                        snackBarMessageFlow.value = null
                    }
                }

                BillingApiClient.BillingApiResult.DUPLICATED -> {
                    client.requestUpdate()
                    viewModelScope.launch {
                        snackBarMessageFlow.value =
                            app.getString(R.string.payment_message_error_duplicated)
                        delay(2000)
                        snackBarMessageFlow.value = null
                    }
                }

                BillingApiClient.BillingApiResult.CANCELLED -> {
                    val paymentMessageErrorCanceled =
                        app.getString(R.string.payment_message_error_canceled)
                    viewModelScope.launch {
                        snackBarMessageFlow.value = paymentMessageErrorCanceled
                        delay(2000)
                        snackBarMessageFlow.value = null
                    }
                }

                BillingApiClient.BillingApiResult.FAILURE -> {
                    viewModelScope.launch {
                        snackBarMessageFlow.value =
                            app.getString(R.string.payment_message_error_failed)
                        delay(2000)
                        snackBarMessageFlow.value = null
                    }
                }
            }
        }
    )

    internal fun initializeMediaController(context: Context) {
        viewModelScope.launch {
            mediaController = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlayerService::class.java))
            ).buildAsync()
                .await()
                .apply {
                    addListener(playerListener)

                    sendCustomCommand(
                        SessionCommand(
                            PlayerService.ACTION_COMMAND_RESTORE_STATE,
                            Bundle.EMPTY
                        ),
                        Bundle.EMPTY
                    )

                    onSourceChanged()
                }
        }
    }

    internal fun releaseMediaController() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
    }

    internal fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) {
        val mediaController = this.mediaController ?: return

        loading.value = true to {
            mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_CANCEL_SUBMIT,
                    Bundle.EMPTY
                ),
                Bundle.EMPTY
            )
            loading.value = false to null
        }
        mediaController.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_SUBMIT_QUEUE,
                Bundle.EMPTY
            ),
            bundleOf(
                PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_ACTION_TYPE to actionType,
                PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_CLASS_TYPE to classType,
                PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_QUEUE to domainTracks.map { it.sourcePath }
            )
        )
    }

    internal fun onQueueMove(from: Int, to: Int) {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_MOVE_QUEUE,
                Bundle.EMPTY
            ),
            bundleOf(
                PlayerService.ACTION_EXTRA_MOVE_QUEUE_FROM to from,
                PlayerService.ACTION_EXTRA_MOVE_QUEUE_TO to to
            )
        )
    }

    internal fun onRemoveTrackFromQueue(domainTrack: DomainTrack) {
        onRemoveTrackFromQueue(domainTrack.sourcePath)
    }

    private fun onRemoveTrackFromQueue(sourcePath: String) {
        val mediaController = this.mediaController ?: return

        viewModelScope.launch {
            mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_REMOVE_QUEUE,
                    Bundle.EMPTY
                ),
                bundleOf(
                    PlayerService.ACTION_EXTRA_REMOVE_QUEUE_TARGET_SOURCE_PATH to sourcePath,
                )
            )
        }
    }

    internal fun onShuffle(actionType: ShuffleActionType? = null) {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_SHUFFLE_QUEUE,
                Bundle.EMPTY
            ),
            bundleOf(PlayerService.ACTION_EXTRA_SHUFFLE_ACTION_TYPE to actionType)
        )
    }

    internal fun onResetShuffle() {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_RESET_QUEUE_ORDER,
                Bundle.EMPTY
            ),
            Bundle.EMPTY
        )
    }

    internal fun onPlayOrPause(playing: Boolean?) {
        onNewPlaybackButton(if (playing == true) PlaybackButton.PAUSE else PlaybackButton.PLAY)
    }

    internal fun onNext() {
        onNewPlaybackButton(PlaybackButton.NEXT)
    }

    internal fun onPrev() {
        onNewPlaybackButton(PlaybackButton.PREV)
    }

    internal fun onFF(): Boolean {
        onNewPlaybackButton(PlaybackButton.FF)
        return true
    }

    internal fun onRewind(): Boolean {
        onNewPlaybackButton(PlaybackButton.REWIND)
        return true
    }

    internal fun onClickClearQueueButton() {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_CLEAR_QUEUE,
                Bundle.EMPTY
            ),
            bundleOf(PlayerService.ACTION_EXTRA_CLEAR_QUEUE_NEED_TO_KEEP_CURRENT to true)
        )
    }

    internal fun onClickRepeatButton() {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_ROTATE_REPEAT_MODE,
                Bundle.EMPTY
            ),
            Bundle.EMPTY
        )
    }

    private fun onSourceChanged() = viewModelScope.launch {
        val mediaController = this@MainViewModel.mediaController ?: return@launch

        loading.value = false to null
        currentSourcePathsFlow.value =
            List(mediaController.mediaItemCount) {
                mediaController.getMediaItemAt(it).mediaId
            }.filter { it.isNotBlank() }.toImmutableList()
        currentIndexFlow.value = mediaController.currentMediaItemIndex.coerceAtLeast(0)
        currentPlaybackPositionFlow.value = mediaController.currentPosition
        currentBufferedPositionFlow.value = mediaController.bufferedPosition
        currentPlaybackInfoFlow.value =
            mediaController.playWhenReady to mediaController.playbackState
        currentRepeatModeFlow.value = mediaController.repeatMode
        notifyPlaybackPositionJob.cancel()
        notifyPlaybackPositionJob = viewModelScope.launch {
            while (this.isActive) {
                currentPlaybackPositionFlow.value =
                    mediaController.currentPosition
                delay(100)
            }
        }
        notifyBufferedPositionJob.cancel()
        notifyBufferedPositionJob = viewModelScope.launch {
            while (this.isActive) {
                currentBufferedPositionFlow.value = mediaController.bufferedPosition
                delay(100)
            }
        }
    }

    internal fun checkDBIsEmpty(onEmpty: () -> Unit) {
        viewModelScope.launch {
            val trackCount = db.trackDao().count()
            if (trackCount == 0) onEmpty()
        }
    }

    internal fun deleteTrack(domainTrack: DomainTrack) {
        viewModelScope.launch {
            purgeDownloaded(listOf(domainTrack.sourcePath)).join()
            onRemoveTrackFromQueue(domainTrack)

            db.trackDao().deleteIncludingRootIfEmpty(db, domainTrack.id)
        }
    }

    internal fun purgeDownloaded(targetSourcePaths: List<String>): Job = viewModelScope.launch {
        if (targetSourcePaths.contains(currentSourcePathsFlow.value.getOrNull(currentIndexFlow.value))) {
            onNewPlaybackButton(PlaybackButton.PAUSE)
        }
        currentSourcePathsFlow.value.forEach { sourcePath ->
            if (targetSourcePaths.any { it == sourcePath }) {
                onRemoveTrackFromQueue(sourcePath)
            }
        }
        runCatching {
            targetSourcePaths.forEach {
                val file = Uri.parse(it).toFile()
                if (file.exists()) {
                    file.delete()
                }
            }
            db.trackDao().clearAllSourcePaths(targetSourcePaths)
        }
    }

    internal fun onChangeRequestedTrackInQueue(domainTrack: DomainTrack) {
        val index = currentSourcePathsFlow.value.indexOf(domainTrack.sourcePath)
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_RESET_QUEUE_INDEX,
                Bundle.EMPTY
            ),
            bundleOf(
                PlayerService.ACTION_EXTRA_RESET_QUEUE_INDEX_FORCE to false,
                PlayerService.ACTION_EXTRA_RESET_QUEUE_INDEX_INDEX to index
            )
        )
    }

    internal fun onNewSeekBarProgress(progress: Long) {
        mediaController?.seekTo(progress)
    }

    internal fun onNewPlaybackButton(playbackButton: PlaybackButton) {
        val mediaController = this.mediaController ?: return

        when (playbackButton) {
            PlaybackButton.PLAY -> mediaController.play()
            PlaybackButton.PAUSE -> mediaController.pause()
            PlaybackButton.NEXT -> mediaController.seekToNext()
            PlaybackButton.PREV -> mediaController.seekToPrevious()
            PlaybackButton.FF -> mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_FAST_FORWARD,
                    Bundle.EMPTY
                ),
                Bundle.EMPTY
            )

            PlaybackButton.REWIND -> mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_REWIND,
                    Bundle.EMPTY
                ),
                Bundle.EMPTY
            )

            PlaybackButton.UNDEFINED -> mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_STOP_FAST_SEEK,
                    Bundle.EMPTY
                ),
                Bundle.EMPTY
            )
        }
    }

    internal suspend fun storeDropboxApiToken() {
        val credential = Auth.getDbxCredential() ?: return
        app.setDropboxCredential(credential.toString())
        showDropboxFolderChooser()
    }

    internal fun showDropboxFolderChooser(dropboxMetadata: Metadata? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = obtainDbxClient(app).firstOrNull() ?: return@launch
            var result = client.files().listFolder(dropboxMetadata?.pathLower.orEmpty())
            while (true) {
                if (result.hasMore.not()) break

                result = client.files().listFolderContinue(result.cursor)
            }
            val currentDirTitle = (dropboxMetadata?.name ?: "Root")
            dropboxItemListChannel.send(
                currentDirTitle to result.entries.filterIsInstance<FolderMetadata>()
                    .sortedBy { it.name.lowercase() }
                    .toImmutableList()
            )
        }
    }

    internal fun clearDropboxItemList() {
        viewModelScope.launch {
            dropboxItemListChannel.send("" to persistentListOf())
        }
    }

    internal fun startBilling(activity: Activity) {
        viewModelScope.launch {
            billingApiClient.startBilling(activity, listOf("donate"))
        }
    }

    internal fun requestBillingInfoUpdate() {
        billingApiClient.requestUpdate()
    }

    internal suspend fun emitSnackBarMessage(message: String?) {
        snackBarMessageFlow.emit(message)
    }
}