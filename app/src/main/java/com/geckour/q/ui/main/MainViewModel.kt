package com.geckour.q.ui.main

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.concurrent.futures.await
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.work.WorkManager
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.BillingApiClient
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.service.DropboxMediaSyncJobService
import com.geckour.q.service.PlayerService
import com.geckour.q.util.DownloadState
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.getDropboxCredential
import com.geckour.q.util.getHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.getIsInNightMode
import com.geckour.q.util.getShowLyric
import com.geckour.q.util.isFavoriteToggled
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.setDropboxCredential
import com.geckour.q.util.setHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.setIsNightMode
import com.geckour.q.util.setShowLyric
import com.geckour.q.util.toLrcString
import com.geckour.q.util.toUiTrack
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(private val app: App) : ViewModel() {

    companion object {

        const val DROPBOX_PATH_ROOT = "/"

        private const val MAX_RETRY_COUNT = 5

        private const val MAX_RATE_LIMIT_RETRY_COUNT = 2
    }

    private val db = DB.getInstance(app)
    internal val workManager = WorkManager.getInstance(app)
    internal val workInfoListFlow =
        workManager.getWorkInfosForUniqueWorkFlow(MEDIA_RETRIEVE_WORKER_NAME)

    private var mediaController: MediaController? = null

    internal var isDropboxAuthOngoing = false

    internal val currentSourcePathsFlow =
        MutableStateFlow<ImmutableList<String>>(persistentListOf())
    internal val currentIndexFlow = MutableStateFlow(0)
    internal val currentQueueFlow = DB.getInstance(app).trackDao().getAllAsFlow()
        .combine(currentSourcePathsFlow) { allTracks, currentSourcePaths ->
            allTracks to currentSourcePaths
        }.combine(currentIndexFlow) { (allTracks, currentSourcePaths), currentIndex ->
            currentSourcePaths.mapIndexedNotNull { index, sourcePath ->
                allTracks.firstOrNull { it.track.sourcePath == sourcePath }
                    ?.toUiTrack(nowPlaying = currentIndex == index)
            }
        }
    internal val currentPlaybackPositionFlow = MutableStateFlow(0L)
    internal val currentBufferedPositionFlow = MutableStateFlow(0L)
    internal val currentPlaybackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val currentRepeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val snackbarMessageFlow = MutableStateFlow<String?>(null)

    private var notifyPlaybackPositionJob: Job = Job()
    private var notifyBufferedPositionJob: Job = Job()

    private val dropboxItemList =
        MutableStateFlow<Triple<String, ImmutableList<FolderMetadata>, ImmutableList<FileMetadata>>>(
            Triple("", persistentListOf(), persistentListOf())
        )

    private val mutableDialogState = MutableStateFlow<DialogState?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val dialogState: StateFlow<DialogState?> = mutableDialogState.flatMapLatest { state ->
        when (state) {
            is DialogState.Dropbox -> combine(
                dropboxItemList,
                app.getHasAlreadyShownDropboxSyncAlert(),
                app.getDropboxCredential(),
            ) { itemList, hasAlreadyShownSyncAlert, credential ->
                state.copy(
                    hasAlreadyShownSyncAlert = hasAlreadyShownSyncAlert,
                    hasCredential = credential.isNullOrBlank().not(),
                    itemList = itemList,
                )
            }

            is DialogState.SaveQueue -> {
                db.savedQueueDao().getNextIdAsFlow().map { state.copy(nextId = it) }
            }

            else -> flowOf(state)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    internal val topBarTitle = MutableStateFlow("")
    internal val selectedNav = MutableStateFlow<Nav?>(null)
    internal val appBarOptionMediaItem = MutableStateFlow<MediaItem?>(null)
    internal val scrollToTop = MutableStateFlow(0L)
    internal val forceScrollToCurrent = MutableStateFlow(System.currentTimeMillis())

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
            playWhenReady: Boolean, reason: Int
        ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            onSourceChanged()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)

            currentRepeatModeFlow.value = repeatMode
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            onSourceChanged()
        }
    }

    private val billingApiClient = BillingApiClient(app, onError = {
        viewModelScope.launch {
            snackbarMessageFlow.value =
                app.getString(R.string.payment_message_error_failed_to_start)
            delay(2000.milliseconds)
            snackbarMessageFlow.value = null
        }
    }, onDonateCompleted = { result, client ->
        when (result) {
            BillingApiClient.BillingApiResult.SUCCESS -> {
                client.requestUpdate()
                viewModelScope.launch {
                    snackbarMessageFlow.value = app.getString(R.string.payment_message_success)
                    delay(2000.milliseconds)
                    snackbarMessageFlow.value = null
                }
            }

            BillingApiClient.BillingApiResult.DUPLICATED -> {
                client.requestUpdate()
                viewModelScope.launch {
                    snackbarMessageFlow.value =
                        app.getString(R.string.payment_message_error_duplicated)
                    delay(2000.milliseconds)
                    snackbarMessageFlow.value = null
                }
            }

            BillingApiClient.BillingApiResult.CANCELLED -> {
                val paymentMessageErrorCanceled =
                    app.getString(R.string.payment_message_error_canceled)
                viewModelScope.launch {
                    snackbarMessageFlow.value = paymentMessageErrorCanceled
                    delay(2000.milliseconds)
                    snackbarMessageFlow.value = null
                }
            }

            BillingApiClient.BillingApiResult.FAILURE -> {
                viewModelScope.launch {
                    snackbarMessageFlow.value = app.getString(R.string.payment_message_error_failed)
                    delay(2000.milliseconds)
                    snackbarMessageFlow.value = null
                }
            }
        }
    })

    internal fun initializeMediaController(context: Context) {
        viewModelScope.launch {
            mediaController = MediaController.Builder(
                context,
                SessionToken(
                    context,
                    ComponentName(context, PlayerService::class.java),
                ),
            ).buildAsync().await().apply {
                addListener(playerListener)
                onSourceChanged()
            }
        }
    }

    internal fun releaseMediaController() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
    }

    internal fun onNewQueue(
        sourcePaths: List<String>,
        actionType: InsertActionType,
        classType: OrientedClassType,
        needSorted: Boolean? = null,
    ) {
        val mediaController = this.mediaController ?: return

        loading.value = true to {
            mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_CANCEL_SUBMIT,
                    Bundle.EMPTY,
                ),
                Bundle.EMPTY,
            )
            loading.value = false to null
        }
        mediaController.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_SUBMIT_QUEUE, Bundle.EMPTY
            ),
            Bundle().apply {
                putSerializable(
                    PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_ACTION_TYPE,
                    actionType,
                )
                putSerializable(
                    PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_CLASS_TYPE,
                    classType,
                )
                putStringArrayList(
                    PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_QUEUE,
                    ArrayList(sourcePaths),
                )
                putBoolean(
                    PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_NEED_SORTED,
                    needSorted != false,
                )
            }
        )
    }

    internal fun onNewQueueFromSource(
        source: TrackSource,
        favoriteOnly: Boolean,
        actionType: InsertActionType,
        classType: OrientedClassType,
    ) {
        viewModelScope.launch {
            onNewQueue(
                sourcePaths = source.getTracks(favoriteOnly).map { it.track.sourcePath },
                actionType = actionType,
                classType = classType,
            )
        }
    }

    internal fun deleteTracksFromSource(source: TrackSource, favoriteOnly: Boolean) {
        viewModelScope.launch {
            source.getTracks(favoriteOnly).forEach { deleteTrack(it.toUiTrack()) }
        }
    }

    private suspend fun TrackSource.getTracks(favoriteOnly: Boolean): List<JoinedTrack> {
        val trackDao = db.trackDao()
        return when (this) {
            is TrackSource.OfAlbum -> {
                if (favoriteOnly) trackDao.getAllWithFavoriteByAlbum(albumId)
                else trackDao.getAllByAlbum(albumId)
            }

            is TrackSource.OfArtist -> {
                if (favoriteOnly) trackDao.getAllWithFavoriteByArtist(artistId)
                else trackDao.getAllByArtist(artistId)
            }

            TrackSource.All -> {
                if (favoriteOnly) trackDao.getAllWithFavorite()
                else trackDao.getAll()
            }

            is TrackSource.OfGenre -> trackDao.getAllByGenreName(genreName)
        }
    }

    internal fun onGenerateQueue(
        track: UiTrack,
        actionType: InsertActionType,
        classType: OrientedClassType,
    ) {
        val mediaController = this.mediaController ?: return

        loading.value = true to {
            mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_CANCEL_SUBMIT,
                    Bundle.EMPTY,
                ),
                Bundle.EMPTY,
            )
            loading.value = false to null
        }
        viewModelScope.launch {
            val queue = db.queueHistoryDao().generateQueue(track.id)

            withContext(Dispatchers.Main) {
                mediaController.sendCustomCommand(
                    SessionCommand(
                        PlayerService.ACTION_COMMAND_SUBMIT_QUEUE, Bundle.EMPTY,
                    ),
                    bundleOf(
                        PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_ACTION_TYPE to actionType,
                        PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_CLASS_TYPE to classType,
                        PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_QUEUE to queue,
                        PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_NEED_SORTED to false,
                    ),
                )
            }
        }
    }

    internal fun onQueueMove(from: Int, to: Int) {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_MOVE_QUEUE, Bundle.EMPTY
            ), bundleOf(
                PlayerService.ACTION_EXTRA_MOVE_QUEUE_FROM to from,
                PlayerService.ACTION_EXTRA_MOVE_QUEUE_TO to to
            )
        )
    }

    internal fun onRemoveTrackFromQueue(index: Int) {
        val mediaController = this.mediaController ?: return

        viewModelScope.launch {
            mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_REMOVE_QUEUE, Bundle.EMPTY
                ), bundleOf(
                    PlayerService.ACTION_EXTRA_REMOVE_QUEUE_TARGET_INDEX to index,
                )
            )
        }
    }

    private fun onRemoveTrackFromQueue(sourcePath: String) {
        val mediaController = this.mediaController ?: return

        viewModelScope.launch {
            mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_REMOVE_QUEUE, Bundle.EMPTY
                ), bundleOf(
                    PlayerService.ACTION_EXTRA_REMOVE_QUEUE_TARGET_SOURCE_PATH to sourcePath,
                )
            )
        }
    }

    internal fun deleteSavedQueue(savedQueueId: Long) {
        viewModelScope.launch {
            db.savedQueueDao().deleteSavedQueue(savedQueueId)
        }
    }

    internal fun onShuffle(actionType: ShuffleActionType? = null) {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_SHUFFLE_QUEUE, Bundle.EMPTY
            ), bundleOf(PlayerService.ACTION_EXTRA_SHUFFLE_ACTION_TYPE to actionType)
        )
    }

    internal fun onResetShuffle() {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_RESET_QUEUE_ORDER, Bundle.EMPTY
            ), Bundle.EMPTY
        )
    }

    internal fun showDialog(dialogState: DialogState?) {
        if (dialogState == null) {
            dismissDialog()
            return
        }

        mutableDialogState.value = dialogState
    }

    internal fun dismissDialog() {
        if (mutableDialogState.value is DialogState.Dropbox) clearDropboxItemList()
        mutableDialogState.value = null
    }

    internal fun showInvalidateDownloadedDialogForArtist(artistId: Long) {
        viewModelScope.launch {
            showInvalidateDownloadedDialog(db.artistDao().getContainTrackIds(artistId))
        }
    }

    internal fun showInvalidateDownloadedDialogForAlbum(albumId: Long) {
        viewModelScope.launch {
            showInvalidateDownloadedDialog(db.albumDao().getContainTrackIds(albumId))
        }
    }

    private fun showInvalidateDownloadedDialog(targets: List<String>) {
        if (targets.isEmpty()) return

        showDialog(DialogState.ConfirmInvalidateDownloaded(targets.toImmutableList()))
    }

    internal fun setOptionArtist(artistId: Long) {
        viewModelScope.launch {
            appBarOptionMediaItem.value = db.artistDao().get(artistId) ?: return@launch
        }
    }

    internal fun setOptionAlbum(albumId: Long) {
        viewModelScope.launch {
            appBarOptionMediaItem.value = db.albumDao().get(albumId)?.album ?: return@launch
        }
    }

    internal fun requestScrollToTop() {
        scrollToTop.value = System.currentTimeMillis()
    }

    internal fun requestScrollToCurrent() {
        forceScrollToCurrent.value = System.currentTimeMillis()
    }

    internal fun toggleNightMode() {
        viewModelScope.launch {
            app.setIsNightMode(app.getIsInNightMode().first().not())
        }
    }

    internal fun acknowledgeDropboxSyncAlert() {
        viewModelScope.launch {
            app.setHasAlreadyShownDropboxSyncAlert(true)
        }
    }

    internal suspend fun getLrcString(trackId: Long): String? =
        db.lyricDao().getLyricByTrackId(trackId)?.toLrcString()

    internal fun attachLyric(trackId: Long, lyricLines: List<LyricLine>) {
        viewModelScope.launch {
            val id = db.lyricDao().getLyricIdByTrackId(trackId) ?: 0
            db.lyricDao().upsertLyric(Lyric(id = id, trackId = trackId, lines = lyricLines))
            snackbarMessageFlow.value = app.getString(R.string.message_attach_lyric_success)
            delay(2000.milliseconds)
            snackbarMessageFlow.value = null
        }
    }

    internal fun detachLyric(trackId: Long) {
        viewModelScope.launch {
            db.lyricDao().deleteLyricByTrackId(trackId)
            snackbarMessageFlow.value = app.getString(R.string.message_delete_lyric_complete)
            delay(2000.milliseconds)
            snackbarMessageFlow.value = null
        }
    }

    internal fun toggleShowLyric() {
        viewModelScope.launch {
            app.setShowLyric(app.getShowLyric().first().not())
        }
    }

    internal fun toggleFavorite(mediaItem: MediaItem?): MediaItem? {
        val newMediaItem = mediaItem.isFavoriteToggled()
        viewModelScope.launch {
            when (newMediaItem) {
                is UiTrack -> {
                    val trackDao = db.trackDao()
                    val newTrack = trackDao.get(newMediaItem.id)
                        ?.track
                        ?.copy(isFavorite = newMediaItem.isFavorite)
                        ?: return@launch
                    trackDao.insert(newTrack)
                }

                is Album -> {
                    db.albumDao().insert(newMediaItem)
                }

                is Artist -> {
                    db.artistDao().insert(newMediaItem)
                }
            }
        }

        return newMediaItem
    }

    internal fun onTogglePlayPause() {
        val (playWhenReady, playbackState) = currentPlaybackInfoFlow.value
        onPlayOrPause(playWhenReady && playbackState == Player.STATE_READY)
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
                PlayerService.ACTION_COMMAND_CLEAR_QUEUE, Bundle.EMPTY
            ), bundleOf(PlayerService.ACTION_EXTRA_CLEAR_QUEUE_NEED_TO_KEEP_CURRENT to true)
        )
    }

    internal fun saveQueue(
        savedQueueId: Long? = null,
        title: String,
        trackIds: List<Long>,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            db.savedQueueDao().save(
                savedQueueId = savedQueueId,
                title = title,
                trackIds = trackIds,
            )
            onComplete()
        }
    }

    internal fun onClickRepeatButton() {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_ROTATE_REPEAT_MODE,
                Bundle.EMPTY,
            ),
            Bundle.EMPTY,
        )
    }

    private fun onSourceChanged() = viewModelScope.launch {
        val mediaController = this@MainViewModel.mediaController ?: return@launch

        loading.value = false to null
        currentSourcePathsFlow.value = List(mediaController.mediaItemCount) {
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
                currentPlaybackPositionFlow.value = mediaController.currentPosition
                delay(100.milliseconds)
            }
        }
        notifyBufferedPositionJob.cancel()
        notifyBufferedPositionJob = viewModelScope.launch {
            while (this.isActive) {
                currentBufferedPositionFlow.value = mediaController.bufferedPosition
                delay(100.milliseconds)
            }
        }
    }

    internal fun checkDBIsEmpty(onEmpty: () -> Unit) {
        viewModelScope.launch {
            val trackCount = db.trackDao().count()
            if (trackCount == 0) onEmpty()
        }
    }

    internal fun deleteTrack(uiTrack: UiTrack) {
        viewModelScope.launch {
            purgeDownloaded(listOf(uiTrack.sourcePath)).join()
            onRemoveTrackFromQueue(uiTrack.sourcePath)

            db.trackDao().deleteIncludingRootIfEmpty(db, uiTrack.id)
        }
    }

    internal fun downloadDropboxMedia(targetPaths: List<String>): Job =
        viewModelScope.launch {
            DropboxMediaSyncJobService.schedule(app, targetPaths)
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
                val file = it.toUri().toFile()
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        DownloadState.notifyChanged()
    }

    internal fun onChangeIndexRequested(index: Int) {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_RESET_QUEUE_INDEX, Bundle.EMPTY
            ), bundleOf(
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
                    Bundle.EMPTY,
                ),
                Bundle.EMPTY,
            )

            PlaybackButton.REWIND -> mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_REWIND,
                    Bundle.EMPTY,
                ),
                Bundle.EMPTY,
            )

            PlaybackButton.UNDEFINED -> mediaController.sendCustomCommand(
                SessionCommand(
                    PlayerService.ACTION_COMMAND_STOP_FAST_SEEK,
                    Bundle.EMPTY,
                ),
                Bundle.EMPTY,
            )
        }
    }

    internal fun enablePauseOnCurrentTrackEnd() {
        mediaController?.sendCustomCommand(
            SessionCommand(
                PlayerService.ACTION_COMMAND_SHOULD_PAUSE_ON_END_CURRENT,
                Bundle.EMPTY,
            ),
            Bundle.EMPTY,
        )
    }

    internal suspend fun storeDropboxApiToken(onFailure: (Throwable) -> Unit) {
        val credential = Auth.getDbxCredential() ?: return
        app.setDropboxCredential(credential.toString())
        showDropboxFolderChooser(onFailure = onFailure)
    }

    internal fun showDropboxFolderChooser(
        dropboxMetadata: Metadata? = null,
        onFailure: (Throwable) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = obtainDbxClient(app).firstOrNull() ?: return@launch

            runCatching {
                var result = client.files().listFolder(dropboxMetadata?.pathLower.orEmpty())
                while (true) {
                    if (result.hasMore.not()) break

                    result = client.files().listFolderContinue(result.cursor)
                }
                val currentDirTitle = (dropboxMetadata?.name ?: "Root")
                dropboxItemList.emit(
                    Triple(
                        currentDirTitle,
                        result.entries
                            .filterIsInstance<FolderMetadata>()
                            .sortedBy { it.name.lowercase() }
                            .toImmutableList(),
                        result.entries
                            .filterIsInstance<FileMetadata>()
                            .sortedBy { it.name.lowercase() }
                            .toImmutableList(),
                    )
                )
            }.onFailure {
                if (dropboxItemList.value.first.isEmpty()) dismissDialog()
                onFailure(it)
            }
        }
    }

    private fun clearDropboxItemList() {
        dropboxItemList.value = Triple("", persistentListOf(), persistentListOf())
    }

    internal fun startBilling(activity: Activity) {
        viewModelScope.launch {
            billingApiClient.startBilling(activity, listOf("donate"))
        }
    }

    internal fun requestBillingInfoUpdate() {
        billingApiClient.requestUpdate()
    }

    internal suspend fun emitSnackbarMessage(message: String?) {
        snackbarMessageFlow.emit(message)
    }
}
