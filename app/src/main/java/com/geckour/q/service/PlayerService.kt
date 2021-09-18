package com.geckour.q.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
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
import com.geckour.q.util.ducking
import com.geckour.q.util.equalizerEnabled
import com.geckour.q.util.equalizerParams
import com.geckour.q.util.equalizerSettings
import com.geckour.q.util.getMediaMetadata
import com.geckour.q.util.getMediaSource
import com.geckour.q.util.getPlayerNotification
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.shuffleByClassType
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.verifyWithDropbox
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) ?: return false
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

    private val player: SimpleExoPlayer by lazy {
        ducking = sharedPreferences.ducking
        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        SimpleExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(listener)
                setAudioAttributes(AudioAttributes.Builder().build(), ducking)
                addAnalyticsListener(object : EventLogger(trackSelector) {
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

    lateinit var mediaSession: MediaSessionCompat
    private val playbackStateCompat = PlaybackStateCompat.Builder()
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
    private val currentIndex
        get() =
            if (player.currentWindowIndex == -1 && source.size > 0) 0
            else player.currentWindowIndex

    private var equalizer: Equalizer? = null

    private lateinit var db: DB

    private val cachedQueueOrder = mutableListOf<Long>()
    internal val sourcePathsFlow = MutableStateFlow(emptyList<String>())
    internal val currentIndexFlow = MutableStateFlow(0)
    internal val loadStateFlow = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)
    internal val playbackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val playbackPositionFLow = MutableStateFlow(0L)
    internal val repeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val equalizerStateFlow = MutableStateFlow(false)
    internal val onDestroyFlow = MutableSharedFlow<Unit>()

    private lateinit var mediaSourceFactory: ProgressiveMediaSource.Factory
    private var source = ConcatenatingMediaSource()
    internal val currentMediaSource: MediaSource?
        get() =
            if (source.size > 0 && currentIndex > -1) source.getMediaSource(currentIndex)
            else null

    private val listener = object : Player.Listener {

        override fun onTracksChanged(
            trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray
        ) {
            sourcePathsFlow.value = source.currentSourcePaths
            currentIndexFlow.value = currentIndex
            playbackPositionFLow.value = player.currentPosition
            notificationUpdateJob.cancel()
            notificationUpdateJob = showNotification()
            playbackCountIncreaseJob = increasePlaybackCount()
        }

        override fun onLoadingChanged(isLoading: Boolean) = Unit

        override fun onPositionDiscontinuity(reason: Int) = Unit

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeatModeFlow.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {

            if (source.size < 1) {
                destroyNotification()
            }
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        getPlaybackState(playWhenReady, playbackState),
                        player.currentPosition,
                        1f
                    )
                    .build()
            )

            if (currentIndex == source.size - 1 &&
                playbackState == Player.STATE_ENDED
                && player.repeatMode == Player.REPEAT_MODE_OFF
            ) stop()

            if (playbackState == Player.STATE_READY) {
                notificationUpdateJob.cancel()
                notificationUpdateJob = showNotification()
            }

            playbackInfoFlow.value = playWhenReady to playbackState
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            Timber.e(error)
            FirebaseCrashlytics.getInstance().recordException(error)

            verifyByCauseIfNeeded(error)
        }
    }

    private var notificationUpdateJob: Job = Job()
    private var notifyPlaybackPositionJob: Job = Job()
    private var playbackCountIncreaseJob: Job = Job()
    private var seekJob: Job = Job()

    private var ducking: Boolean = false
    fun getDuking(): Boolean = ducking

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private lateinit var dropboxClient: DbxClientV2

    override fun onBind(intent: Intent?): IBinder {
        dispatcher.onServicePreSuperOnBind()
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        onPlayerControlAction(intent)
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
            DefaultDataSourceFactory(
                applicationContext,
                Util.getUserAgent(applicationContext, packageName)
            )
        )

        player.setMediaSource(source)
        player.prepare()

        dropboxClient = obtainDbxClient()

        restoreState()
    }

    override fun onStart(intent: Intent?, startId: Int) {
        dispatcher.onServicePreSuperOnStart()

        super.onStart(intent, startId)
    }

    override fun onDestroy() {
        Timber.d("qgeck onDestroy called")

        stop()
        player.stop()
        player.clearMediaItems()
        destroyNotification()
        dispatcher.onServicePreSuperOnDestroy()
        player.release()

        onDestroyFlow.tryEmit(Unit)

        super.onDestroy()
    }

    private fun onStopServiceRequested() {
        if (player.playWhenReady.not()) stopSelf()
    }

    fun onMediaButtonEvent(event: KeyEvent) {
        mediaSession.controller?.dispatchMediaButtonEvent(event)
    }

    private fun verifyByCauseIfNeeded(throwable: Throwable) {
        if ((throwable as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 410) {
            lifecycleScope.launch {
                source.currentSourcePaths[currentIndex]
                    .toDomainTrack(db)
                    ?.verifyWithDropbox(this@PlayerService, dropboxClient)
                    ?.let { replace(it) }
            }
        }
    }

    suspend fun submitQueue(
        queueInfo: QueueInfo,
        force: Boolean = false
    ) {
        var enabled = true
        loadStateFlow.value = true to { enabled = false }

        val newQueue = queueInfo.queue.map {
            if (enabled.not()) {
                loadStateFlow.value = false to null
                return
            }
            it.verifyWithDropbox(this, dropboxClient)
        }
        val needToResetSource = when (queueInfo.metadata.actionType) {
            InsertActionType.NEXT -> {
                val isEmpty = source.size == 0
                val position = if (source.size < 1) 0 else currentIndex + 1
                source.addMediaSources(position,
                    newQueue.map { it.getMediaSource(mediaSourceFactory) })
                isEmpty
            }
            InsertActionType.LAST -> {
                val isEmpty = source.size == 0
                val position = source.size
                source.addMediaSources(position,
                    newQueue.map { it.getMediaSource(mediaSourceFactory) })
                isEmpty
            }
            InsertActionType.OVERRIDE -> {
                override(newQueue, force)
                true
            }
            InsertActionType.SHUFFLE_NEXT -> {
                val isEmpty = source.size == 0
                val position = if (source.size < 1) 0 else currentIndex + 1
                val shuffled = newQueue.shuffleByClassType(queueInfo.metadata.classType)

                source.addMediaSources(position,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
                isEmpty
            }
            InsertActionType.SHUFFLE_LAST -> {
                val isEmpty = source.size == 0
                val position = source.size
                val shuffled = newQueue.shuffleByClassType(queueInfo.metadata.classType)

                source.addMediaSources(position,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
                isEmpty
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                val shuffled = newQueue.shuffleByClassType(queueInfo.metadata.classType)
                override(shuffled, force)
                true
            }
            InsertActionType.SHUFFLE_SIMPLE_NEXT -> {
                val isEmpty = source.size == 0
                val position = if (source.size < 1) 0 else currentIndex + 1
                val shuffled = newQueue.shuffled()

                source.addMediaSources(position,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
                isEmpty
            }
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                val isEmpty = source.size == 0
                val position = source.size
                val shuffled = newQueue.shuffled()

                source.addMediaSources(position,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
                isEmpty
            }
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                val shuffled = newQueue.shuffled()

                override(shuffled, force)
                true
            }
        }
        if (needToResetSource) {
            player.setMediaSource(source)
            player.prepare()
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
        getSystemService(AudioManager::class.java)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioFocusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .build()
                requestAudioFocus(audioFocusRequest)
            } else {
                requestAudioFocus(
                    {},
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }

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

        if (player.playWhenReady) storeState()

        player.playWhenReady = false
        notifyPlaybackPositionJob.cancel()
        getSystemService(AudioManager::class.java)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioFocusRequest = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ).build()
                abandonAudioFocusRequest(audioFocusRequest)
            } else {
                abandonAudioFocus {}
            }
        }
    }

    fun togglePlayPause() {
        if (player.playWhenReady) pause()
        else {
            if (player.playbackState == Player.STATE_READY) resume()
            else play()
        }
    }

    fun stop() {
        pause()
        forceIndex(0)
        storeState()
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
            destroyNotification()
            source.clear()
        } else {
            source.removeMediaSourceRange(0, positionToKeep)
            source.removeMediaSourceRange(1, source.size)
        }
        storeState()
    }

    fun override(queue: List<DomainTrack>, force: Boolean = false) {
        clear(force.not())
        source.addMediaSources(queue.map { it.getMediaSource(mediaSourceFactory) })
    }

    private fun replace(with: DomainTrack) {
        replace(listOf(with))
    }

    private fun replace(with: List<DomainTrack>) {
        if (with.isEmpty()) return

        with.forEach { withTrack ->
            val index = source.currentSourcePaths
                .indexOfFirst {
                    val path = withTrack.dropboxPath ?: withTrack.sourcePath
                    it == path
                }
            removeQueue(index)
            source.addMediaSource(index, withTrack.getMediaSource(mediaSourceFactory))
        }
    }

    fun next() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) seekToTail()
        else {
            if (player.currentWindowIndex < source.size - 1) {
                val index = player.currentWindowIndex + 1
                forceIndex(index)
            } else stop()
        }
    }

    private fun prev() {
        val index = if (player.currentWindowIndex > 0) player.currentWindowIndex - 1
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
            if (source.size < 1 && source.size != currentQueue.size) return@launch

            val shuffled = when (actionType) {
                ShuffleActionType.SHUFFLE_SIMPLE -> currentQueue.map { it.id }.shuffled()
                ShuffleActionType.SHUFFLE_ALBUM_ORIENTED -> {
                    currentQueue.groupBy { it.album.id }
                        .map { it.value }
                        .shuffled()
                        .flatten()
                        .map { it.id }
                }
                ShuffleActionType.SHUFFLE_ARTIST_ORIENTED -> {
                    currentQueue.groupBy { it.artist }
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
        when (playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
            Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
            Player.STATE_IDLE -> PlaybackState.STATE_NONE
            Player.STATE_READY -> {
                if (playWhenReady) PlaybackState.STATE_PLAYING
                else PlaybackState.STATE_PAUSED
            }
            else -> PlaybackState.STATE_ERROR
        }

    private fun onPlayerControlAction(intent: Intent) {
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
        playbackPositionFLow.value = player.currentPosition
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

    @OptIn(ExperimentalSerializationApi::class)
    private fun storeState() {
        cachedQueueOrder.apply {
            clear()
            lifecycleScope.launch {
                val trackIds = source.currentSourcePaths.mapNotNull {
                    it.toDomainTrack(db)?.id
                }
                addAll(trackIds)
                val state = PlayerState(
                    player.playWhenReady,
                    trackIds,
                    currentIndex,
                    player.currentPosition,
                    player.repeatMode
                )
                sharedPreferences.edit()
                    .putString(PREF_KEY_PLAYER_STATE, Json.encodeToString(state))
                    .apply()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun restoreState() {
        if (player.playWhenReady.not()) {
            sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)?.let {
                Json.decodeFromString<PlayerState>(it)
            }?.set()
        }
    }

    private fun showNotification() = lifecycleScope.launch(Dispatchers.Main) {
        val mediaMetadata = currentMediaSource?.toDomainTrack(db)
            ?.getMediaMetadata(this@PlayerService) ?: return@launch
        mediaSession.setPlaybackState(
            playbackStateCompat.setState(
                player.playbackState.toPlaybackStateCompat,
                player.currentPosition,
                player.playbackParameters.speed
            ).build()
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

    private fun Notification.show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            Timber.d("qgeck starting foreground player service")
            startForeground(NOTIFICATION_ID_PLAYER, this)
        } else {
            Timber.d("qgeck stopping foreground player service")
            stopForeground(false)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID_PLAYER, this)
        }
    }

    private fun destroyNotification() {
        mediaSession.isActive = false
        notificationUpdateJob.cancel()
        stopForeground(true)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID_PLAYER)
    }

    private val Int.toPlaybackStateCompat
        get() = when (this) {
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_READY -> {
                if (player.playWhenReady) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED
            }
            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackState.STATE_NONE
        }
}