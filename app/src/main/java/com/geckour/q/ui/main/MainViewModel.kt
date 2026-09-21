package com.geckour.q.ui.main

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.BillingApiClient
import com.geckour.q.data.SpotifyApiClient
import com.geckour.q.data.SpotifyContentClient
import com.geckour.q.data.SpotifyContentException
import com.geckour.q.data.SpotifyApiException
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.data.db.model.SpotifyContentEntry
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.domain.model.SpotifyContentItem
import com.geckour.q.domain.model.SyncProgress
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.service.DropboxMediaSyncJobService
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.main.dialog.DialogState
import com.geckour.q.ui.main.library.SpotifyBrowseItem
import com.geckour.q.ui.main.library.SpotifyBrowseSource
import com.geckour.q.ui.main.library.SpotifyBrowseState
import com.geckour.q.ui.main.library.SpotifyLevel
import com.geckour.q.ui.main.library.level
import com.geckour.q.ui.main.library.levelKey
import com.geckour.q.ui.main.library.key
import com.geckour.q.ui.main.dialog.TrackSource
import com.geckour.q.util.DownloadState
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.SpotifyAuthRequiredException
import com.geckour.q.util.SpotifyPlaybackErrorState
import com.geckour.q.util.SpotifyPlaybackStartTimeoutException
import com.geckour.q.util.SpotifyPremiumRequiredException
import com.geckour.q.util.SyncProgressState
import com.geckour.q.util.SyncSizeAlertState
import com.geckour.q.util.getActiveQAudioDeviceInfo
import com.geckour.q.util.getDropboxCredential
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.getIsInNightMode
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.getShowLyric
import com.geckour.q.util.escapeSql
import com.geckour.q.util.getIsSpotifyFlattened
import com.geckour.q.util.getIsSpotifyUnlocked
import com.geckour.q.util.getSpotifyCredential
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isFavoriteToggled
import com.geckour.q.util.isSpotifyConfigured
import com.geckour.q.util.isSpotifySourcePath
import com.geckour.q.util.isSpotifyTrackUri
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.setDropboxCredential
import com.geckour.q.util.setHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.setIsNightMode
import com.geckour.q.util.setShowLyric
import com.geckour.q.util.setIsSpotifyFlattened
import com.geckour.q.util.setIsSpotifyUnlocked
import com.geckour.q.util.toLrcString
import com.geckour.q.util.toUiTrack
import com.geckour.q.worker.KEY_PROGRESS_FINISHED
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_FRACTION
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_PATHS
import com.geckour.q.worker.KEY_PROGRESS_REMAINING_DURATION
import com.geckour.q.worker.KEY_PROGRESS_REMAINING_FILES
import com.geckour.q.worker.KEY_PROGRESS_SKIPPED_FILES
import com.geckour.q.worker.KEY_PROGRESS_TITLE
import com.geckour.q.worker.KEY_PROGRESS_TOTAL_FILES
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    private val app: App,
    private val spotifyApiClient: SpotifyApiClient,
    private val spotifyContentClient: SpotifyContentClient,
) : ViewModel() {

    companion object {

        const val DROPBOX_PATH_ROOT = "/"

        private const val MAX_RETRY_COUNT = 5

        private const val MAX_RATE_LIMIT_RETRY_COUNT = 2

        private const val HTTP_FORBIDDEN = 403

        private const val HTTP_TOO_MANY_REQUESTS = 429

        private const val MAX_SPOTIFY_CONTAINER_TRACKS = 300

        private const val SPOTIFY_TRACK_DETAIL_CONCURRENCY = 6

        private const val SPOTIFY_CONTENT_SYNC_INTERVAL_MILLIS = 10 * 60 * 1000L

        private const val SPOTIFY_CONTENT_SEARCH_LIMIT = 10

        private const val SPOTIFY_UNLOCK_TAP_COUNT = 7

        private const val SPOTIFY_UNLOCK_TAP_INTERVAL_MILLIS = 800L

        private const val ROOT_PARENT_URI = ""

        private const val SPOTIFY_PLAYLIST_URI_PREFIX = "spotify:playlist:"

        private const val SPOTIFY_ALBUM_URI_PREFIX = "spotify:album:"
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
    internal val currentQueueFlow = combine(
        DB.getInstance(app).trackDao().getAllAsFlow(),
        DB.getInstance(app).spotifyTrackDao().getAllAsFlow(),
        currentSourcePathsFlow,
        currentIndexFlow,
    ) { allTracks, spotifyTracks, currentSourcePaths, currentIndex ->
        val spotifyTrackMap = spotifyTracks.associateBy { it.uri }
        currentSourcePaths.mapIndexedNotNull { index, sourcePath ->
            val nowPlaying = currentIndex == index
            if (sourcePath.isSpotifySourcePath) {
                spotifyTrackMap[sourcePath]?.toUiTrack(nowPlaying = nowPlaying)
            } else {
                allTracks.firstOrNull { it.track.sourcePath == sourcePath }
                    ?.toUiTrack(nowPlaying = nowPlaying)
            }
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

    internal val spotifyBrowse: StateFlow<SpotifyBrowseState>
        get() = spotifyBrowseState
    internal val hasSpotifyCredential = app.getSpotifyCredential().map { it != null }

    private val spotifyBrowseState = MutableStateFlow(SpotifyBrowseState())
    private var spotifyContentSyncJob: Job? = null

    private val spotifyContainers = mutableMapOf<String, SpotifyContainer>()

    private val spotifyContentMutex = Mutex()

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

    internal val isInNightMode = app.getIsInNightMode()
    internal val showLyric = app.getShowLyric()
    internal val isSpotifyUnlocked = app.getIsSpotifyUnlocked()

    internal val isSpotifyFlattened = app.getIsSpotifyFlattened()
    internal val equalizerParams = app.getEqualizerParams()
    internal val activeQAudioDeviceInfo = app.getActiveQAudioDeviceInfo()

    internal val syncSizeAlert = SyncSizeAlertState.alert

    private val mutableProgressState = MutableStateFlow(ProgressUiState())
    internal val progressState: StateFlow<ProgressUiState> = mutableProgressState
    private var cancelProgressAction: (() -> Unit)? = null
    private var latestWorkInfoList: List<WorkInfo> = emptyList()
    private val finishedWorkIds = mutableSetOf<UUID>()

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

    init {
        viewModelScope.launch {
            SyncProgressState.progress.collect { onSyncProgressChanged(it) }
        }
        viewModelScope.launch {
            workInfoListFlow.collect { onWorkInfoListChanged(it) }
        }
        viewModelScope.launch {
            SpotifyPlaybackErrorState.errors.collect { showSpotifyError(it) }
        }
    }

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
        spotifyTracks: List<SpotifyTrack> = emptyList(),
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
                if (spotifyTracks.isNotEmpty()) {
                    putString(
                        PlayerService.ACTION_EXTRA_SUBMIT_QUEUE_SPOTIFY_TRACKS,
                        Json.encodeToString(spotifyTracks),
                    )
                }
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

    internal fun respondSyncSizeConfirmation(approved: Boolean) {
        DropboxMediaSyncJobService.respondSizeConfirmation(approved)
    }

    internal fun dismissSyncSizeExceeded() {
        SyncSizeAlertState.update(null)
    }

    internal fun cancelProgress() {
        cancelProgressAction?.invoke()
    }

    private fun onSyncProgressChanged(progress: SyncProgress?) {
        if (progress == null) {
            if (latestWorkInfoList.none { it.state == WorkInfo.State.RUNNING }) clearProgress()
            return
        }

        val remainingText = app.getString(
            R.string.remaining,
            progress.remainingFiles,
            "${progress.processedFilesSize.toFloat().getReadableStringWithUnit()}B",
            "${progress.totalFilesSize.toFloat().getReadableStringWithUnit()}B",
            progress.skippedFiles,
        )
        val remainingDurationText =
            if (progress.remainingDuration < 0) ""
            else app.getString(
                R.string.remaining_duration,
                progress.remainingDuration.getTimeString(),
            )

        setProgress(
            message = listOf(progress.title, remainingText, remainingDurationText)
                .filter { it.isNotEmpty() }
                .joinToString("\n"),
            paths = progress.paths.toImmutableList(),
            fraction = progress.progressFraction,
            cancelAction = { DropboxMediaSyncJobService.cancel(app) },
        )
    }

    private fun onWorkInfoListChanged(workInfoList: List<WorkInfo>) {
        latestWorkInfoList = workInfoList
        if (SyncProgressState.progress.value != null) return

        if (workInfoList.none { it.state == WorkInfo.State.RUNNING }) {
            val pendingWorkInfo = workInfoList.firstOrNull {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
            }
            if (pendingWorkInfo == null) {
                clearProgress()
            } else {
                cancelProgressAction = { pendingWorkInfo.cancel() }
                mutableProgressState.update {
                    it.copy(message = app.getString(R.string.starting), cancelable = true)
                }
            }
            return
        }

        workInfoList.forEach { workInfo ->
            val progress = workInfo.progress
            val fraction = progress.getFloat(KEY_PROGRESS_PROGRESS_FRACTION, -1f)
            if (fraction < 0) return@forEach

            val remainingFilesCount = progress.getInt(KEY_PROGRESS_REMAINING_FILES, -1)
            val totalFilesCount = progress.getInt(KEY_PROGRESS_TOTAL_FILES, -1)
            val skippedFilesCount = progress.getInt(KEY_PROGRESS_SKIPPED_FILES, 0)
            val remainingText =
                if (remainingFilesCount < 0 || totalFilesCount < 0) ""
                else app.getString(
                    R.string.remaining_files,
                    remainingFilesCount,
                    totalFilesCount - remainingFilesCount,
                    totalFilesCount,
                    skippedFilesCount,
                )
            val remainingDuration = progress.getLong(KEY_PROGRESS_REMAINING_DURATION, -1)
            val remainingDurationText =
                if (remainingDuration < 0) ""
                else app.getString(
                    R.string.remaining_duration,
                    remainingDuration.getTimeString(),
                )
            val message = listOf(
                progress.getString(KEY_PROGRESS_TITLE).orEmpty(),
                remainingText,
                remainingDurationText,
            )
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            if (message.isNotEmpty()) {
                setProgress(
                    message = message,
                    paths = progress.getStringArray(KEY_PROGRESS_PROGRESS_PATHS)
                        ?.toList()
                        .orEmpty()
                        .toImmutableList(),
                    fraction = fraction,
                    cancelAction = { workInfo.cancel() },
                )
            }

            if (finishedWorkIds.contains(workInfo.id).not() &&
                (workInfo.outputData.getBoolean(KEY_PROGRESS_FINISHED, false) ||
                        workInfo.state in listOf(
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.CANCELLED,
                    WorkInfo.State.FAILED
                ))
            ) {
                finishedWorkIds += workInfo.id
                if (workInfoList.all { it.state.isFinished }) {
                    mutableProgressState.update {
                        it.copy(message = null, paths = persistentListOf())
                    }
                }
            }
        }
    }

    private fun WorkInfo.cancel() {
        tags.forEach { workManager.cancelAllWorkByTag(it) }
    }

    private fun setProgress(
        message: String,
        paths: ImmutableList<String>,
        fraction: Float,
        cancelAction: () -> Unit,
    ) {
        cancelProgressAction = cancelAction
        mutableProgressState.value = ProgressUiState(
            message = message,
            paths = paths,
            fraction = fraction,
            cancelable = true,
        )
    }

    private fun clearProgress() {
        cancelProgressAction = null
        mutableProgressState.value = ProgressUiState()
    }

    internal fun showDialog(dialogState: DialogState?) {
        if (dialogState == null) {
            dismissDialog()
            return
        }

        mutableDialogState.value = dialogState
    }

    internal fun dismissDialog() {
        when (mutableDialogState.value) {
            is DialogState.Dropbox -> clearDropboxItemList()
            else -> Unit
        }
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

    private val mutableToastMessage = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val toastMessage: SharedFlow<String> = mutableToastMessage

    private var topBarTitleTapCount = 0
    private var lastTopBarTitleTapAt = 0L

    internal fun onTapTopBarTitle() {
        val now = SystemClock.elapsedRealtime()
        topBarTitleTapCount =
            if (now - lastTopBarTitleTapAt > SPOTIFY_UNLOCK_TAP_INTERVAL_MILLIS) 1
            else topBarTitleTapCount + 1
        lastTopBarTitleTapAt = now

        if (topBarTitleTapCount < SPOTIFY_UNLOCK_TAP_COUNT) return

        topBarTitleTapCount = 0
        viewModelScope.launch {
            if (app.getIsSpotifyUnlocked().first()) return@launch

            app.setIsSpotifyUnlocked(true)
            mutableToastMessage.tryEmit(app.getString(R.string.spotify_message_menu_enabled))
            if (app.getSpotifyCredential().first() == null) {
                showDialog(DialogState.ConfirmSpotifyAuth)
            }
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

    internal suspend fun storeDropboxApiToken(onFailure: (Throwable) -> Unit): Boolean {
        val credential = Auth.getDbxCredential() ?: return false
        app.setDropboxCredential(credential.toString())
        showDropboxFolderChooser(onFailure = onFailure)
        return true
    }

    internal fun showDropboxFolderChooser(
        dropboxMetadata: Metadata? = null,
        onFailure: (Throwable) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = obtainDbxClient(app).firstOrNull() ?: return@launch

            runCatching {
                var result = client.files().listFolder(dropboxMetadata?.pathLower.orEmpty())
                val entries = result.entries.toMutableList()
                while (result.hasMore) {
                    result = client.files().listFolderContinue(result.cursor)
                    entries += result.entries
                }
                val currentDirTitle = (dropboxMetadata?.name ?: "Root")
                dropboxItemList.emit(
                    Triple(
                        currentDirTitle,
                        entries
                            .filterIsInstance<FolderMetadata>()
                            .sortedBy { it.name.lowercase() }
                            .toImmutableList(),
                        entries
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

    internal suspend fun storeSpotifyCredential(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Int,
    ) {
        spotifyApiClient.storeCredential(accessToken, refreshToken, expiresInSeconds)
    }

    internal fun signOutSpotify() {
        spotifyContentClient.release()
        viewModelScope.launch {
            spotifyApiClient.clearCredential()
            spotifyBrowseState.value = SpotifyBrowseState()
        }
    }

    internal fun loadSpotifySource(source: SpotifyBrowseSource, reset: Boolean) {
        val level = spotifyBrowseState.value.level(source.levelKey)
        if (reset.not() && level.isLoading) return
        val offset = if (reset) 0 else level.nextOffset ?: return

        loadSpotifyLevel(source.levelKey, reset) {
            when (source) {
                SpotifyBrowseSource.SAVED -> {
                    val page = spotifyApiClient.getSavedTracks(offset)
                    page.items.map { SpotifyBrowseItem.Track(it) } to page.nextOffset
                }

                SpotifyBrowseSource.PLAYLISTS -> {
                    val page = spotifyApiClient.getMyPlaylists(offset)
                    page.items.map { SpotifyBrowseItem.Container(it) } to page.nextOffset
                }

                SpotifyBrowseSource.RECOMMENDED -> {
                    loadSpotifyRecommendedItems(app.getIsSpotifyFlattened().first()) to null
                }
            }
        }
        if (source == SpotifyBrowseSource.RECOMMENDED) syncSpotifyContentInBackground()
    }

    internal fun loadSpotifyContainer(container: SpotifyContainer, reset: Boolean) {
        val level = spotifyBrowseState.value.level(container.levelKey)
        if (reset.not() && level.isLoading) return
        val offset = if (reset) 0 else level.nextOffset ?: return

        loadSpotifyLevel(container.levelKey, reset) {
            if (container.kind == SpotifyContainer.Kind.CONTENT) {
                val page = spotifyContentClient.getChildren(container, offset)
                page.items.toBrowseItems() to page.nextOffset
            } else {
                val page = spotifyApiClient.getContainerTracks(container, offset)
                page.items.map { SpotifyBrowseItem.Track(it) } to page.nextOffset
            }
        }
    }

    internal suspend fun resolveSpotifyContainer(
        uri: String,
        kind: SpotifyContainer.Kind,
    ): SpotifyContainer? {
        spotifyContainers[uri]?.let { return it }

        if (kind == SpotifyContainer.Kind.CONTENT) {
            db.spotifyContentEntryDao().find(uri)?.let { entry ->
                return entry.toContentItem().toContainer().also { spotifyContainers[uri] = it }
            }
        }

        return try {
            spotifyApiClient.getContainer(uri, kind)?.also { spotifyContainers[uri] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Timber.e(t)
            null
        }
    }

    private fun loadSpotifyLevel(
        key: String,
        reset: Boolean,
        load: suspend () -> Pair<List<SpotifyBrowseItem>, Int?>,
    ) {
        updateSpotifyLevel(key) {
            it.copy(
                items = if (reset) persistentListOf() else it.items,
                nextOffset = if (reset) null else it.nextOffset,
                isLoading = true,
                hasLoaded = if (reset) false else it.hasLoaded,
                hasFailed = false,
            )
        }
        viewModelScope.launch {
            try {
                val (items, nextOffset) = load()
                items.filterIsInstance<SpotifyBrowseItem.Container>()
                    .forEach { spotifyContainers[it.container.uri] = it.container }
                updateSpotifyLevel(key) { current ->
                    current.copy(
                        items = (current.items + items).distinctBy { it.key }.toImmutableList(),
                        nextOffset = nextOffset,
                        isLoading = false,
                        hasLoaded = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Timber.e(t)
                updateSpotifyLevel(key) { it.copy(isLoading = false, hasFailed = true) }
                showSpotifyError(t)
            }
        }
    }

    private fun updateSpotifyLevel(key: String, update: (level: SpotifyLevel) -> SpotifyLevel) {
        spotifyBrowseState.update { state ->
            state.copy(levels = state.levels.toPersistentMap().put(key, update(state.level(key))))
        }
    }

    internal fun changeSpotifyFlatten(flatten: Boolean) {
        viewModelScope.launch {
            if (app.getIsSpotifyFlattened().first() == flatten) return@launch

            app.setIsSpotifyFlattened(flatten)
            loadSpotifySource(SpotifyBrowseSource.RECOMMENDED, reset = true)
        }
    }

    private suspend fun loadSpotifyRecommendedItems(
        flatten: Boolean,
    ): List<SpotifyBrowseItem> {
        val contents = spotifyContentMutex.withLock {
            val dao = db.spotifyContentEntryDao()
            if (dao.latestUpdatedAt() == null) syncSpotifyContent()

            val sections = dao.getChildren(ROOT_PARENT_URI).map { it.toContentItem() }
            if (flatten.not()) return@withLock sections

            sections.flatMap { dao.getChildren(it.uri) }
                .map { it.toContentItem() }
                .distinctBy { it.uri }
                .sortedBy { it.title.lowercase() }
        }

        return if (flatten) contents.toBrowseItems()
        else contents.map { SpotifyBrowseItem.Container(it.toContainer()) }
    }

    internal fun syncSpotifyContentInBackground() {
        if (isSpotifyConfigured.not()) return
        if (spotifyContentSyncJob?.isActive == true) return

        spotifyContentSyncJob = viewModelScope.launch {
            val synced = try {
                syncSpotifyContentIfStale()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Timber.e(t)
                return@launch
            }
            if (synced.not()) return@launch

            val key = SpotifyBrowseSource.RECOMMENDED.levelKey
            if (spotifyBrowseState.value.level(key).items.isEmpty()) return@launch

            val items = loadSpotifyRecommendedItems(app.getIsSpotifyFlattened().first())
            updateSpotifyLevel(key) { it.copy(items = items.toImmutableList()) }
        }
    }

    private suspend fun syncSpotifyContentIfStale(): Boolean = spotifyContentMutex.withLock {
        val latestUpdatedAt = db.spotifyContentEntryDao().latestUpdatedAt()
        if (latestUpdatedAt != null &&
            System.currentTimeMillis() - latestUpdatedAt < SPOTIFY_CONTENT_SYNC_INTERVAL_MILLIS
        ) {
            return@withLock false
        }

        syncSpotifyContent()
        true
    }

    private suspend fun syncSpotifyContent() {
        val dao = db.spotifyContentEntryDao()
        val syncedAt = System.currentTimeMillis()
        val sections = spotifyContentClient.getRootItems()
        dao.replaceChildren(ROOT_PARENT_URI, sections.toEntries(ROOT_PARENT_URI, syncedAt))
        sections.forEach { section ->
            val children = loadAllContentItems(section.toContainer())
            dao.replaceChildren(section.uri, children.toEntries(section.uri, syncedAt))
        }
        dao.deleteStalerThan(syncedAt)
    }

    private fun SpotifyContentItem.toContainer(): SpotifyContainer = SpotifyContainer(
        kind = SpotifyContainer.Kind.CONTENT,
        id = id,
        uri = uri,
        name = title,
        creatorName = subtitle,
        artworkUrl = artworkUrl,
        releaseDate = null,
        totalTracks = null,
    )

    private suspend fun List<SpotifyContentItem>.toBrowseItems(): List<SpotifyBrowseItem> {
        val tracks = toSpotifyTracks().associateBy { it.uri }

        return mapNotNull { item ->
            if (item.uri.isSpotifyTrackUri) {
                tracks[item.uri]?.let { SpotifyBrowseItem.Track(it) }
            } else {
                SpotifyBrowseItem.Container(item.toContainer())
            }
        }
    }

    private suspend fun List<SpotifyContentItem>.toSpotifyTracks(): List<SpotifyTrack> {
        val uris = filter { it.uri.isSpotifyTrackUri }.map { it.uri }
        if (uris.isEmpty()) return emptyList()

        val cached = db.spotifyTrackDao().getAllByUris(uris).associateBy { it.uri }
        val fetched = coroutineScope {
            uris.filterNot { cached.containsKey(it) }
                .chunked(SPOTIFY_TRACK_DETAIL_CONCURRENCY)
                .flatMap { chunk ->
                    chunk.map { uri -> async { spotifyApiClient.getTrack(uri) } }.awaitAll()
                }
                .filterNotNull()
        }
        fetched.forEach { db.spotifyTrackDao().upsert(it) }

        val details = cached + fetched.associateBy { it.uri }

        return uris.mapNotNull { details[it] }
    }

    private fun List<SpotifyContentItem>.toEntries(
        parentUri: String,
        syncedAt: Long,
    ): List<SpotifyContentEntry> = mapIndexed { index, item ->
        SpotifyContentEntry(
            parentUri = parentUri,
            uri = item.uri,
            contentId = item.id,
            title = item.title,
            subtitle = item.subtitle,
            artworkUrl = item.artworkUrl,
            position = index,
            updatedAt = syncedAt,
        )
    }

    private fun SpotifyContentEntry.toSearchItem(): SearchItem? {
        val type = when {
            uri.startsWith(SPOTIFY_PLAYLIST_URI_PREFIX) -> {
                SearchItem.SearchItemType.SPOTIFY_PLAYLIST
            }

            uri.startsWith(SPOTIFY_ALBUM_URI_PREFIX) -> SearchItem.SearchItemType.SPOTIFY_ALBUM
            else -> return null
        }

        return SearchItem(title, toContentItem().toContainer(), type)
    }

    private val SearchItem.spotifyUri: String?
        get() = when (val data = data) {
            is SpotifyTrack -> data.uri
            is SpotifyContainer -> data.uri
            else -> null
        }

    private fun SpotifyContentEntry.toContentItem(): SpotifyContentItem = SpotifyContentItem(
        id = contentId,
        uri = uri,
        title = title,
        subtitle = subtitle,
        artworkUrl = artworkUrl,
    )

    private suspend fun loadAllContentItems(
        container: SpotifyContainer,
    ): List<SpotifyContentItem> {
        val items = mutableListOf<SpotifyContentItem>()
        var offset = 0
        while (items.size < MAX_SPOTIFY_CONTAINER_TRACKS) {
            val page = spotifyContentClient.getChildren(container, offset)
            items += page.items
            offset = page.nextOffset ?: break
        }

        return items
    }

    internal fun addSpotifyTrack(track: SpotifyTrack, actionType: InsertActionType) {
        submitSpotifyTracks(listOf(track), actionType)
    }

    internal fun addSpotifyContainer(
        container: SpotifyContainer,
        actionType: InsertActionType,
        classType: OrientedClassType,
    ) {
        Timber.d("qgeck spotify add container: ${container.kind} ${container.name}")

        val job = viewModelScope.launch {
            try {
                val tracks = mutableListOf<SpotifyTrack>()
                var offset = 0
                while (tracks.size < MAX_SPOTIFY_CONTAINER_TRACKS) {
                    val nextOffset = if (container.kind == SpotifyContainer.Kind.CONTENT) {
                        val page = spotifyContentClient.getChildren(container, offset)
                        tracks += page.items.toSpotifyTracks()
                        page.nextOffset
                    } else {
                        val page = spotifyApiClient.getContainerTracks(container, offset)
                        tracks += page.items
                        page.nextOffset
                    }
                    offset = nextOffset ?: break
                }
                Timber.d("qgeck spotify container tracks: ${tracks.size}")

                if (tracks.isEmpty()) {
                    loading.value = false to null
                    showSnackbar(app.getString(R.string.spotify_message_empty))
                    return@launch
                }

                submitSpotifyTracks(
                    tracks = tracks.take(MAX_SPOTIFY_CONTAINER_TRACKS)
                        .ordered(actionType, classType),
                    actionType = actionType,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Timber.e(t)
                loading.value = false to null
                showSpotifyError(t)
            }
        }
        loading.value = true to {
            job.cancel()
            loading.value = false to null
        }
    }

    private fun List<SpotifyTrack>.ordered(
        actionType: InsertActionType,
        classType: OrientedClassType,
    ): List<SpotifyTrack> =
        when (actionType) {
            InsertActionType.SHUFFLE_SIMPLE_NEXT,
            InsertActionType.SHUFFLE_SIMPLE_LAST,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> shuffled()

            InsertActionType.SHUFFLE_NEXT,
            InsertActionType.SHUFFLE_LAST,
            InsertActionType.SHUFFLE_OVERRIDE -> {
                when (classType) {
                    OrientedClassType.ARTIST -> groupBy { it.artistName }
                    else -> groupBy { it.albumName }
                }
                    .values
                    .shuffled()
                    .flatten()
            }

            else -> this
        }

    internal suspend fun searchSpotifyItems(query: String): List<SearchItem> {
        if (isSpotifyConfigured.not()) return emptyList()
        if (app.getIsSpotifyUnlocked().first().not()) return emptyList()
        if (app.getSpotifyCredential().first() == null) return emptyList()

        val page = try {
            spotifyApiClient.search(query, offset = 0)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Timber.e(t)
            null
        }

        val fetched = page?.let {
            it.tracks.map { track ->
                SearchItem(track.title, track, SearchItem.SearchItemType.SPOTIFY_TRACK)
            } + it.albums.map { album ->
                SearchItem(album.name, album, SearchItem.SearchItemType.SPOTIFY_ALBUM)
            } + it.artists.map { artist ->
                SearchItem(artist.name, artist, SearchItem.SearchItemType.SPOTIFY_ARTIST)
            } + it.playlists.map { playlist ->
                SearchItem(playlist.name, playlist, SearchItem.SearchItemType.SPOTIFY_PLAYLIST)
            }
        }.orEmpty()
        val fetchedUris = fetched.mapNotNull { it.spotifyUri }.toSet()
        val cached = db.spotifyContentEntryDao()
            .searchByTitle("%${query.escapeSql}%", SPOTIFY_CONTENT_SEARCH_LIMIT)
            .mapNotNull { it.toSearchItem() }
            .filterNot { it.spotifyUri in fetchedUris }
        val items = fetched + cached
        if (items.isEmpty()) return emptyList()

        return listOf(
            SearchItem(
                app.getString(R.string.spotify_title),
                SearchCategory(),
                SearchItem.SearchItemType.CATEGORY,
            )
        ) + items
    }

    private fun submitSpotifyTracks(
        tracks: List<SpotifyTrack>,
        actionType: InsertActionType,
    ) {
        if (tracks.isEmpty()) return

        onNewQueue(
            sourcePaths = tracks.map { it.uri },
            actionType = actionType,
            classType = OrientedClassType.TRACK,
            needSorted = false,
            spotifyTracks = tracks,
        )
    }

    override fun onCleared() {
        spotifyContentClient.release()
        super.onCleared()
    }

    internal fun showSpotifyError(throwable: Throwable) {
        showSnackbar(throwable.toSpotifyErrorMessage())
    }

    internal fun showSnackbar(message: String) {
        viewModelScope.launch {
            snackbarMessageFlow.value = message
            delay(3000.milliseconds)
            snackbarMessageFlow.value = null
        }
    }

    private fun Throwable.toSpotifyErrorMessage(): String = when (this) {
        is CouldNotFindSpotifyApp -> app.getString(R.string.spotify_message_app_not_installed)
        is NotLoggedInException -> app.getString(R.string.spotify_message_not_logged_in)
        is UserNotAuthorizedException -> app.getString(R.string.spotify_message_not_authorized)
        is OfflineModeException -> app.getString(R.string.spotify_message_offline)
        is SpotifyPlaybackStartTimeoutException -> {
            app.getString(R.string.spotify_message_start_timeout)
        }

        is SpotifyPremiumRequiredException -> {
            app.getString(R.string.spotify_message_premium_required)
        }

        is SpotifyAuthRequiredException -> app.getString(R.string.spotify_message_auth_required)
        is SpotifyContentException -> {
            app.getString(R.string.spotify_message_content_failure, message.orEmpty())
        }

        is SpotifyApiException -> when (code) {
            HTTP_FORBIDDEN -> app.getString(R.string.spotify_message_forbidden)
            HTTP_TOO_MANY_REQUESTS -> app.getString(R.string.spotify_message_rate_limited)
            else -> app.getString(R.string.spotify_message_request_failure, code)
        }

        is IOException -> app.getString(R.string.spotify_message_network_failure)
        else -> app.getString(R.string.spotify_message_playback_failure, message.orEmpty())
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
