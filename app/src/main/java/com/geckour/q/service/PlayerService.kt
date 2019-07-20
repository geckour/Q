package com.geckour.q.service

import android.app.Notification
import android.app.NotificationManager
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
import android.preference.PreferenceManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.domain.model.Song
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.EqualizerSettings
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.NotificationCommand
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.SettingCommand
import com.geckour.q.util.ducking
import com.geckour.q.util.equalizerEnabled
import com.geckour.q.util.equalizerParams
import com.geckour.q.util.equalizerSettings
import com.geckour.q.util.getMediaMetadata
import com.geckour.q.util.getMediaSource
import com.geckour.q.util.getPlayerNotification
import com.geckour.q.util.move
import com.geckour.q.util.shuffleByClassType
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.AudioListener
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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

        var mediaSession: MediaSessionCompat? = null
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
            val keyEvent: KeyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
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
                        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                            onSkipToNext()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
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
        ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector()).apply {
            addAudioListener(object : AudioListener {
                override fun onAudioSessionId(audioSessionId: Int) {
                    super.onAudioSessionId(audioSessionId)

                    setEqualizer(audioSessionId)
                }
            })
            addListener(eventListener)
            setAudioAttributes(AudioAttributes.Builder().build(), ducking)
        }
    }
    private val currentPosition
        get() = when (player.currentWindowIndex) {
            -1 -> if (source.size > 0) 0 else -1
            else -> player.currentWindowIndex
        }
    private val currentSong: Song?
        get() = when (currentPosition) {
            in queue.indices -> queue[currentPosition]
            else -> null
        }
    private val playing get() = player.playbackState == Player.STATE_READY && player.playWhenReady

    private var equalizer: Equalizer? = null

    private val queue: ArrayList<Song> = ArrayList()
    private var onQueueChanged: ((List<Song>) -> Unit)? = null
    private var onCurrentPositionChanged: ((Int) -> Unit)? = null
    private var onPlaybackStateChanged: ((Int, Boolean) -> Unit)? = null
    private var onPlaybackRatioChanged: ((Float) -> Unit)? = null
    private var onRepeatModeChanged: ((Int) -> Unit)? = null
    private var onEqualizerStateChanged: ((Boolean) -> Unit)? = null
    private var onDestroyed: (() -> Unit)? = null

    private lateinit var mediaSourceFactory: ExtractorMediaSource.Factory
    private var source = ConcatenatingMediaSource()

    private val eventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) = Unit

        override fun onSeekProcessed() = Unit

        override fun onTracksChanged(
                trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?
        ) {
            serviceScope.launch(Dispatchers.Main) {
                onCurrentPositionChanged?.invoke(currentPosition)
            }
            notificationUpdateJob.cancel()
            notificationUpdateJob = showNotification()
            playbackCountIncreaseJob = increasePlaybackCount()
        }

        override fun onPlayerError(error: ExoPlaybackException?) = Unit

        override fun onLoadingChanged(isLoading: Boolean) = Unit

        override fun onPositionDiscontinuity(reason: Int) = Unit

        override fun onRepeatModeChanged(repeatMode: Int) = Unit

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            serviceScope.launch(Dispatchers.Main) {
                onQueueChanged?.invoke(queue)
                onCurrentPositionChanged?.invoke(currentPosition)
            }
            if (source.size < 1) destroyNotification()
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            val sessionPlaybackState = when (playbackState) {
                Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
                Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
                Player.STATE_IDLE -> PlaybackState.STATE_NONE
                Player.STATE_READY -> {
                    if (playWhenReady) PlaybackState.STATE_PLAYING
                    else PlaybackState.STATE_PAUSED
                }
                else -> PlaybackState.STATE_ERROR
            }
            mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
                    .setState(sessionPlaybackState, player.currentPosition, 1f)
                    .build())

            if (currentPosition == source.size - 1
                    && playbackState == Player.STATE_ENDED
                    && player.repeatMode == Player.REPEAT_MODE_OFF) {
                stop()
            }

            if (playbackState == Player.STATE_READY) {
                notificationUpdateJob.cancel()
                notificationUpdateJob = showNotification()
            }

            serviceScope.launch(Dispatchers.Main) {
                onPlaybackStateChanged?.invoke(playbackState, playWhenReady)
                onCurrentPositionChanged?.invoke(currentPosition)
            }
        }
    }

    private val headsetStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SOURCE_ACTION_WIRED_STATE -> {
                    val state = intent.getIntExtra("state", 1)
                    Timber.d("qgeck wired state: $state")
                    if (state <= 0)
                        onUnplugged()
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
        override val coroutineContext: CoroutineContext get() = job
    }
    private var notificationUpdateJob: Job = Job()
    private var notifyPlaybackRatioJob: Job = Job()
    private var playbackCountIncreaseJob: Job = Job()
    private var seekJob: Job = Job()

    private var ducking: Boolean = false
    fun getDuking(): Boolean = ducking

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        onNotificationAction(intent)
        onSettingAction(intent)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        job = Job()

        mediaSession = MediaSessionCompat(this, TAG).apply {
            setPlaybackState(PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_FAST_FORWARD or
                            PlaybackStateCompat.ACTION_REWIND or
                            PlaybackStateCompat.ACTION_SEEK_TO)
                    .build())
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback)
            isActive = false
        }

        mediaSourceFactory = ExtractorMediaSource.Factory(DefaultDataSourceFactory(applicationContext,
                Util.getUserAgent(applicationContext, packageName)))
                .setExtractorsFactory(DefaultExtractorsFactory())

        registerReceiver(headsetStateReceiver, IntentFilter().apply {
            addAction(SOURCE_ACTION_WIRED_STATE)
            addAction(SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE)
        })

        restoreState()
    }

    override fun onDestroy() {
        Timber.d("qgeck onDestroy called")

        stop()
        player.stop(true)
        mediaSession = null
        equalizer = null
        destroyNotification()
        unregisterReceiver(headsetStateReceiver)
        job.cancel()
        player.release()

        onDestroyed?.invoke()

        super.onDestroy()
    }

    private fun onNotificationAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_CONTROL_COMMAND)) {
            val key = intent.extras?.getInt(ARGS_KEY_CONTROL_COMMAND, -1) ?: return
            when (NotificationCommand.values()[key]) {
                NotificationCommand.PLAY_PAUSE ->
                    sendMediaButtonDownEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                NotificationCommand.NEXT ->
                    sendMediaButtonDownEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                NotificationCommand.PREV ->
                    sendMediaButtonDownEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                NotificationCommand.DESTROY -> onRequestedStopService()
            }
        }
    }

    private fun sendMediaButtonDownEvent(keyCode: Int) {
        mediaSession?.controller?.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    private fun sendMediaButtonUpEvent(keyCode: Int) {
        mediaSession?.controller?.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun onSettingAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_SETTING_COMMAND)) {
            val key = intent.extras?.getInt(ARGS_KEY_SETTING_COMMAND, -1) ?: return
            when (SettingCommand.values()[key]) {
                SettingCommand.SET_EQUALIZER ->
                    player.audioSessionId.apply { setEqualizer(if (this != 0) this else null) }
                SettingCommand.UNSET_EQUALIZER -> setEqualizer(null)
                SettingCommand.REFLECT_EQUALIZER_SETTING -> reflectEqualizerSettings()
            }
        }
    }

    fun setOnQueueChangedListener(listener: ((List<Song>) -> Unit)?) {
        this.onQueueChanged = listener
    }

    fun setOnCurrentPositionChangedListener(listener: ((Int) -> Unit)?) {
        this.onCurrentPositionChanged = listener
    }

    fun setOnPlaybackStateChangeListener(
            listener: ((playbackState: Int, playWhenReady: Boolean) -> Unit)?) {
        this.onPlaybackStateChanged = listener
    }

    fun setOnPlaybackRatioChangedListener(listener: ((Float) -> Unit)?) {
        this.onPlaybackRatioChanged = listener
    }

    fun setOnRepeatModeChangedListener(listener: ((Int) -> Unit)?) {
        this.onRepeatModeChanged = listener
    }

    fun setOnEqualizerStateChangedListener(listener: ((Boolean) -> Unit)?) {
        this.onEqualizerStateChanged = listener
    }

    fun setOnDestroyedListener(listener: (() -> Unit)?) {
        this.onDestroyed = listener
    }

    private fun PlayerState.set() {
        val insertQueue =
                QueueInfo(QueueMetadata(InsertActionType.OVERRIDE, OrientedClassType.SONG),
                        queue)
        submitQueue(insertQueue, true)
        forcePosition(currentPosition)
        seek(progress)
        player.repeatMode = repeatMode
    }

    fun submitQueue(queueInfo: QueueInfo, force: Boolean = false) {
        val needPrepare = source.size == 0
        when (queueInfo.metadata.actionType) {
            InsertActionType.NEXT -> {
                val position = if (source.size < 1) 0 else currentPosition + 1

                this.queue.addAll(position, queueInfo.queue)
                source.addMediaSources(position,
                        queueInfo.queue.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.LAST -> {
                this.queue.addAll(this.queue.size, queueInfo.queue)
                source.addMediaSources(source.size,
                        queueInfo.queue.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.OVERRIDE -> override(queueInfo.queue, force)
            InsertActionType.SHUFFLE_NEXT -> {
                val position = if (source.size < 1) 0 else currentPosition + 1
                val shuffled = queueInfo.queue.shuffleByClassType(queueInfo.metadata.classType)

                this.queue.addAll(position, shuffled)
                source.addMediaSources(position,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_LAST -> {
                val shuffled = queueInfo.queue.shuffleByClassType(queueInfo.metadata.classType)

                this.queue.addAll(this.queue.size, shuffled)
                source.addMediaSources(source.size,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                val shuffled = queueInfo.queue.shuffleByClassType(queueInfo.metadata.classType)

                override(shuffled, force)
            }
            InsertActionType.SHUFFLE_SIMPLE_NEXT -> {
                val position = if (source.size < 1) 0 else currentPosition + 1
                val shuffled = queueInfo.queue.shuffled()

                this.queue.addAll(position, shuffled)
                source.addMediaSources(position,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                val shuffled = queueInfo.queue.shuffled()

                this.queue.addAll(this.queue.size, shuffled)
                source.addMediaSources(source.size,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                val shuffled = queueInfo.queue.shuffled()

                override(shuffled, force)
            }
        }

        storeState()
        if (needPrepare) player.prepare(source)
    }

    fun swapQueuePosition(from: Int, to: Int) {
        val sourceRange = 0 until source.size
        if (from !in sourceRange || to !in sourceRange) return
        source.moveMediaSource(from, to)
        this.queue.move(from, to)
    }

    fun removeQueue(position: Int) {
        source.removeMediaSource(position)
        this.queue.removeAt(position)
    }

    fun removeQueue(trackId: Long) {
        this.queue.filter { it.id == trackId }
                .forEach {
                    val index = this.queue.indexOf(it)
                    removeQueue(index)
                }
    }

    private fun forcePosition(position: Int) {
        player.seekToDefaultPosition(position)
    }

    private fun increasePlaybackCount() = serviceScope.launch {
        currentSong?.also { song ->
            val db = DB.getInstance(this@PlayerService)
            db.trackDao().increasePlaybackCount(song.id)
            db.albumDao().increasePlaybackCount(song.albumId)
            db.artistDao().apply {
                val artist = findArtist(song.artist).firstOrNull()
                        ?: db.albumDao().get(song.albumId)?.artistId?.let {
                            get(it)
                        }
                artist?.apply { increasePlaybackCount(id) }
            }
        }
    }

    private fun play() {
        Timber.d("qgeck play invoked")
        getSystemService(AudioManager::class.java).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioFocusRequest = AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
                requestAudioFocus(audioFocusRequest)
            } else {
                requestAudioFocus({}, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }

        resume()
    }

    fun play(position: Int) {
        if (currentPosition != position) forcePosition(position)
    }

    private fun resume() {
        Timber.d("qgeck resume invoked")

        notifyPlaybackRatioJob.cancel()
        notifyPlaybackRatioJob = serviceScope.launch {
            while (true) {
                val song = currentSong ?: break
                withContext(Dispatchers.Main) {
                    onPlaybackRatioChanged?.invoke(player.contentPosition.toFloat() / song.duration)
                }
                delay(100)
            }
        }

        if (player.playWhenReady.not()) {
            player.playWhenReady = true
            mediaSession?.isActive = true
        }
    }

    fun pause() {
        Timber.d("qgeck pause invoked")

        if (player.playWhenReady) storeState()

        player.playWhenReady = false
        notifyPlaybackRatioJob.cancel()
        getSystemService(AudioManager::class.java).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioFocusRequest = AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
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
        seekToHead()
        mediaSession?.isActive = false
    }

    fun clear(keepCurrentIfPlaying: Boolean = false) {
        val before = if (keepCurrentIfPlaying
                && player.playbackState == Player.STATE_READY
                && player.playWhenReady) {
            currentPosition
        } else source.size
        clear(before)
    }

    private fun clear(before: Int) {
        if (before >= source.size) {
            stop()
            destroyNotification()
            source.clear()
            this.queue.clear()
            storeState()
            return
        }
        val after = source.size - 1 - before

        repeat(before) {
            source.removeMediaSource(0)
            this.queue.removeAt(0)
        }
        repeat(after) {
            source.removeMediaSource(1)
            this.queue.removeAt(1)
        }

        storeState()
    }

    fun override(queue: List<Song>, force: Boolean = false) {
        clear(force.not())
        source.addMediaSources(queue.map { it.getMediaSource(mediaSourceFactory) })
        if (this.queue.isEmpty()) player.prepare(source)
        this.queue.addAll(queue)
    }

    fun next() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) seekToTail()
        else {
            if (player.currentWindowIndex < source.size - 1) {
                val index = player.currentWindowIndex + 1
                player.seekToDefaultPosition(index)
            } else stop()
        }
    }

    private fun prev() {
        val index =
                if (player.currentWindowIndex > 0)
                    player.currentWindowIndex - 1
                else 0
        player.seekToDefaultPosition(index)
    }

    fun fastForward() {
        val song = currentSong
        if (song != null) {
            seekJob.cancel()
            seekJob = serviceScope.launch {
                while (true) {
                    val seekTo = (player.currentPosition + 1000).let {
                        if (it > song.duration) song.duration else it
                    }
                    seek(seekTo)
                    delay(100)
                }
            }
        }
    }

    fun rewind() {
        if (currentSong != null) {
            seekJob.cancel()
            seekJob = serviceScope.launch {
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
        currentSong?.duration?.apply { seek(this) }
    }

    fun headOrPrev() {
        val current = currentSong ?: return
        if (currentPosition > 0 && player.contentPosition < current.duration / 100) prev()
        else seekToHead()
    }

    fun seek(ratio: Float) {
        if (ratio !in 0f..1f) return
        val current = currentSong ?: return
        seek((current.duration * ratio).toLong())
    }

    fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
        val current = currentSong ?: return
        serviceScope.launch(Dispatchers.Main) {
            onPlaybackRatioChanged?.invoke(playbackPosition.toFloat() / current.duration)
        }
    }

    fun shuffle() {
        if (source.size < 1) return

        val shuffled = (0 until source.size).toList().shuffled()

        (0 until source.size).forEach {
            val moveTo = shuffled.indexOf(it)
            source.moveMediaSource(it, moveTo)
            this.queue.move(it, moveTo)
        }
    }

    fun rotateRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
        serviceScope.launch(Dispatchers.Main) {
            onRepeatModeChanged?.invoke(player.repeatMode)
        }
    }

    private fun setEqualizer(audioSessionId: Int?) {
        if (audioSessionId != null) {
            if (equalizer == null) {
                try {
                    equalizer = Equalizer(0, audioSessionId).apply {
                        val params = EqualizerParams(
                                bandLevelRange.let { it.first().toInt() to it.last().toInt() },
                                (0 until numberOfBands).map {
                                    val short = it.toShort()
                                    EqualizerParams.Band(
                                            getBandFreqRange(short).let { it.first() to it.last() },
                                            getCenterFreq(short)
                                    )
                                }
                        )
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
            onEqualizerStateChanged?.invoke(equalizer?.enabled ?: false)
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

    fun onRequestedStopService() {
        if (player.playWhenReady.not()) {
            stopSelf()
        }
    }

    private fun storeState() {
        val state = PlayerState(
                player.playWhenReady,
                queue,
                currentPosition,
                player.currentPosition,
                player.repeatMode
        )
        sharedPreferences.edit()
                .putString(PREF_KEY_PLAYER_STATE, Gson().toJson(state))
                .apply()
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
            onQueueChanged?.invoke(queue)
            onCurrentPositionChanged?.invoke(currentPosition)
            currentSong?.apply {
                onPlaybackRatioChanged?.invoke(player.currentPosition.toFloat() / this.duration)
            }
            onPlaybackStateChanged?.invoke(player.playbackState, player.playWhenReady)
            onRepeatModeChanged?.invoke(player.repeatMode)
        }
    }

    private fun onUnplugged() {
        pause()
    }

    private fun showNotification() = serviceScope.launch {
        val song = currentSong ?: return@launch

        mediaSession?.setMetadata(song.getMediaMetadata(this@PlayerService))
        getPlayerNotification(this@PlayerService, mediaSession?.sessionToken, song, playing)
                ?.show()
    }

    private fun Notification.show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playing) {
            Timber.d("qgeck starting player service as foreground")
            startForeground(NOTIFICATION_ID_PLAYER, this)
        } else {
            Timber.d("qgeck starting player service as NOT foreground")
            stopForeground(false)
            getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID_PLAYER, this)
        }
    }

    private fun destroyNotification() {
        notificationUpdateJob.cancel()
        stopForeground(true)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_PLAYER)
    }
}