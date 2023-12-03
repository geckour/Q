package com.geckour.q.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.view.KeyEvent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.work.WorkManager
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.geckour.q.App
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.service.PlayerService
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.setDropboxCredential
import com.geckour.q.util.toDomainTrack
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(private val app: App) : ViewModel() {

    companion object {

        const val DROPBOX_PATH_ROOT = "/"
    }

    private val db = DB.getInstance(app)
    internal val workManager = WorkManager.getInstance(app)
    internal val mediaRetrieveWorkInfoListFlow =
        workManager.getWorkInfosForUniqueWorkFlow(MEDIA_RETRIEVE_WORKER_NAME)

    private var isBoundService = false

    internal var isDropboxAuthOngoing = false

    internal val currentSourcePathsFlow = MutableStateFlow(emptyList<String>())
    internal val currentQueueFlow = MutableStateFlow(emptyList<DomainTrack>())
    internal val currentIndexFlow = MutableStateFlow(0)
    internal val currentPlaybackPositionFlow = MutableStateFlow(0L)
    internal val currentPlaybackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val currentRepeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val equalizerStateFlow = MutableStateFlow(false)

    internal val forceLoad = MutableLiveData<Unit>()

    private val dropboxItemListChannel =
        Channel<Pair<String, List<FolderMetadata>>>(capacity = Channel.CONFLATED)
    internal val dropboxItemList = dropboxItemListChannel.receiveAsFlow()

    internal val loading = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)

    private var onSubmitQueue: ((queueInfo: QueueInfo) -> Unit)? = null
    private var onMoveQueuePosition: ((from: Int, to: Int) -> Unit)? = null
    private var onRemoveQueueByIndex: ((index: Int) -> Unit)? = null
    internal var onRemoveQueueByTrack: (suspend (track: DomainTrack) -> Unit)? = null
    private var onShuffleQueue: ((type: ShuffleActionType?) -> Unit)? = null
    private var onResetQueueOrder: (() -> Unit)? = null
    private var onClearQueue: ((keepCurrentIfPlaying: Boolean) -> Unit)? = null
    private var onRotateRepeatMode: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onResetQueuePosition: ((position: Int) -> Unit)? = null
    private var onSeek: ((progress: Long) -> Unit)? = null
    private var onNewMediaButton: ((event: KeyEvent) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(app, PlayerService::class.java)) {
                Timber.d("qgeck called onServiceConnected")
                isBoundService = true

                val playerService = (service as? PlayerService.PlayerBinder)?.service ?: return

                viewModelScope.launch {
                    playerService.loadStateFlow.collect { (loading, onAbort) ->
                        onLoadStateChanged(loading, onAbort)
                    }
                }
                viewModelScope.launch {
                    playerService.sourcePathsFlow.collect {
                        currentSourcePathsFlow.value = it
                        currentQueueFlow.value = it.mapIndexedNotNull { index, path ->
                            DB.getInstance(app)
                                .trackDao()
                                .getBySourcePath(path)
                                ?.toDomainTrack(
                                    nowPlaying = index == currentIndexFlow.value
                                )
                        }
                    }
                }
                viewModelScope.launch {
                    playerService.currentIndexFlow.collect { index ->
                        currentIndexFlow.value = index
                        currentQueueFlow.value = currentQueueFlow.value.mapIndexed { i, item ->
                            item.copy(nowPlaying = i == index)
                        }
                    }
                }
                viewModelScope.launch {
                    playerService.playbackPositionFLow.collect {
                        currentPlaybackPositionFlow.value = it
                    }
                }
                viewModelScope.launch {
                    playerService.playbackInfoFlow.collect {
                        currentPlaybackInfoFlow.value = it
                    }
                }
                viewModelScope.launch {
                    playerService.repeatModeFlow.collect {
                        currentRepeatModeFlow.value = it
                    }
                }
                viewModelScope.launch {
                    playerService.equalizerStateFlow.collect {
                        equalizerStateFlow.value = it
                    }
                }
                viewModelScope.launch {
                    playerService.onDestroyFlow.collect { onPlayerDestroyed() }
                }

                onSubmitQueue = {
                    viewModelScope.launch {
                        playerService.submitQueue(it)
                    }
                }
                onMoveQueuePosition = { from, to ->
                    playerService.moveQueuePosition(from, to)
                }
                onRemoveQueueByIndex = {
                    playerService.removeQueue(it)
                }
                onRemoveQueueByTrack = {
                    playerService.removeQueue(it)
                }
                onShuffleQueue = {
                    playerService.shuffle(it)
                }
                onResetQueueOrder = {
                    playerService.resetQueueOrder()
                }
                onClearQueue = {
                    playerService.clear(it)
                }
                onRotateRepeatMode = {
                    playerService.rotateRepeatMode()
                }
                onPause = {
                    playerService.pause()
                }
                onResetQueuePosition = { position ->
                    playerService.resetQueuePosition(position)
                }
                onSeek = {
                    playerService.seek(it)
                }
                onNewMediaButton = {
                    playerService.onMediaButtonEvent(it)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(app, PlayerService::class.java)) {
                Timber.d("qgeck called onServiceDisconnected")
                onPlayerDestroyed()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(app, PlayerService::class.java)) {
                onPlayerDestroyed()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(app, PlayerService::class.java)) {
                onPlayerDestroyed()
            }
        }
    }

    init {
        bindPlayer()
    }

    override fun onCleared() {
        Timber.d("qgeck MainViewModel is cleared")

        unbindPlayer()
        PlayerService.destroy(app)

        super.onCleared()
    }

    fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) {
        viewModelScope.launch {
            onSubmitQueue?.invoke(QueueInfo(QueueMetadata(actionType, classType), domainTracks))
        }
    }

    fun onQueueMove(from: Int, to: Int) {
        onMoveQueuePosition?.invoke(from, to)
    }

    fun onRemoveTrackFromQueue(domainTrack: DomainTrack) {
        val index = currentQueueFlow.value.indexOf(domainTrack)
        onRemoveQueueByIndex?.invoke(index)
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
        onClearQueue?.invoke(true)
    }

    fun onClickRepeatButton() {
        onRotateRepeatMode?.invoke()
    }

    private fun bindPlayer() {
        if (isBoundService.not()) {
            app.bindService(
                PlayerService.createIntent(app),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun unbindPlayer() {
        try {
            app.startService(PlayerService.createIntent(app))
        } catch (t: Throwable) {
            Timber.e(t)
        }
        if (isBoundService) app.unbindService(serviceConnection)
    }

    internal fun onPlayerDestroyed() {
        isBoundService = false
    }

    internal fun checkDBIsEmpty(onEmpty: () -> Unit) {
        viewModelScope.launch {
            val trackCount = db.trackDao().count()
            if (trackCount == 0) onEmpty()
        }
    }

    internal fun deleteTrack(domainTrack: DomainTrack) {
        viewModelScope.launch {
            onRemoveQueueByTrack?.invoke(domainTrack)

            db.trackDao().deleteIncludingRootIfEmpty(db, domainTrack.id)
        }
    }

    internal fun onLoadStateChanged(state: Boolean, onAbort: (() -> Unit)? = null) {
        viewModelScope.launch { loading.emit(state to onAbort) }
    }

    internal fun onChangeRequestedTrackInQueue(domainTrack: DomainTrack) {
        val position = currentQueueFlow.value.indexOf(domainTrack)
        onResetQueuePosition?.invoke(position)
    }

    internal fun onNewSeekBarProgress(progress: Long) {
        onSeek?.invoke(progress)
    }

    internal fun onNewPlaybackButton(playbackButton: PlaybackButton) {
        onNewMediaButton?.invoke(
            KeyEvent(
                if (playbackButton == PlaybackButton.UNDEFINED) KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN,
                when (playbackButton) {
                    PlaybackButton.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                    PlaybackButton.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    PlaybackButton.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    PlaybackButton.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    PlaybackButton.FF -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    PlaybackButton.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
                    PlaybackButton.UNDEFINED -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
            )
        )
    }

    internal suspend fun storeDropboxApiToken() {
        val credential = Auth.getDbxCredential() ?: return
        app.setDropboxCredential(credential.toString())
        showDropboxFolderChooser()
    }

    internal fun showDropboxFolderChooser(dropboxMetadata: Metadata? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            val client = obtainDbxClient(app).firstOrNull() ?: return@launch
            var result = client.files().listFolder(dropboxMetadata?.pathLower.orEmpty())
            while (true) {
                if (result.hasMore.not()) break

                result = client.files().listFolderContinue(result.cursor)
            }
            val currentDirTitle = (dropboxMetadata?.name ?: "Root")
            dropboxItemListChannel.send(currentDirTitle to result.entries.filterIsInstance<FolderMetadata>())
        }

    internal fun clearDropboxItemList() {
        viewModelScope.launch {
            dropboxItemListChannel.send("" to emptyList())
        }
    }
}