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
import androidx.media.session.MediaButtonReceiver
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.App
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.EqualizerSettings
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.PlayerControlCommand
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.SettingCommand
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.currentSourcePaths
import com.geckour.q.util.equalizerEnabled
import com.geckour.q.util.equalizerParams
import com.geckour.q.util.equalizerSettings
import com.geckour.q.util.getMediaMetadata
import com.geckour.q.util.getMediaSource
import com.geckour.q.util.getPlayerNotification
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.verifyWithDropbox
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.IOException

class PlayerService : Service(), LifecycleOwner {

    inner class PlayerBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, PlayerService::class.java)

        private const val TAG: String = "com.geckour.q.service.PlayerService"

        private const val NOTIFICATION_ID_PLAYER = 320

        const val ARGS_KEY_CONTROL_COMMAND = "args_key_control_command"
        const val ARGS_KEY_SETTING_COMMAND = "args_key_setting_command"

        const val PREF_KEY_PLAYER_STATE = "pref_key_player_state"
    }

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

    private val player: ExoPlayer by lazy {
        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(this, renderersFactory)
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
                    override fun onLoadError(
                        eventTime: AnalyticsListener.EventTime,
                        loadEventInfo: LoadEventInfo,
                        mediaLoadData: MediaLoadData,
                        error: IOException,
                        wasCanceled: Boolean
                    ) {
                        super.onLoadError(
                            eventTime,
                            loadEventInfo,
                            mediaLoadData,
                            error,
                            wasCanceled
                        )

                        Timber.e(error)
                        FirebaseCrashlytics.getInstance().recordException(error)

                        verifyByCauseIfNeeded(error)
                    }

                    override fun onAudioSessionIdChanged(
                        eventTime: AnalyticsListener.EventTime,
                        audioSessionId: Int
                    ) {
                        super.onAudioSessionIdChanged(eventTime, audioSessionId)
                        setEqualizer(audioSessionId)
                    }
                })
            }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val currentIndex
        get() =
            if (player.currentMediaItemIndex == -1 && source.size > 0) 0
            else player.currentMediaItemIndex

    private var equalizer: Equalizer? = null

    private lateinit var db: DB

    private val cachedQueueOrder = mutableListOf<Long>()
    internal val sourcePathsFlow = MutableStateFlow(emptyList<String>())
    internal val currentIndexFlow = MutableStateFlow(0)

    /**
     * Pair: loading to onAbort
     */
    internal val loadStateFlow = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)
    internal val playbackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val playbackPositionFLow = MutableStateFlow(0L)
    internal val repeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val equalizerStateFlow = MutableStateFlow(false)
    internal val onDestroyFlow = MutableStateFlow(0L)

    private lateinit var mediaSourceFactory: ProgressiveMediaSource.Factory
    private var source = ConcatenatingMediaSource()
    internal val currentMediaSource: MediaSource?
        get() =
            if (source.size > 0 && currentIndex > -1) source.getMediaSource(currentIndex)
            else null

    private val listener = object : Player.Listener {

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)

            onSourcesChanged()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeatModeFlow.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            onSourcesChanged()

            if (source.size == 0) {
                destroyNotification()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            Timber.d("qgeck player playback state: ${when(playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN"
            }}")

            mediaSession.setPlaybackState(getPlaybackState(player.playWhenReady, playbackState))

            if (currentIndex == source.size - 1 &&
                playbackState == Player.STATE_ENDED
                && player.repeatMode == Player.REPEAT_MODE_OFF
            ) stop()

            playbackInfoFlow.value = player.playWhenReady to playbackState
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            mediaSession.setPlaybackState(getPlaybackState(playWhenReady, player.playbackState))

            playbackInfoFlow.value = playWhenReady to player.playbackState

            notificationUpdateJob.cancel()
            notificationUpdateJob = updateNotification()

            storeState()
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            storeState()

            Timber.e(error)
            FirebaseCrashlytics.getInstance().recordException(error)

            verifyByCauseIfNeeded(error)

            notificationUpdateJob.cancel()
            notificationUpdateJob = updateNotification()
        }
    }

    private var notificationUpdateJob: Job = Job()
    private var notifyPlaybackPositionJob: Job = Job()
    private var playbackCountIncreaseJob: Job = Job()
    private var seekJob: Job = Job()

    private val sharedPreferences by inject<SharedPreferences>()

    private val dropboxClient: DbxClientV2?
        get() = obtainDbxClient(sharedPreferences)

    override fun onBind(intent: Intent?): IBinder {
        dispatcher.onServicePreSuperOnBind()
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        onPlayerServiceControlAction(intent)
        onSettingAction(intent)

        return START_NOT_STICKY
    }

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override fun getLifecycle(): Lifecycle = dispatcher.lifecycle

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

        mediaSourceFactory = ProgressiveMediaSource.Factory(
            DefaultDataSource.Factory(applicationContext)
        )

        player.setMediaSource(source)
        player.prepare()

        restoreState()
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
        sourcePathsFlow.value = source.currentSourcePaths
        currentIndexFlow.value = currentIndex
        playbackPositionFLow.value = player.currentPosition
        notificationUpdateJob.cancel()
        notificationUpdateJob = updateNotification()
        playbackCountIncreaseJob = increasePlaybackCount()
        storeState()
    }

    private fun onStopServiceRequested() {
        if (player.playWhenReady.not()) stopSelf()
    }

    fun onMediaButtonEvent(event: KeyEvent) {
        mediaSession.controller?.dispatchMediaButtonEvent(event)
    }

    private fun verifyByCauseIfNeeded(throwable: Throwable) {
        val cause = when (throwable) {
            is PlaybackException,
            is IOException -> {
                throwable.cause
            }
            else -> null
        }
        if ((cause as? HttpDataSource.InvalidResponseCodeException?)?.responseCode == 410) {
            lifecycleScope.launch {
                source.currentSourcePaths[currentIndex]
                    .toDomainTrack(db)
                    ?.let { track ->
                        val new = dropboxClient?.let {
                            track.verifyWithDropbox(this@PlayerService, it)
                        } ?: track

                        track to new
                    }
                    ?.let { replace(it) }
            }
        }
    }

    suspend fun submitQueue(
        queueInfo: QueueInfo,
        force: Boolean = false
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
                if (shuffleSimple) {
                    it.shuffled()
                } else {
                    it.sortedByTrackOrder(
                        queueInfo.metadata.classType,
                        queueInfo.metadata.actionType
                    )
                }
            }
            .map { track ->
                if (alive.not()) {
                    loadStateFlow.value = false to null
                    return
                }
                (dropboxClient?.let { track.verifyWithDropbox(this, it) } ?: track)
                    .getMediaSource(mediaSourceFactory)
            }
        when (queueInfo.metadata.actionType) {
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> clear(force.not())
            else -> Unit
        }
        val needToResetSource = source.size == 0
        when (queueInfo.metadata.actionType) {
            InsertActionType.NEXT,
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_NEXT,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_NEXT,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                val position = if (source.size < 1) 0 else currentIndex + 1
                source.addMediaSources(position, newQueue)
            }

            InsertActionType.LAST,
            InsertActionType.SHUFFLE_LAST,
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                val position = source.size
                source.addMediaSources(position, newQueue)
            }
        }
        if (needToResetSource) {
            player.setMediaSource(source)
            player.prepare()
            stop()
        }

        loadStateFlow.value = false to null

        storeState()
    }

    fun moveQueuePosition(from: Int, to: Int) {
        val sourceRange = 0 until source.size
        if (from !in sourceRange || to !in sourceRange) return
        source.moveMediaSource(from, to)
    }

    fun removeQueue(position: Int) {
        if (position !in 0 until source.size ||
            player.playWhenReady
            && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            && position == currentIndex
        ) return

        source.removeMediaSource(position)
    }

    fun removeQueue(track: DomainTrack) {
        lifecycleScope.launch {
            val position = source.currentSourcePaths
                .indexOfFirst { it.toDomainTrack(db)?.id == track.id }
            removeQueue(position)
        }
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
        notifyPlaybackPositionJob = lifecycleScope.launch(Dispatchers.Main) {
            while (this.isActive) {
                playbackPositionFLow.value = player.currentPosition
                delay(100)
            }
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
        if (positionToKeep !in 0 until source.size) {
            pause()
            source.clear()
        } else {
            source.removeMediaSourceRange(0, positionToKeep)
            source.removeMediaSourceRange(1, source.size)
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
            val index = source.currentSourcePaths.indexOfFirst { it == withTrack.first.sourcePath }
            Timber.d("qgeck source path: ${withTrack.first.sourcePath}")
            Timber.d("qgeck index: $index")
            removeQueue(index)
            source.addMediaSource(index, withTrack.second.getMediaSource(mediaSourceFactory))
        }
    }

    fun next() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) seekToTail()
        else {
            if (player.currentMediaItemIndex < source.size - 1) {
                val index = player.currentMediaItemIndex + 1
                forceIndex(index)
            } else stop()
        }
    }

    private fun prev() {
        val index = if (player.currentMediaItemIndex > 0) player.currentMediaItemIndex - 1
        else 0
        player.seekToDefaultPosition(index)
    }

    fun fastForward() {
        lifecycleScope.launch {
            currentMediaSource?.toDomainTrack(db)?.let { track ->
                seekJob.cancel()
                seekJob = lifecycleScope.launch {
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

    fun rewind() {
        if (source.size > 0 && currentIndex > -1) {
            seekJob.cancel()
            seekJob = lifecycleScope.launch(Dispatchers.Main) {
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

    fun stopFastSeek() {
        seekJob.cancel()
    }

    private fun seekToHead() {
        seek(0)
    }

    private fun seekToTail() {
        lifecycleScope.launch {
            currentMediaSource?.toDomainTrack(db)?.duration?.let { seek(it) }
        }
    }

    fun headOrPrev() {
        lifecycleScope.launch {
            val currentDuration = currentMediaSource?.toDomainTrack(db)?.duration ?: return@launch

            if (currentIndex > 0 && player.contentPosition < currentDuration / 100) prev()
            else seekToHead()
        }
    }

    fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
        playbackPositionFLow.value = playbackPosition
    }

    fun shuffle(actionType: ShuffleActionType = ShuffleActionType.SHUFFLE_SIMPLE) {
        lifecycleScope.launch {
            val currentQueue = source.currentSourcePaths.mapNotNull { it.toDomainTrack(db) }
            if (source.size < 1 || source.size != currentQueue.size) return@launch

            val shuffled = when (actionType) {
                ShuffleActionType.SHUFFLE_SIMPLE -> {
                    currentQueue.shuffled().map { it.id }
                }
                ShuffleActionType.SHUFFLE_ALBUM_ORIENTED -> {
                    currentQueue.groupBy { it.album.id }
                        .map { it.value }
                        .shuffled()
                        .flatten()
                        .map { it.id }
                }
                ShuffleActionType.SHUFFLE_ARTIST_ORIENTED -> {
                    currentQueue.groupBy { it.artist.id }
                        .map { it.value }
                        .shuffled()
                        .flatten()
                        .map { it.id }
                }
            }

            reorderQueue(shuffled)
        }
    }

    fun resetQueueOrder() {
        if (source.size < 1) return
        lifecycleScope.launch {
            val currentIds = source.currentSourcePaths.mapNotNull { it.toDomainTrack(db)?.id }
            val isCacheValid =
                currentIds.size == cachedQueueOrder.size && currentIds.containsAll(cachedQueueOrder)
            if (isCacheValid.not()) return@launch

            reorderQueue(cachedQueueOrder)
        }
    }

    private fun reorderQueue(order: List<Long>) {
        lifecycleScope.launch {
            order.forEachIndexed { i, id ->
                val currentIndex = source.currentSourcePaths
                    .mapNotNull { it.toDomainTrack(db) }
                    .indexOfFirst { it.id == id }
                source.moveMediaSource(currentIndex, i)
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
        lifecycleScope.launch(Dispatchers.Main) { repeatModeFlow.emit(player.repeatMode) }
    }

    private fun getPlaybackState(playWhenReady: Boolean, playbackState: Int) =
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
                        if (playWhenReady) PlaybackState.STATE_PLAYING
                        else PlaybackState.STATE_PAUSED
                    }
                    else -> PlaybackState.STATE_ERROR
                },
                player.currentPosition,
                player.playbackParameters.speed
            )
            .build()

    private fun onPlayerServiceControlAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_CONTROL_COMMAND)) {
            val key = intent.getIntExtra(ARGS_KEY_CONTROL_COMMAND, -1)
            when (PlayerControlCommand.values()[key]) {
                PlayerControlCommand.DESTROY -> onStopServiceRequested()
            }
        }
    }

    private fun onSettingAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_SETTING_COMMAND)) {
            val key = intent.getIntExtra(ARGS_KEY_SETTING_COMMAND, -1)
            when (SettingCommand.values()[key]) {
                SettingCommand.SET_EQUALIZER -> {
                    player.audioSessionId.apply { setEqualizer(if (this != 0) this else null) }
                }
                SettingCommand.UNSET_EQUALIZER -> setEqualizer(null)
                SettingCommand.REFLECT_EQUALIZER_SETTING -> reflectEqualizerSettings()
            }
        }
    }

    private fun PlayerState.set() {
        lifecycleScope.launch(Dispatchers.Main) {
            val queueInfo = QueueInfo(
                QueueMetadata(InsertActionType.OVERRIDE, OrientedClassType.TRACK),
                db.trackDao().let { trackDao ->
                    trackIds.mapNotNull { trackDao.get(it)?.toDomainTrack() }
                }
            )
            submitQueue(queueInfo, true)
            forceIndex(currentIndex)
            seek(progress)
            player.repeatMode = repeatMode
        }
    }

    private fun forceIndex(windowIndex: Int) {
        val index = player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        player.seekToDefaultPosition(index + windowIndex)
    }

    private fun increasePlaybackCount() = lifecycleScope.launch(Dispatchers.Main) {
        currentMediaSource?.toDomainTrack(db)
            ?.let { track ->
                db.trackDao().increasePlaybackCount(track.id)
                db.albumDao().increasePlaybackCount(track.album.id)
                db.artistDao().increasePlaybackCount(track.artist.id)
            }
    }

    private fun setEqualizer(audioSessionId: Int?) {
        if (audioSessionId != null) {
            if (equalizer == null) {
                try {
                    equalizer = Equalizer(0, audioSessionId).apply {
                        val params = EqualizerParams(bandLevelRange.let { range ->
                            range.first().toInt() to range.last().toInt()
                        }, (0 until numberOfBands).map { index ->
                            val short = index.toShort()
                            EqualizerParams.Band(
                                getBandFreqRange(short).let { it.first() to it.last() },
                                getCenterFreq(short)
                            )
                        })
                        sharedPreferences.equalizerParams = params
                        if (sharedPreferences.equalizerSettings == null) {
                            sharedPreferences.equalizerSettings =
                                EqualizerSettings(params.bands.map { 0 })
                        }
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            if (sharedPreferences.equalizerEnabled) {
                reflectEqualizerSettings()
                equalizer?.enabled = true
            }
        } else {
            equalizer?.enabled = false
            equalizer = null
        }

        equalizerStateFlow.value = equalizer?.enabled == true
    }

    private fun reflectEqualizerSettings() {
        sharedPreferences.equalizerSettings?.apply {
            levels.forEachIndexed { i, level ->
                try {
                    equalizer?.setBandLevel(i.toShort(), level.toShort())
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    private fun storeState(
        playWhenReady: Boolean = player.playWhenReady,
        currentIndex: Int = this.currentIndex,
        duration: Long = player.duration,
        progress: Long = player.currentPosition,
        repeatMode: Int = player.repeatMode
    ) = lifecycleScope.launch {
        cachedQueueOrder.clear()
        val trackIds = source.currentSourcePaths.mapNotNull {
            it.toDomainTrack(db)?.id
        }
        cachedQueueOrder.addAll(trackIds)
        val state = PlayerState(
            playWhenReady,
            trackIds,
            currentIndex,
            duration,
            progress,
            repeatMode
        )
        sharedPreferences.edit {
            putString(PREF_KEY_PLAYER_STATE, Json.encodeToString(state))
        }
    }

    private fun restoreState() {
        if (player.playWhenReady.not()) {
            sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)?.let {
                Json.decodeFromString<PlayerState>(it)
            }?.set()
        }
    }

    private fun updateNotification() = lifecycleScope.launch {
        val mediaMetadata = currentMediaSource?.toDomainTrack(db)
            ?.getMediaMetadata(this@PlayerService) ?: return@launch
        mediaSession.setPlaybackState(getPlaybackState(player.playWhenReady, player.playbackState))
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

    private fun Notification.show() {
        val isInForeground =
            Build.VERSION.SDK_INT >= 29 && foregroundServiceType != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
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