package com.geckour.q.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.geckour.q.App
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.PlayerControlCommand
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.currentSourcePaths
import com.geckour.q.util.dropboxCachePathPattern
import com.geckour.q.util.getEqualizerEnabled
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getMediaItem
import com.geckour.q.util.getMediaMetadata
import com.geckour.q.util.getPlayerNotification
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.removedAt
import com.geckour.q.util.setEqualizerParams
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toDomainTracks
import com.geckour.q.util.verifiedWithDropbox
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.FileNotFoundException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerService : Service(), LifecycleOwner {

    inner class PlayerBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    companion object {

        private const val TAG: String = "com.geckour.q.service.PlayerService"

        private const val NOTIFICATION_ID_PLAYER = 320

        const val ARGS_KEY_CONTROL_COMMAND = "args_key_control_command"

        const val PREF_KEY_PLAYER_STATE = "pref_key_player_state"

        fun createIntent(context: Context): Intent = Intent(context, PlayerService::class.java)

        fun destroy(context: Context) {
            context.startService(
                createIntent(context).putExtra(
                    ARGS_KEY_CONTROL_COMMAND,
                    PlayerControlCommand.DESTROY
                )
            )
        }
    }

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private val binder = PlayerBinder()

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            super.onPlay()
            play()
        }

        override fun onPause() {
            super.onPause()
            pause()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            next()
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            headOrPrev()
        }

        override fun onFastForward() {
            super.onFastForward()
            fastForward()
        }

        override fun onRewind() {
            super.onRewind()
            rewind()
        }

        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            seek(pos)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val keyEvent: KeyEvent =
                if (Build.VERSION.SDK_INT >= 33) {
                    mediaButtonEvent.getParcelableExtra(
                        Intent.EXTRA_KEY_EVENT,
                        KeyEvent::class.java
                    )
                } else {
                    mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                } ?: return false
            return when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            onPlay()
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            onPause()
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            onPlayPause()
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                            onSkipToNext()
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                            onSkipToPrevious()
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            onFastForward()
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            onRewind()
                            true
                        }

                        else -> false
                    }
                }

                KeyEvent.ACTION_UP -> {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            stopFastSeek()
                            true
                        }

                        else -> false
                    }
                }

                else -> false
            }
        }

        private fun onPlayPause() {
            togglePlayPause()
        }
    }

    private lateinit var player: ExoPlayer

    private lateinit var mediaSession: MediaSessionCompat
    private val currentIndex
        get() =
            if (player.currentMediaItemIndex == -1) 0
            else player.currentMediaItemIndex

    private var equalizer: Equalizer? = null

    private lateinit var db: DB

    private val cachedSourcePaths = mutableListOf<String>()
    internal val sourcePathsFlow = MutableStateFlow(emptyList<String>())
    internal val currentIndexFlow = MutableStateFlow(0)

    /**
     * Pair: isLoading to onAbort
     */
    internal val loadStateFlow = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)
    internal val playbackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val playbackPositionFLow = MutableStateFlow(0L)
    internal val repeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val onDestroyFlow = MutableStateFlow(0L)

    private val listener = object : Player.Listener {

        var lastMediaItem: MediaItem? = null

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)

            Timber.d("qgeck player tracks changed: ${tracks.groups}")

            onSourcesChanged()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeatModeFlow.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            Timber.d("qgeck player on timeline changed: $timeline, $reason")

            onSourcesChanged()

            if (timeline.isEmpty) {
                destroyNotification()
            }
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

            mediaSession.setPlaybackState(
                getPlaybackState(
                    player.isPlaying,
                    playbackState,
                    player.currentPosition
                )
            )

            if (currentIndex == player.mediaItemCount - 1
                && playbackState == Player.STATE_ENDED
                && player.repeatMode == Player.REPEAT_MODE_OFF
            ) {
                stop()
                onSourcesChanged()
            }

            playbackInfoFlow.value = player.playWhenReady to playbackState

            notificationUpdateJob.cancel()
            notificationUpdateJob = updateNotification()

            if (playbackState == Player.STATE_READY &&
                player.playWhenReady &&
                player.currentMediaItem != lastMediaItem
            ) {
                lastMediaItem = player.currentMediaItem
                playbackCountIncreaseJob = increasePlaybackCount()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            Timber.d("qgeck player play when ready: $playWhenReady")

            mediaSession.setPlaybackState(
                getPlaybackState(
                    player.isPlaying,
                    player.playbackState,
                    player.currentPosition
                )
            )

            playbackInfoFlow.value = playWhenReady to player.playbackState

            notificationUpdateJob.cancel()
            notificationUpdateJob = updateNotification()

            storeState()

            if (player.playbackState == Player.STATE_READY &&
                playWhenReady &&
                player.currentMediaItem != lastMediaItem
            ) {
                lastMediaItem = player.currentMediaItem
                playbackCountIncreaseJob = increasePlaybackCount()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            Timber.e(error)
            FirebaseCrashlytics.getInstance().recordException(error)

            val currentPlayWhenReady = player.playWhenReady
            pause()
            if (verifyByCauseIfNeeded(error).not()) {
                removeQueue(currentIndex)
            }
            if (currentPlayWhenReady) resume()

            notificationUpdateJob.cancel()
            notificationUpdateJob = updateNotification()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)

            setEqualizer(audioSessionId)
        }
    }

    private var notificationUpdateJob: Job = Job()
    private var notifyPlaybackPositionJob: Job = Job()
    private var playbackCountIncreaseJob: Job = Job()
    private var seekJob: Job = Job()

    private val sharedPreferences by inject<SharedPreferences>()

    override fun onBind(intent: Intent): IBinder {
        dispatcher.onServicePreSuperOnBind()

        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        onPlayerServiceControlAction(intent)

        return START_NOT_STICKY
    }

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()

        super.onCreate()

        db = DB.getInstance(this@PlayerService)

        mediaSession = MediaSessionCompat(this, TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(mediaSessionCallback)
            isActive = false
        }

        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(listener)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                addAnalyticsListener(object : EventLogger() {

                    override fun onAudioSessionIdChanged(
                        eventTime: AnalyticsListener.EventTime,
                        audioSessionId: Int
                    ) {
                        super.onAudioSessionIdChanged(eventTime, audioSessionId)
                        setEqualizer(audioSessionId)
                    }
                })
            }

        restoreState()

        lifecycleScope.launch {
            getEqualizerParams().collectLatest { params ->
                params?.let { reflectEqualizerSettings(it) }
            }
        }
        lifecycleScope.launch {
            getEqualizerEnabled().collectLatest { enabled ->
                setEqualizer(if (enabled) player.audioSessionId else null)
            }
        }
    }

    override fun onDestroy() {
        Timber.d("qgeck onDestroy called")

        stop()
        player.stop()
        player.clearMediaItems()
        destroyNotification()
        dispatcher.onServicePreSuperOnDestroy()
        player.release()

        onDestroyFlow.value = System.currentTimeMillis()

        super.onDestroy()
    }

    private fun onSourcesChanged() {
        sourcePathsFlow.value = player.currentSourcePaths
        currentIndexFlow.value = currentIndex
        playbackPositionFLow.value = player.currentPosition
        notificationUpdateJob.cancel()
        notificationUpdateJob = updateNotification()
        storeState()
        setEqualizer(player.audioSessionId)
    }

    private fun onStopServiceRequested() {
        if (player.playWhenReady.not()) {
            stopSelf()
            destroyNotification()
        }
    }

    fun onMediaButtonEvent(event: KeyEvent) {
        mediaSession.controller?.dispatchMediaButtonEvent(event)
    }

    private fun Throwable.getCausesRecursively(initial: List<Throwable> = emptyList()): List<Throwable> {
        return cause?.let { it.getCausesRecursively(initial + it) } ?: initial
    }

    private fun verifyByCauseIfNeeded(throwable: Throwable): Boolean {
        val isTarget =
            throwable.getCausesRecursively().apply { Timber.d("qgeck causes: $this") }.any {
                (it as? HttpDataSource.InvalidResponseCodeException?)?.responseCode == 410 ||
                        (it is FileNotFoundException &&
                                player.currentSourcePaths
                                    .getOrNull(currentIndex)
                                    .apply { Timber.d("qgeck source path: $this") }
                                    ?.matches(dropboxCachePathPattern) == true)
            }
        if (isTarget) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    loadStateFlow.value = true to null
                    val index = currentIndex
                    val position = player.currentPosition
                    player.currentSourcePaths
                        .forEach { path ->
                            val track = path.toDomainTrack(db) ?: return@forEach
                            val new = obtainDbxClient(this@PlayerService).firstOrNull()?.let {
                                track.verifiedWithDropbox(this@PlayerService, it)
                            } ?: return@forEach

                            replace(track to new)
                        }

                    forceIndex(index)
                    seek(position)

                    loadStateFlow.value = false to null
                }
            }
            return true
        }
        return false
    }

    suspend fun submitQueue(
        queueInfo: QueueInfo,
        positionToKeep: Int? = null,
        needSorted: Boolean = true,
    ) {
        var alive = true
        loadStateFlow.value = true to { alive = false }

        val shuffleSimple = queueInfo.metadata.actionType in listOf(
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_NEXT,
            InsertActionType.SHUFFLE_SIMPLE_LAST,
        )

        val newQueue = queueInfo.queue
            .let {
                when {
                    shuffleSimple -> it.shuffled()
                    needSorted -> it.sortedByTrackOrder(
                        queueInfo.metadata.classType,
                        queueInfo.metadata.actionType
                    )

                    else -> it
                }
            }
            .map { track ->
                if (alive.not()) {
                    loadStateFlow.value = false to null
                    return
                }
                (obtainDbxClient(this).firstOrNull()?.let {
                    track.verifiedWithDropbox(this, it)
                } ?: track)
                    .getMediaItem()
            }
        when (queueInfo.metadata.actionType) {
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                if (positionToKeep == null) clear()
                else clear(positionToKeep)
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
                val position = if (player.mediaItemCount < 1) 0 else currentIndex + 1
                player.addMediaItems(position, newQueue)
            }

            InsertActionType.LAST,
            InsertActionType.SHUFFLE_LAST,
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                val position = player.mediaItemCount
                player.addMediaItems(position, newQueue)
            }
        }

        loadStateFlow.value = false to null
        storeState()
    }

    fun moveQueuePosition(from: Int, to: Int) {
        if (from == to) return
        val sourceRange = 0 until player.mediaItemCount
        if (from !in sourceRange || to !in sourceRange) return
        player.moveMediaItem(from, to)
    }

    fun removeQueue(position: Int) {
        if (position !in 0 until player.mediaItemCount ||
            player.playWhenReady
            && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            && position == currentIndex
        ) return

        player.removeMediaItem(position)
    }

    suspend fun removeQueue(track: DomainTrack) {
        val position = player.currentSourcePaths
            .indexOfFirst { it.toDomainTrack(db)?.id == track.id }
        removeQueue(position)
    }

    private fun play() {
        Timber.d("qgeck play invoked")

        resume()
    }

    fun resetQueuePosition(position: Int, force: Boolean = false) {
        if (force || currentIndex != position) forceIndex(position)
    }

    private fun resume() {
        Timber.d("qgeck resume invoked")

        notifyPlaybackPositionJob.cancel()
        notifyPlaybackPositionJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (this.isActive) {
                    playbackPositionFLow.value = player.currentPosition
                    delay(100)
                }
            }
        }

        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        if (player.playWhenReady.not()) {
            player.playWhenReady = true
        }
    }

    fun pause() {
        Timber.d("qgeck pause invoked")

        player.playWhenReady = false
        notifyPlaybackPositionJob.cancel()
    }

    fun togglePlayPause() {
        if (player.playWhenReady) pause()
        else resume()
    }

    fun stop() {
        pause()
        forceIndex(0)
    }

    fun clear(keepCurrentIfPlaying: Boolean = false) {
        val needToKeepCurrent = keepCurrentIfPlaying
                && player.playbackState == Player.STATE_READY
                && player.playWhenReady
        clear(if (needToKeepCurrent) currentIndex else -1)
    }

    private fun clear(positionToKeep: Int) {
        if (positionToKeep !in 0 until player.mediaItemCount) {
            player.clearMediaItems()
        } else {
            player.removeMediaItems(0, positionToKeep)
            player.removeMediaItems(1, player.mediaItemCount)
        }
    }

    /**
     * @param with: first: old, second: new
     */
    private fun replace(with: Pair<DomainTrack, DomainTrack>) {
        replace(listOf(with))
    }

    private fun replace(with: List<Pair<DomainTrack, DomainTrack>>) {
        if (with.isEmpty()) return

        with.forEach { withTrack ->
            val index = player.currentSourcePaths.indexOfFirst { it == withTrack.first.sourcePath }
            Timber.d("qgeck source path: ${withTrack.first.sourcePath}")
            Timber.d("qgeck index: $index")
            removeQueue(index)
            player.addMediaItem(
                index,
                withTrack.second.getMediaItem()
            )
        }
    }

    fun next() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) seekToTail()
        else {
            if (player.currentMediaItemIndex < player.mediaItemCount - 1) {
                val index = player.currentMediaItemIndex + 1
                forceIndex(index)
            } else stop()
        }
    }

    private fun prev() {
        val windowIndex = player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        val index = if (player.currentMediaItemIndex > 0) player.currentMediaItemIndex - 1
        else 0
        player.seekToDefaultPosition(windowIndex + index)
        currentIndexFlow.value = index
    }

    fun fastForward() {
        lifecycleScope.launch {
            player.currentMediaItem?.toDomainTrack(db)?.let { track ->
                seekJob.cancel()
                seekJob = lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            withContext(Dispatchers.Main) {
                                val seekTo = (player.currentPosition + 1000).let {
                                    if (it > track.duration) track.duration else it
                                }
                                seek(seekTo)
                            }
                            delay(100)
                        }
                    }
                }
            }
        }
    }

    fun rewind() {
        if (player.mediaItemCount > 0 && currentIndex > -1) {
            seekJob.cancel()
            seekJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        val seekTo = (player.currentPosition - 1000).let {
                            if (it < 0) 0 else it
                        }
                        seek(seekTo)
                        delay(100)
                    }
                }
            }
        }
    }

    fun stopFastSeek() {
        seekJob.cancel()
    }

    private fun seekToHead() {
        seek(0)
    }

    private fun seekToTail() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.currentMediaItem?.toDomainTrack(db)?.duration?.let { seek(it) }
            }
        }
    }

    fun headOrPrev() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val currentDuration =
                    player.currentMediaItem?.toDomainTrack(db)?.duration ?: return@repeatOnLifecycle

                if (currentIndex > 0 && player.contentPosition < currentDuration / 100) prev()
                else seekToHead()
            }
        }
    }

    fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
        mediaSession.setPlaybackState(
            getPlaybackState(
                player.isPlaying,
                player.playbackState,
                playbackPosition
            )
        )
        playbackPositionFLow.value = playbackPosition
    }

    fun shuffle(actionType: ShuffleActionType? = null) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val currentQueue = player.currentSourcePaths
                if (player.mediaItemCount < 1 || player.mediaItemCount != currentQueue.size) return@repeatOnLifecycle

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

                reorderQueue(shuffled)
            }
        }
    }

    fun resetQueueOrder() {
        if (player.mediaItemCount < 1) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val sourcePaths = player.currentSourcePaths
                val isCacheValid =
                    sourcePaths.size == cachedSourcePaths.size && sourcePaths.containsAll(
                        cachedSourcePaths
                    )
                if (isCacheValid.not()) return@repeatOnLifecycle

                reorderQueue(cachedSourcePaths)
            }
        }
    }

    private fun reorderQueue(newSourcePaths: List<String>) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val targetIndex = newSourcePaths.indexOfFirst {
                    it == player.currentSourcePaths.getOrNull(currentIndex)
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
                moveQueuePosition(currentIndex, targetIndex)
            }
        }
    }

    fun rotateRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repeatModeFlow.emit(player.repeatMode)
            }
        }
    }

    private fun getPlaybackState(isPlaying: Boolean, playbackState: Int, playbackPosition: Long) =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
                    Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
                    Player.STATE_IDLE -> PlaybackState.STATE_NONE
                    Player.STATE_READY -> {
                        if (isPlaying) PlaybackState.STATE_PLAYING
                        else PlaybackState.STATE_PAUSED
                    }

                    else -> PlaybackState.STATE_ERROR
                },
                playbackPosition,
                player.playbackParameters.speed
            )
            .build()

    private fun onPlayerServiceControlAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_CONTROL_COMMAND)) {
            val key = if (Build.VERSION.SDK_INT > 32) {
                intent.getSerializableExtra(
                    ARGS_KEY_CONTROL_COMMAND,
                    PlayerControlCommand::class.java
                )
            } else {
                intent.getSerializableExtra(ARGS_KEY_CONTROL_COMMAND)
            }
            when (key) {
                PlayerControlCommand.DESTROY -> onStopServiceRequested()
            }
        }
    }

    private fun PlayerState.set() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                loadStateFlow.value = true to {}
                val queueInfo = QueueInfo(
                    QueueMetadata(InsertActionType.OVERRIDE, OrientedClassType.TRACK),
                    sourcePaths.mapNotNull { it.toDomainTrack(db) }
                )
                loadStateFlow.value = false to {}
                submitQueue(queueInfo, needSorted = false)
                forceIndex(currentIndex)
                seek(progress)
                player.repeatMode = repeatMode
            }
        }
    }

    private fun forceIndex(index: Int) {
        val windowIndex = player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        player.seekToDefaultPosition(windowIndex + index)
        currentIndexFlow.value = index
    }

    private fun increasePlaybackCount() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            player.currentMediaItem?.toDomainTrack(db)
                ?.let { track ->
                    db.trackDao().increasePlaybackCount(track.id)
                    db.albumDao().increasePlaybackCount(track.album.id)
                    db.artistDao().increasePlaybackCount(track.artist.id)
                }
        }
    }

    private fun setEqualizer(audioSessionId: Int?) {
        lifecycleScope.launch {
            player.audioSessionId
            if (audioSessionId != null && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (equalizer == null) {
                    try {
                        equalizer = Equalizer(0, audioSessionId).also { eq ->
                            if (getEqualizerParams().take(1).lastOrNull() == null) {
                                setEqualizerParams(
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
                getEqualizerParams().take(1).lastOrNull()?.let {
                    reflectEqualizerSettings(it)
                }
                equalizer?.enabled = getEqualizerEnabled().take(1).lastOrNull() == true
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

    private fun storeState(
        playWhenReady: Boolean = player.playWhenReady,
        sourcePaths: List<String> = player.currentSourcePaths,
        currentIndex: Int = this.currentIndex,
        duration: Long = player.duration,
        progress: Long = player.currentPosition,
        repeatMode: Int = player.repeatMode
    ) = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            cachedSourcePaths.clear()
            cachedSourcePaths.addAll(sourcePaths)
            val state = PlayerState(
                playWhenReady,
                sourcePaths,
                currentIndex,
                duration,
                progress,
                repeatMode
            )
            sharedPreferences.edit {
                putString(PREF_KEY_PLAYER_STATE, Json.encodeToString(state))
            }
        }
    }

    private fun restoreState() {
        if (player.playWhenReady.not()) {
            sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)
                ?.let { catchAsNull { Json.decodeFromString<PlayerState>(it) } }
                ?.set()
        }
    }

    private fun updateNotification() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            val mediaMetadata = player.currentMediaItem?.toDomainTrack(db)
                ?.getMediaMetadata(this@PlayerService) ?: return@repeatOnLifecycle
            mediaSession.setPlaybackState(
                getPlaybackState(
                    player.isPlaying,
                    player.playbackState,
                    player.currentPosition
                )
            )
            mediaSession.setMetadata(mediaMetadata)
            mediaSession.isActive = true
            mediaSession.setSessionActivity(
                PendingIntent.getActivity(
                    this@PlayerService,
                    App.REQUEST_CODE_LAUNCH_APP,
                    LauncherActivity.createIntent(this@PlayerService),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            getPlayerNotification(
                this@PlayerService,
                mediaSession,
                player.isPlaying
            ).show()
        }
    }

    private fun Notification.show() {
        val isInForeground = Build.VERSION.SDK_INT >= 29
                && foregroundServiceType != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
        if (isInForeground || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.notify(
                NOTIFICATION_ID_PLAYER,
                this
            )
            return
        }

        Timber.d("qgeck starting foreground player service")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID_PLAYER,
                this,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID_PLAYER, this)
        }
    }

    private fun destroyNotification() {
        mediaSession.isActive = false
        notificationUpdateJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID_PLAYER)
    }
}