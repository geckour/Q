package com.geckour.q.ui.main

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toFile
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.work.WorkManager
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.BillingApiClient
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.service.PlayerService
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.dropboxCachePathPattern
import com.geckour.q.util.getEqualizerEnabled
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getMediaItem
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.orderModified
import com.geckour.q.util.removedAt
import com.geckour.q.util.setDropboxCredential
import com.geckour.q.util.setEqualizerParams
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toDomainTracks
import com.geckour.q.util.verifiedWithDropbox
import com.geckour.q.worker.DROPBOX_DOWNLOAD_WORKER_NAME
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.FileNotFoundException

@UnstableApi
class MainViewModel(
    private val app: App,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

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

    private lateinit var mediaControllerFuture: ListenableFuture<MediaController>
    private var equalizer: Equalizer? = null

    internal var isDropboxAuthOngoing = false

    internal val currentSourcePathsFlow =
        MutableStateFlow<ImmutableList<String>>(persistentListOf())
    internal val currentIndexFlow = MutableStateFlow(0)
    internal val currentQueueFlow = DB.getInstance(app).trackDao()
        .getAllAsync()
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
    internal var snackBarMessageFlow = MutableStateFlow<String?>(null)

    private var notifyPlaybackPositionJob: Job = Job()
    private var notifyBufferedPositionJob: Job = Job()

    internal val forceLoad = MutableLiveData<Unit>()

    private val dropboxItemListChannel =
        Channel<Pair<String, ImmutableList<FolderMetadata>>>(capacity = Channel.CONFLATED)
    internal val dropboxItemList = dropboxItemListChannel.receiveAsFlow()

    internal val loading = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)

    private var onSubmitQueue: ((insertTo: Int, newQueue: List<MediaItem>) -> Unit)? = null
    private var onPrepare: (() -> Unit)? = null
    private var onMoveQueuePosition: ((from: Int, to: Int) -> Unit)? = null
    private var onRemoveQueue: ((index: Int) -> Unit)? = null
    private var onShuffleQueue: ((type: ShuffleActionType?) -> Unit)? = null
    private var onResetQueueOrder: (() -> Unit)? = null
    private var onClearQueue: ((positionToKeep: Int) -> Unit)? = null
    private var onSetRepeatMode: ((repeatMode: Int) -> Unit)? = null
    private var onRotateRepeatMode: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onResetQueueIndex: ((force: Boolean, position: Int) -> Unit)? = null
    private var onSeek: ((progress: Long) -> Unit)? = null
    private var onNewMediaButton: ((playbackButton: PlaybackButton) -> Unit)? = null
    private var onSetEqualizer: ((enabled: Boolean) -> Unit)? = null

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

    init {
        viewModelScope.launch {
            app.getEqualizerParams().collectLatest { params ->
                params?.let { reflectEqualizerSettings(it) }
            }
        }
        viewModelScope.launch {
            app.getEqualizerEnabled().collectLatest { enabled ->
                onSetEqualizer?.invoke(enabled)
            }
        }
    }

    internal fun initializeMediaController(context: Context) {
        mediaControllerFuture = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlayerService::class.java))
        )
            .buildAsync()

        mediaControllerFuture.addListener(
            {
                val mediaController =
                    if (mediaControllerFuture.isDone && mediaControllerFuture.isCancelled.not()) {
                        mediaControllerFuture.get()
                    } else return@addListener

                mediaController.addListener(object : Player.Listener {

                    var lastMediaItem: MediaItem? = null

                    override fun onTracksChanged(tracks: Tracks) {
                        super.onTracksChanged(tracks)

                        Timber.d(
                            "qgeck player tracks changed: ${
                                tracks.groups.map { group ->
                                    List(
                                        group.length
                                    ) { group.getTrackFormat(it) }
                                }
                            }"
                        )

                        onSourceChanged(mediaController)
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        super.onTimelineChanged(timeline, reason)

                        Timber.d("qgeck player on timeline changed: $timeline, $reason")

                        onSourceChanged(mediaController)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)

                        Timber.d(
                            "qgeck player playback state: ${
                                when (playbackState) {
                                    Player.STATE_IDLE -> "STATE_IDLE"
                                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                                    Player.STATE_READY -> "STATE_READY"
                                    Player.STATE_ENDED -> "STATE_ENDED"
                                    else -> "UNKNOWN"
                                }
                            }"
                        )

                        onSourceChanged(mediaController)

                        if (mediaController.currentIndex == mediaController.mediaItemCount - 1
                            && playbackState == Player.STATE_ENDED
                            && mediaController.repeatMode == Player.REPEAT_MODE_OFF
                        ) {
                            mediaController.stop()
                        }

                        if (playbackState == Player.STATE_READY && mediaController.playWhenReady) {
                            notifyPlaybackPositionJob.cancel()
                            notifyPlaybackPositionJob = viewModelScope.launch {
                                while (this.isActive) {
                                    currentPlaybackPositionFlow.value =
                                        mediaController.currentPosition
                                    delay(100)
                                }
                            }
                            if (mediaController.currentMediaItem != lastMediaItem) {
                                lastMediaItem = mediaController.currentMediaItem
                                increasePlaybackCount(mediaController)
                            }
                        }
                    }

                    override fun onPlayWhenReadyChanged(
                        playWhenReady: Boolean,
                        reason: Int
                    ) {
                        super.onPlayWhenReadyChanged(playWhenReady, reason)

                        Timber.d("qgeck player play when ready: $playWhenReady")

                        onSourceChanged(mediaController)

                        if (mediaController.playbackState == Player.STATE_READY && playWhenReady) {
                            if (mediaController.currentMediaItem != lastMediaItem) {
                                lastMediaItem = mediaController.currentMediaItem
                                increasePlaybackCount(mediaController)
                            }
                        }
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

                        currentPlaybackPositionFlow.value = newPosition.positionMs
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)

                        Timber.e(error)
                        FirebaseCrashlytics.getInstance().recordException(error)

                        val currentPlayWhenReady = mediaController.playWhenReady
                        mediaController.pause()
                        if (verifyByCauseIfNeeded(mediaController, error).not()) {
                            removeQueue(mediaController.currentIndex)
                        }
                        if (currentPlayWhenReady) mediaController.play()
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        super.onAudioSessionIdChanged(audioSessionId)

                        onSetEqualizer = { enabled ->
                            setEqualizer(if (enabled) audioSessionId else null)
                        }
                    }
                })
                onSubmitQueue = { insertTo, newQueue ->
                    mediaController.addMediaItems(insertTo, newQueue)
                }
                onPrepare = {
                    mediaController.prepare()
                }
                onClearQueue = { positionToKeep ->
                    if (positionToKeep !in 0 until mediaController.mediaItemCount) {
                        mediaController.clearMediaItems()
                    } else {
                        mediaController.removeMediaItems(0, positionToKeep)
                        mediaController.removeMediaItems(1, mediaController.mediaItemCount)
                    }
                }
                onSetRepeatMode = {
                    mediaController.repeatMode = it
                }
                onRotateRepeatMode = {
                    mediaController.repeatMode = when (mediaController.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                        else -> throw IllegalStateException()
                    }
                }
                onRemoveQueue = { index ->
                    if (index in 0 until mediaController.mediaItemCount &&
                        (index != mediaController.currentIndex ||
                                mediaController.playWhenReady.not() ||
                                (mediaController.playbackState != Player.STATE_READY &&
                                        mediaController.playbackState != Player.STATE_BUFFERING))
                    ) {
                        mediaController.removeMediaItem(index)
                    }
                }
                onMoveQueuePosition = { from, to ->
                    val sourceRange = 0 until mediaController.mediaItemCount
                    if (from != to && from in sourceRange && to in sourceRange) {
                        mediaController.moveMediaItem(from, to)
                    }
                }
                onShuffleQueue = {
                    shuffle(mediaController, it)
                }
                onResetQueueOrder = {
                    resetQueueOrder(mediaController)
                }
                onPause = {
                    mediaController.pause()
                }
                onResetQueueIndex = { force, index ->
                    if (force || mediaController.currentIndex != index) {
                        forceIndex(mediaController, index)
                    }
                }
                onSeek = {
                    mediaController.seekTo(it)
                }
                onNewMediaButton = {
                    when (it) {
                        PlaybackButton.PLAY -> mediaController.play()
                        PlaybackButton.PAUSE -> mediaController.pause()
                        PlaybackButton.NEXT -> mediaController.seekToNext()
                        PlaybackButton.PREV -> mediaController.seekToPrevious()
                        PlaybackButton.FF -> mediaController.seekForward()
                        PlaybackButton.REWIND -> mediaController.seekBack()
                        PlaybackButton.UNDEFINED -> mediaController.sendCustomCommand(
                            SessionCommand(
                                PlayerService.ACTION_COMMAND_STOP_FAST_SEEK,
                                Bundle.EMPTY
                            ),
                            Bundle.EMPTY
                        )
                    }
                }

                mediaController.sendCustomCommand(
                    SessionCommand(
                        PlayerService.ACTION_COMMAND_RESTORE_STATE,
                        Bundle.EMPTY
                    ),
                    Bundle.EMPTY
                )

                onSourceChanged(mediaController)
            },
            MoreExecutors.directExecutor()
        )
    }

    internal fun releaseMediaController() {
        MediaController.releaseFuture(mediaControllerFuture)
    }

    private suspend fun submitQueue(
        queueInfo: QueueInfo,
        positionToKeep: Int? = null,
        needSorted: Boolean = true,
    ) {
        var alive = true
        onLoadStateChanged(state = true, onAbort = { alive = false })

        val newQueue = queueInfo.queue
            .let {
                when {
                    needSorted -> it.orderModified(
                        queueInfo.metadata.classType,
                        queueInfo.metadata.actionType
                    )

                    else -> it
                }
            }
            .map { track ->
                if (alive.not()) {
                    onLoadStateChanged(state = false, onAbort = null)
                    return
                }
                (obtainDbxClient(app).take(1).lastOrNull()?.let {
                    track.verifiedWithDropbox(app, it)
                } ?: track)
                    .sourcePath
                    .getMediaItem()
            }
        when (queueInfo.metadata.actionType) {
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                if (positionToKeep == null) clear()
                else onClearQueue?.invoke(positionToKeep)
            }

            else -> Unit
        }
        when (queueInfo.metadata.actionType) {
            InsertActionType.NEXT,
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_NEXT,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_NEXT,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                onSubmitQueue?.invoke(currentIndexFlow.value + 1, newQueue)
            }

            InsertActionType.LAST,
            InsertActionType.SHUFFLE_LAST,
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                onSubmitQueue?.invoke(currentSourcePathsFlow.value.size, newQueue)
            }
        }

        onLoadStateChanged(state = false, onAbort = null)

        onPrepare?.invoke()
    }

    private fun clear(keepCurrentIfPlaying: Boolean = true) {
        val needToKeepCurrent = keepCurrentIfPlaying
                && currentPlaybackInfoFlow.value.second == Player.STATE_READY
                && currentPlaybackInfoFlow.value.first
        onClearQueue?.invoke(if (needToKeepCurrent) currentIndexFlow.value else -1)
    }

    /**
     * @param with: first: old, second: new
     */
    private fun replace(
        mediaController: MediaController,
        with: List<Pair<DomainTrack, DomainTrack>>
    ) {
        if (with.isEmpty()) return

        viewModelScope.launch {
            with.forEach { withTrack ->
                val index = currentSourcePathsFlow.value
                    .indexOfFirst { it == withTrack.first.sourcePath }
                FirebaseCrashlytics.getInstance()
                    .log("replace source path: ${withTrack.second.sourcePath}")
                removeQueue(index)
                mediaController.addMediaItem(
                    index,
                    withTrack.second.sourcePath.getMediaItem()
                )
            }
        }
    }

    private fun removeQueue(position: Int) {
        onRemoveQueue?.invoke(position)
    }

    private fun removeQueue(sourcePath: String) {
        val position = currentSourcePathsFlow.value.indexOfFirst { it == sourcePath }
        removeQueue(position)
    }

    private suspend fun removeQueue(track: DomainTrack) {
        val position = currentSourcePathsFlow.value
            .indexOfFirst { it.toDomainTrack(db)?.id == track.id }
        removeQueue(position)
    }

    private fun shuffle(mediaController: MediaController, actionType: ShuffleActionType? = null) {
        viewModelScope.launch {
            val currentQueue = currentSourcePathsFlow.value
            if (mediaController.mediaItemCount > 0 && mediaController.mediaItemCount == currentQueue.size) {
                val shuffled = when (actionType) {
                    null,
                    ShuffleActionType.SHUFFLE_SIMPLE -> {
                        currentQueue.shuffled()
                    }

                    ShuffleActionType.SHUFFLE_ALBUM_ORIENTED -> {
                        currentQueue.toDomainTracks(db)
                            .groupBy { it.album.id }
                            .map { it.value }
                            .shuffled()
                            .flatten()
                            .map { it.sourcePath }
                    }

                    ShuffleActionType.SHUFFLE_ARTIST_ORIENTED -> {
                        currentQueue.toDomainTracks(db)
                            .groupBy { it.artist.id }
                            .map { it.value }
                            .shuffled()
                            .flatten()
                            .map { it.sourcePath }
                    }
                }

                reorderQueue(mediaController, shuffled)
            }
        }
    }

    private fun resetQueueOrder(mediaController: MediaController) {
        if (mediaController.mediaItemCount < 1) return
        viewModelScope.launch {
            val sourcePaths = currentSourcePathsFlow.value
            val cachedSourcePaths =
                sharedPreferences.getString(PlayerService.PREF_KEY_PLAYER_STATE, null)
                    ?.let { catchAsNull { Json.decodeFromString<PlayerState>(it) } }
                    ?.sourcePaths
                    .orEmpty()
            val isCacheValid =
                sourcePaths.size == cachedSourcePaths.size &&
                        sourcePaths.containsAll(cachedSourcePaths)
            if (isCacheValid.not()) return@launch

            reorderQueue(mediaController, cachedSourcePaths)
        }
    }

    private fun reorderQueue(mediaController: MediaController, newSourcePaths: List<String>) {
        viewModelScope.launch {
            val currentIndex = mediaController.currentIndex.coerceAtLeast(0)
            val targetIndex = newSourcePaths.indexOfFirst {
                it == currentSourcePathsFlow.value.getOrNull(currentIndex)
            }.coerceAtLeast(0)
            submitQueue(
                QueueInfo(
                    QueueMetadata(
                        InsertActionType.OVERRIDE,
                        OrientedClassType.TRACK
                    ),
                    newSourcePaths.removedAt(targetIndex).toDomainTracks(db)
                ),
                currentIndex
            )
            onMoveQueuePosition?.invoke(currentIndex, targetIndex)
        }
    }

    private fun forceIndex(mediaController: MediaController, index: Int) {
        val windowIndex =
            mediaController.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        mediaController.seekToDefaultPosition(windowIndex + index)
    }

    private fun increasePlaybackCount(mediaController: MediaController) = viewModelScope.launch {
        mediaController.currentMediaItem?.toDomainTrack(db)
            ?.let { track ->
                db.trackDao().increasePlaybackCount(track.id)
                db.albumDao().increasePlaybackCount(track.album.id)
                db.artistDao().increasePlaybackCount(track.artist.id)
            }
    }

    private fun verifyByCauseIfNeeded(
        mediaController: MediaController,
        throwable: Throwable
    ): Boolean {
        var aborted = false
        val isTarget =
            throwable.getCausesRecursively().any {
                (it as? HttpDataSource.InvalidResponseCodeException?)?.responseCode == 410 ||
                        (it is FileNotFoundException &&
                                currentSourcePathsFlow.value
                                    .getOrNull(mediaController.currentIndex)
                                    ?.matches(dropboxCachePathPattern) == true)
            }
        if (isTarget) {
            viewModelScope.launch {
                onLoadStateChanged(state = true, onAbort = { aborted = true })
                val index = mediaController.currentIndex
                val position = mediaController.currentPosition
                val replacingPairs = currentSourcePathsFlow.value
                    .mapNotNull { path ->
                        if (aborted) return@launch
                        val track = path.toDomainTrack(db) ?: return@mapNotNull null
                        val new = obtainDbxClient(app).firstOrNull()?.let {
                            track.verifiedWithDropbox(app, it)
                        } ?: return@mapNotNull null
                        track to new
                    }
                replace(mediaController, replacingPairs)

                forceIndex(mediaController, index)
                onSeek?.invoke(position)

                onLoadStateChanged(state = false, onAbort = null)
            }
            return true
        }
        return false
    }

    private fun Throwable.getCausesRecursively(initial: List<Throwable> = emptyList()): List<Throwable> {
        return cause?.let { it.getCausesRecursively(initial + it) } ?: initial
    }

    fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) {
        viewModelScope.launch {
            submitQueue(QueueInfo(QueueMetadata(actionType, classType), domainTracks))
        }
    }

    fun onQueueMove(from: Int, to: Int) {
        onMoveQueuePosition?.invoke(from, to)
    }

    fun onRemoveTrackFromQueue(domainTrack: DomainTrack) {
        viewModelScope.launch {
            removeQueue(domainTrack)
        }
    }

    fun onShuffle(actionType: ShuffleActionType? = null) {
        onShuffleQueue?.invoke(actionType)
    }

    fun onResetShuffle() {
        onResetQueueOrder?.invoke()
    }

    fun onPlayOrPause(playing: Boolean?) {
        onNewPlaybackButton(if (playing == true) PlaybackButton.PAUSE else PlaybackButton.PLAY)
    }

    fun onNext() {
        onNewPlaybackButton(PlaybackButton.NEXT)
    }

    fun onPrev() {
        onNewPlaybackButton(PlaybackButton.PREV)
    }

    fun onFF(): Boolean {
        onNewPlaybackButton(PlaybackButton.FF)
        return true
    }

    fun onRewind(): Boolean {
        onNewPlaybackButton(PlaybackButton.REWIND)
        return true
    }

    fun onClickClearQueueButton() {
        clear()
    }

    fun onClickRepeatButton() {
        onRotateRepeatMode?.invoke()
    }

    private fun setEqualizer(audioSessionId: Int?) {
        viewModelScope.launch {
            if (audioSessionId != null && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (equalizer == null) {
                    try {
                        equalizer = Equalizer(0, audioSessionId).also { eq ->
                            if (app.getEqualizerParams().take(1).lastOrNull() == null) {
                                app.setEqualizerParams(
                                    EqualizerParams(
                                        levelRange = eq.bandLevelRange.let {
                                            it.first().toInt() to it.last().toInt()
                                        },
                                        bands = List(eq.numberOfBands.toInt()) { index ->
                                            EqualizerParams.Band(
                                                freqRange = eq.getBandFreqRange(index.toShort())
                                                    .let { it.first() to it.last() },
                                                centerFreq = eq.getCenterFreq(index.toShort()),
                                                level = 0
                                            )
                                        }
                                    )
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        Timber.e(t)
                    }
                }
                app.getEqualizerParams().take(1).lastOrNull()?.let {
                    reflectEqualizerSettings(it)
                }
                equalizer?.enabled = app.getEqualizerEnabled().take(1).lastOrNull() == true
            } else {
                equalizer?.enabled = false
            }
        }
    }

    private fun reflectEqualizerSettings(equalizerParams: EqualizerParams) {
        equalizerParams.bands.forEachIndexed { i, band ->
            try {
                equalizer?.setBandLevel(i.toShort(), band.level.toShort())
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun onSourceChanged(
        mediaController: MediaController
    ) = viewModelScope.launch {
        currentSourcePathsFlow.value =
            List(mediaController.mediaItemCount) {
                mediaController.getMediaItemAt(it).mediaId
            }.filter { it.isNotBlank() }.toImmutableList()
        currentIndexFlow.value = mediaController.currentIndex
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
            removeQueue(domainTrack)

            db.trackDao().deleteIncludingRootIfEmpty(db, domainTrack.id)
        }
    }

    internal fun purgeDownloaded(targetSourcePaths: List<String>): Job = viewModelScope.launch {
        if (targetSourcePaths.contains(currentSourcePathsFlow.value.getOrNull(currentIndexFlow.value))) {
            onPause?.invoke()
        }
        currentSourcePathsFlow.value.forEach { sourcePath ->
            if (targetSourcePaths.any { it == sourcePath }) {
                removeQueue(sourcePath)
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

    private fun onLoadStateChanged(state: Boolean, onAbort: (() -> Unit)? = null) {
        viewModelScope.launch { loading.emit(state to onAbort) }
    }

    internal fun onChangeRequestedTrackInQueue(domainTrack: DomainTrack) {
        val index = currentSourcePathsFlow.value.indexOf(domainTrack.sourcePath)
        onResetQueueIndex?.invoke(false, index)
    }

    internal fun onNewSeekBarProgress(progress: Long) {
        onSeek?.invoke(progress)
    }

    internal fun onNewPlaybackButton(playbackButton: PlaybackButton) {
        onNewMediaButton?.invoke(playbackButton)
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

    val MediaController.currentIndex
        get() = if (currentMediaItemIndex == -1) 0 else currentMediaItemIndex
}