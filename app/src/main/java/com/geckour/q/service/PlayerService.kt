package com.geckour.q.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.geckour.q.util.addedAll
import com.geckour.q.util.ducking
import com.geckour.q.util.equalizerEnabled
import com.geckour.q.util.equalizerParams
import com.geckour.q.util.equalizerSettings
import com.geckour.q.util.getMediaMetadata
import com.geckour.q.util.getMediaSource
import com.geckour.q.util.getPlayerNotification
import com.geckour.q.util.moved
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.removedAt
import com.geckour.q.util.shuffleByClassType
import com.geckour.q.util.swapped
import com.geckour.q.util.verifyWithDropbox
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.AudioListener
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.CoroutineContext

class PlayerService : Service() {

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

        private const val SOURCE_ACTION_WIRED_STATE = Intent.ACTION_HEADSET_PLUG
        private const val SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE =
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
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
            .build()
            .apply {
                addAudioListener(object : AudioListener {

                    override fun onAudioSessionId(audioSessionId: Int) {
                        super.onAudioSessionId(audioSessionId)

                        setEqualizer(audioSessionId)
                    }
                })
                addListener(eventListener)
                setAudioAttributes(AudioAttributes.Builder().build(), ducking)
                addAnalyticsListener(object : EventLogger(trackSelector) {
                    override fun onLoadError(
                        eventTime: AnalyticsListener.EventTime,
                        loadEventInfo: MediaSourceEventListener.LoadEventInfo,
                        mediaLoadData: MediaSourceEventListener.MediaLoadData,
                        error: IOException,
                        wasCanceled: Boolean
                    ) {
                        Timber.e(error)
                        FirebaseCrashlytics.getInstance().recordException(error)

                        verifyByCauseIfNeeded(error)
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
        get() = when (player.currentWindowIndex) {
            -1 -> if (source.size > 0) 0 else -1
            else -> player.currentWindowIndex
        }

    private val currentDomainTrack: DomainTrack?
        get() = queueFlow.value.getOrNull(currentIndex)

    private var equalizer: Equalizer? = null

    internal val queueFlow = MutableStateFlow<List<DomainTrack>>(emptyList())
    private val cachedQueueOrder = mutableListOf<Long>()
    internal val currentIndexFlow = MutableStateFlow(0)
    internal val loadStateFlow = MutableStateFlow(false)
    internal val playbackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val playbackPositionFLow = MutableStateFlow(0L)
    internal val repeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    private var onEqualizerStateChanged: ((Boolean) -> Unit)? = null
    private var onDestroyed: (() -> Unit)? = null

    private lateinit var mediaSourceFactory: ProgressiveMediaSource.Factory
    private var source = ConcatenatingMediaSource()

    private val eventListener = object : Player.EventListener {

        override fun onTracksChanged(
            trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray
        ) {
            currentIndexFlow.value = currentIndex
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
            playbackPositionFLow.value = player.currentPosition

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

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)

            Timber.e(error)
            FirebaseCrashlytics.getInstance().recordException(error)

            verifyByCauseIfNeeded(error)
        }
    }

    private val headsetStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SOURCE_ACTION_WIRED_STATE -> {
                    val state = intent.getIntExtra("state", 1)
                    Timber.d("qgeck wired state: $state")
                    if (state <= 0) onUnplugged()
                }

                SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                    Timber.d("qgeck bl connection state: $state")
                    when (state) {
                        BluetoothHeadset.STATE_CONNECTED -> Unit
                        BluetoothHeadset.STATE_DISCONNECTED -> onUnplugged()
                    }
                }
            }
        }
    }

    private var job = Job()
    private val serviceScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = job + Dispatchers.IO
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

    private var requestedPositionCache = -1

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        onPlayerControlAction(intent)
        onSettingAction(intent)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        job = Job()

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

        player.prepare(source)

        registerReceiver(headsetStateReceiver, IntentFilter().apply {
            addAction(SOURCE_ACTION_WIRED_STATE)
            addAction(SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE)
        })

        dropboxClient = obtainDbxClient()

        restoreState()
    }

    override fun onDestroy() {
        Timber.d("qgeck onDestroy called")

        stop()
        player.stop(true)
        destroyNotification()
        unregisterReceiver(headsetStateReceiver)
        job.cancel()
        player.release()

        onDestroyed?.invoke()

        super.onDestroy()
    }

    private fun onStopServiceRequested() {
        if (player.playWhenReady.not()) stopSelf()
    }

    fun onMediaButtonEvent(event: KeyEvent) {
        mediaSession.controller?.dispatchMediaButtonEvent(event)
    }

    fun setOnEqualizerStateChangedListener(listener: ((Boolean) -> Unit)?) {
        this.onEqualizerStateChanged = listener
    }

    fun setOnDestroyedListener(listener: (() -> Unit)?) {
        this.onDestroyed = listener
    }

    private fun verifyByCauseIfNeeded(throwable: Throwable) {
        if ((throwable as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 410) {
            (queueFlow.value.getOrNull(requestedPositionCache)
                ?: currentDomainTrack)
                ?.let { track ->
                    serviceScope.launch {
                        replace(track.verifyWithDropbox(this@PlayerService, dropboxClient))
                    }
                }
        }
    }

    suspend fun submitQueue(
        queueInfo: QueueInfo,
        force: Boolean = false
    ) {
        loadStateFlow.value = true

        val newQueue = queueInfo.queue.map { it.verifyWithDropbox(this, dropboxClient) }
        when (queueInfo.metadata.actionType) {
            InsertActionType.NEXT -> {
                val position = if (source.size < 1) 0 else currentIndex + 1

                queueFlow.value = queueFlow.value.addedAll(position, newQueue)
                source.addMediaSources(position,
                    newQueue.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.LAST -> {
                val currentQueue = queueFlow.value
                queueFlow.value = currentQueue.addedAll(currentQueue.size, newQueue)
                source.addMediaSources(source.size,
                    newQueue.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.OVERRIDE -> override(newQueue, force)
            InsertActionType.SHUFFLE_NEXT -> {
                val position = if (source.size < 1) 0 else currentIndex + 1
                val shuffled = newQueue.shuffleByClassType(queueInfo.metadata.classType)

                queueFlow.value = queueFlow.value.addedAll(position, shuffled)
                source.addMediaSources(position,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_LAST -> {
                val shuffled = newQueue.shuffleByClassType(queueInfo.metadata.classType)

                val currentQueue = queueFlow.value
                queueFlow.value = currentQueue.addedAll(currentQueue.size, shuffled)
                source.addMediaSources(source.size,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                val shuffled = newQueue.shuffleByClassType(queueInfo.metadata.classType)

                override(shuffled, force)
            }
            InsertActionType.SHUFFLE_SIMPLE_NEXT -> {
                val position = if (source.size < 1) 0 else currentIndex + 1
                val shuffled = newQueue.shuffled()

                queueFlow.value = queueFlow.value.addedAll(position, shuffled)
                source.addMediaSources(position,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                val shuffled = newQueue.shuffled()

                val currentQueue = queueFlow.value
                queueFlow.value = currentQueue.addedAll(currentQueue.size, shuffled)
                source.addMediaSources(source.size,
                    shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                val shuffled = newQueue.shuffled()

                override(shuffled, force)
            }
        }

        loadStateFlow.value = false

        storeState()
    }

    fun swapQueuePosition(from: Int, to: Int) {
        val sourceRange = 0 until source.size
        if (from !in sourceRange || to !in sourceRange) return
        source.moveMediaSource(from, to)
        serviceScope.launch { queueFlow.value = queueFlow.value.swapped(from, to) }
    }

    fun removeQueue(position: Int) {
        source.removeMediaSource(position)
        serviceScope.launch { queueFlow.value = queueFlow.value.removedAt(position) }
    }

    fun removeQueue(trackId: Long) {
        serviceScope.launch {
            removeQueue(queueFlow.value.indexOfFirst { it.id == trackId })
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

    fun play(position: Int) {
        requestedPositionCache = position
        if (currentIndex != position) forceIndex(position)
    }

    private fun resume() {
        Timber.d("qgeck resume invoked")

        notifyPlaybackPositionJob.cancel()
        notifyPlaybackPositionJob = serviceScope.launch(Dispatchers.Main) {
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
    }

    fun clear(keepCurrentIfPlaying: Boolean = false) {
        val before =
            if (keepCurrentIfPlaying &&
                player.playbackState == Player.STATE_READY &&
                player.playWhenReady
            ) currentIndex
            else source.size
        clear(before)
    }

    private fun clear(before: Int) {
        if (before >= source.size) {
            stop()
            destroyNotification()
            source.clear()
            queueFlow.value = emptyList()
            storeState()
            return
        }
        val after = source.size - 1 - before

        repeat(before) {
            source.removeMediaSource(0)
            queueFlow.value = queueFlow.value.removedAt(0)
        }
        repeat(after) {
            source.removeMediaSource(1)
            queueFlow.value = queueFlow.value.removedAt(1)
        }

        storeState()
    }

    fun override(queue: List<DomainTrack>, force: Boolean = false) {
        clear(force.not())
        source.addMediaSources(queue.map { it.getMediaSource(mediaSourceFactory) })
        queueFlow.value = queueFlow.value.addedAll(queue)
    }

    private fun replace(with: DomainTrack) {
        replace(listOf(with))
    }

    private fun replace(with: List<DomainTrack>) {
        if (with.isEmpty()) return

        with.forEach { withTrack ->
            queueFlow.value.mapIndexed { index, track -> index to track }
                .filter { (_, track) -> track.id == withTrack.id }
                .apply { Timber.d("qgeck target: $this") }
                .forEach {
                    val index = it.first

                    removeQueue(index)
                    source.addMediaSource(index, withTrack.getMediaSource(mediaSourceFactory))
                    queueFlow.value = queueFlow.value.addedAll(index, listOf(withTrack))
                }
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
        val track = currentDomainTrack
        if (track != null) {
            seekJob.cancel()
            seekJob = serviceScope.launch {
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

    fun rewind() {
        if (currentDomainTrack != null) {
            seekJob.cancel()
            seekJob = serviceScope.launch(Dispatchers.Main) {
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
        currentDomainTrack?.duration?.apply { seek(this) }
    }

    fun headOrPrev() {
        val current = currentDomainTrack ?: return
        if (currentIndex > 0 && player.contentPosition < current.duration / 100) prev()
        else seekToHead()
    }

    fun seek(ratio: Float) {
        if (ratio !in 0f..1f) return
        val current = currentDomainTrack ?: return
        seek((current.duration * ratio).toLong())
    }

    fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
        playbackPositionFLow.value = playbackPosition
    }

    fun shuffle(actionType: ShuffleActionType = ShuffleActionType.SHUFFLE_SIMPLE) {
        val currentQueue = queueFlow.value
        if (source.size < 1 && source.size != currentQueue.size) return

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

    fun resetQueueOrder() {
        if (source.size < 1) return
        val isCacheValid =
            queueFlow.value.size == cachedQueueOrder.size &&
                    queueFlow.value.map { it.id }.containsAll(cachedQueueOrder)
        if (isCacheValid.not()) return

        reorderQueue(cachedQueueOrder)
    }

    private fun reorderQueue(order: List<Long>) {
        order.forEachIndexed { i, id ->
            val currentIndex = queueFlow.value.indexOfFirst { it.id == id }
            source.moveMediaSource(currentIndex, i)
            queueFlow.value = queueFlow.value.moved(currentIndex, i)
        }
    }

    fun rotateRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
        serviceScope.launch(Dispatchers.Main) { repeatModeFlow.emit(player.repeatMode) }
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
        val queueInfo = QueueInfo(
            QueueMetadata(InsertActionType.OVERRIDE, OrientedClassType.TRACK), queue
        )
        serviceScope.launch(Dispatchers.Main) {
            submitQueue(queueInfo, true)
            forceIndex(currentIndex)
            seek(progress)
            player.repeatMode = repeatMode
        }
    }

    private fun forceIndex(windowIndex: Int) {
        val index = player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        player.seekToDefaultPosition(index + windowIndex)
        seek(0)
        requestedPositionCache = -1
    }

    private fun increasePlaybackCount() = serviceScope.launch(Dispatchers.Main) {
        currentDomainTrack?.let { track ->
            val db = DB.getInstance(this@PlayerService)
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

        serviceScope.launch(Dispatchers.Main) {
            onEqualizerStateChanged?.invoke(equalizer?.enabled == true)
        }
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

    private fun storeState() {
        cachedQueueOrder.apply {
            clear()
            addAll(queueFlow.value.map { it.id })
        }
        val state = PlayerState(
            player.playWhenReady,
            queueFlow.value,
            currentIndex,
            player.currentPosition,
            player.repeatMode
        )
        sharedPreferences.edit().putString(PREF_KEY_PLAYER_STATE, Gson().toJson(state)).apply()
    }

    private fun restoreState() {
        if (player.playWhenReady.not()) {
            sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)?.let {
                Gson().fromJson(it, PlayerState::class.java)
            }?.set()
        }
    }

    fun publishStatus() {
        serviceScope.launch(Dispatchers.Main) {
            currentIndexFlow.emit(currentIndex)
            currentDomainTrack?.apply {
                playbackPositionFLow.emit(player.currentPosition)
            }
            playbackInfoFlow.emit(player.playWhenReady to player.playbackState)
            repeatModeFlow.emit(player.repeatMode)
        }
    }

    private fun onUnplugged() {
        pause()
    }

    private fun showNotification() = serviceScope.launch(Dispatchers.Main) {
        val domainTrack = currentDomainTrack ?: return@launch
        mediaSession.setPlaybackState(
            playbackStateCompat.setState(
                player.playbackState.toPlaybackStateCompat,
                player.currentPosition,
                player.playbackParameters.speed
            ).build()
        )
        mediaSession.setMetadata(domainTrack.getMediaMetadata(this@PlayerService))
        mediaSession.isActive = true
        mediaSession.setSessionActivity(
            PendingIntent.getActivity(
                this@PlayerService,
                App.REQUEST_CODE_LAUNCH_APP,
                LauncherActivity.createIntent(this@PlayerService),
                PendingIntent.FLAG_UPDATE_CURRENT
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