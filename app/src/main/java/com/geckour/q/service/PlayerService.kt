package com.geckour.q.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Song
import com.geckour.q.util.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import timber.log.Timber

class PlayerService : Service() {

    inner class PlayerBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, PlayerService::class.java)

        private val TAG: String = PlayerService::class.java.simpleName

        const val NOTIFICATION_CHANNEL_ID_PLAYER = "notification_channel_id_player"
        private const val NOTIFICATION_ID_PLAYER = 320

        const val ARGS_KEY_CONTROL_COMMAND = "args_key_control_command"

        private const val SOURCE_ACTION_WIRED_STATE = Intent.ACTION_HEADSET_PLUG
        private const val SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE =
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
    }

    private val binder = PlayerBinder()

    private val mediaSession: MediaSession by lazy {
        MediaSession(this, TAG).apply {
            setPlaybackState(PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_FAST_FORWARD or
                            PlaybackState.ACTION_REWIND or
                            PlaybackState.ACTION_SEEK_TO)
                    .build())
            setCallback(mediaSessionCallback)
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback() {
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

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val keyCode = (mediaButtonIntent.extras?.get(Intent.EXTRA_KEY_EVENT) as? KeyEvent)?.keyCode
            return when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    pause()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    togglePlayPause()
                    true
                }
                else -> super.onMediaButtonEvent(mediaButtonIntent)
            }
        }
    }

    private lateinit var player: SimpleExoPlayer
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

    private val queue: ArrayList<Song> = ArrayList()
    private var onQueueChanged: ((List<Song>) -> Unit)? = null
    private var onCurrentPositionChanged: ((Int) -> Unit)? = null
    private var onPlaybackStateChanged: ((Int, Boolean) -> Unit)? = null
    private var onPlaybackRatioChanged: ((Float) -> Unit)? = null
    private var onRepeatModeChanged: ((Int) -> Unit)? = null
    private var onDestroyed: (() -> Unit)? = null

    private val mediaSourceFactory: ExtractorMediaSource.Factory by lazy {
        ExtractorMediaSource.Factory(DefaultDataSourceFactory(applicationContext,
                Util.getUserAgent(applicationContext, packageName)))
                .setExtractorsFactory(DefaultExtractorsFactory())
    }
    private val source = ConcatenatingMediaSource()

    private val eventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {

        }

        override fun onSeekProcessed() {

        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?,
                                     trackSelections: TrackSelectionArray?) {
            onCurrentPositionChanged?.invoke(currentPosition)

            notificationUpdateJob.cancel()
            notificationUpdateJob = launch(UI + parentJob) {
                val song = currentSong ?: return@launch
                val albumTitle = async(parentJob) {
                    DB.getInstance(applicationContext).albumDao().get(song.albumId)?.title
                            ?: UNKNOWN
                }.await()
                mediaSession.setMetadata(
                        song.getMediaMetadata(this@PlayerService, albumTitle).await())
                getNotification(this@PlayerService, mediaSession.sessionToken,
                        song, albumTitle, player.playWhenReady).await()
                        .show(player.playWhenReady)
            }
        }

        override fun onPlayerError(error: ExoPlaybackException?) {

        }

        override fun onLoadingChanged(isLoading: Boolean) {

        }

        override fun onPositionDiscontinuity(reason: Int) {

        }

        override fun onRepeatModeChanged(repeatMode: Int) {

        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {

        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            onQueueChanged?.invoke(this@PlayerService.queue)
            onCurrentPositionChanged?.invoke(currentPosition)
            if (source.size < 1) destroyNotification()
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playbackState == Player.STATE_ENDED
                    && player.repeatMode == Player.REPEAT_MODE_OFF) {
                pause()
                seekToHead()
            }
            onPlaybackStateChanged?.invoke(playbackState, playWhenReady)
        }
    }

    private val headsetStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SOURCE_ACTION_WIRED_STATE -> {
                    val state = intent.getIntExtra("state", -1)
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

    private var parentJob = Job()
    private var notificationUpdateJob = Job()
    private var notifyPlaybackRatioJob: Job? = null
    private var seekJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mediaSessionCallback.onMediaButtonEvent(intent)
        onNotificationAction(intent)
        return Service.START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        parentJob = Job()

        player = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector()).apply {
            addListener(eventListener)
        }

        registerReceiver(headsetStateReceiver, IntentFilter().apply {
            addAction(SOURCE_ACTION_WIRED_STATE)
            addAction(SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE)
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(headsetStateReceiver)
        parentJob.cancel()
        player.stop(true)
        player.release()
        onDestroyed?.invoke()
    }

    private fun onNotificationAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_CONTROL_COMMAND)) {
            val key = intent.extras?.getInt(ARGS_KEY_CONTROL_COMMAND, -1) ?: return
            val command = NotificationCommand.values()[key]
            when (command) {
                NotificationCommand.PREV -> headOrPrev()
                NotificationCommand.PLAY_OR_PAUSE -> togglePlayPause()
                NotificationCommand.NEXT -> next()
                NotificationCommand.DESTROY -> onRequestedStopService()
                else -> Unit
            }
        }
    }

    fun setOnQueueChangedListener(listener: (List<Song>) -> Unit) {
        this.onQueueChanged = listener
    }

    fun setOnCurrentPositionChangedListener(listener: (Int) -> Unit) {
        this.onCurrentPositionChanged = listener
    }

    fun setOnPlaybackStateChangeListener(
            listener: (playbackState: Int, playWhenReady: Boolean) -> Unit) {
        this.onPlaybackStateChanged = listener
    }

    fun setOnPlaybackRatioChangedListener(listener: (Float) -> Unit) {
        this.onPlaybackRatioChanged = listener
    }

    fun setOnRepeatModeChangedListener(listener: (Int) -> Unit) {
        this.onRepeatModeChanged = listener
    }

    fun setOnDestroyedListener(listener: () -> Unit) {
        this.onDestroyed = listener
    }

    fun submitQueue(queue: InsertQueue) {
        var needPrepare = this.source.size == 0

        when (queue.metadata.actionType) {
            InsertActionType.NEXT -> {
                val position = if (currentPosition < 1) 0 else currentPosition + 1

                this.queue.addAll(position, queue.queue)
                source.addMediaSources(position,
                        queue.queue.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.LAST -> {
                this.queue.addAll(this.queue.size, queue.queue)
                source.addMediaSources(source.size,
                        queue.queue.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.OVERRIDE -> {
                clear()
                this.queue.addAll(queue.queue)
                source.addMediaSources(queue.queue.map { it.getMediaSource(mediaSourceFactory) })
                needPrepare = true
            }
            InsertActionType.SHUFFLE_NEXT -> {
                val position = if (currentPosition < 1) 0 else currentPosition + 1
                val shuffled = queue.queue.shuffleByClassType(queue.metadata.classType)

                this.queue.addAll(position, shuffled)
                source.addMediaSources(position,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_LAST -> {
                val shuffled = queue.queue.shuffleByClassType(queue.metadata.classType)

                this.queue.addAll(this.queue.size, shuffled)
                source.addMediaSources(source.size,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                clear()

                val shuffled = queue.queue.shuffleByClassType(queue.metadata.classType)

                this.queue.addAll(shuffled)
                source.addMediaSources(shuffled.map { it.getMediaSource(mediaSourceFactory) })
                needPrepare = true
            }
            InsertActionType.SHUFFLE_SIMPLE_NEXT -> {
                val position = if (currentPosition < 1) 0 else currentPosition + 1
                val shuffled = queue.queue.shuffled()

                this.queue.addAll(position, shuffled)
                source.addMediaSources(position,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                val shuffled = queue.queue.shuffled()

                this.queue.addAll(this.queue.size, shuffled)
                source.addMediaSources(source.size,
                        shuffled.map { it.getMediaSource(mediaSourceFactory) })
            }
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                clear()

                val shuffled = queue.queue.shuffled()

                this.queue.addAll(shuffled)
                source.addMediaSources(shuffled.map { it.getMediaSource(mediaSourceFactory) })
                needPrepare = true
            }
        }

        onRepeatModeChanged?.invoke(player.repeatMode)
        if (needPrepare) player.prepare(source)
    }

    fun swapQueuePosition(from: Int, to: Int) {
        if (from !in 0 until source.size || to !in 0 until source.size) return
        source.moveMediaSource(from, to)
        this.queue.move(from, to)
    }

    fun removeQueue(position: Int) {
        source.removeMediaSource(position)
        this.queue.removeAt(position)
    }

    fun removeQueue(trackId: Long) {
        this.queue.mapIndexed { i, song -> i to song }
                .filter { it.second.id == trackId }
                .forEach { removeQueue(it.first) }
    }

    fun forcePosition(position: Int) {
        player.seekToDefaultPosition(position)
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
        notifyPlaybackRatioJob?.cancel()
        notifyPlaybackRatioJob = launch(UI + parentJob) {
            while (true) {
                val song = currentSong ?: break
                onPlaybackRatioChanged?.invoke(player.contentPosition.toFloat() / song.duration)
                delay(100)
            }
        }

        if (player.playWhenReady.not()) {
            mediaSession.isActive = true
            notificationUpdateJob.cancel()
            notificationUpdateJob = launch(parentJob) {
                val song = currentSong ?: return@launch
                val albumTitle =
                        DB.getInstance(applicationContext).albumDao().get(song.albumId)?.title
                                ?: UNKNOWN
                getNotification(this@PlayerService, mediaSession.sessionToken,
                        song, albumTitle, player.playWhenReady).await()
                        .show(player.playWhenReady)
            }
            player.playWhenReady = true
        }
    }

    fun pause() {
        Timber.d("qgeck pause invoked")
        player.playWhenReady = false
        notifyPlaybackRatioJob?.cancel()
        getSystemService(AudioManager::class.java).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioFocusRequest = AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
                abandonAudioFocusRequest(audioFocusRequest)
            } else {
                abandonAudioFocus {}
            }
        }
        notificationUpdateJob.cancel()
        notificationUpdateJob = launch(parentJob) {
            val song = currentSong ?: return@launch
            val albumTitle = DB.getInstance(applicationContext).albumDao().get(song.albumId)?.title
                    ?: UNKNOWN
            getNotification(this@PlayerService, mediaSession.sessionToken,
                    song, albumTitle, player.playWhenReady).await()
                    .show(player.playWhenReady)
            stopForeground(false)
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
        mediaSession.isActive = false
        seekToHead()
    }

    fun clear(keepCurrentIfPlaying: Boolean = false) {
        if (keepCurrentIfPlaying
                && player.playbackState == Player.STATE_READY
                && player.playWhenReady) {
            val current = currentPosition
            val remain = source.size - current - 1
            (0 until current).forEach {
                source.removeMediaSource(0)
                this.queue.removeAt(0)
            }
            (0 until remain).forEach {
                source.removeMediaSource(1)
                this.queue.removeAt(1)
            }
        } else {
            stop()
            notificationUpdateJob.cancel()
            this.queue.clear()
            source.clear()
        }
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
            seekJob?.cancel()
            seekJob = launch(UI + parentJob) {
                while (true) {
                    val seekTo = (player.currentPosition + 3000).let {
                        if (it > song.duration) song.duration else it
                    }
                    seek(seekTo)
                    delay(250)
                }
            }
        }
    }

    fun rewind() {
        if (currentSong != null) {
            seekJob?.cancel()
            seekJob = launch(UI + parentJob) {
                while (true) {
                    val seekTo = (player.currentPosition - 3000).let {
                        if (it < 0) 0 else it
                    }
                    seek(seekTo)
                    delay(250)
                }
            }
        }
    }

    fun stopRunningButtonAction() {
        seekJob?.cancel()
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
        if (ratio !in 0..1) return
        val current = currentSong ?: return
        seek((current.duration * ratio).toLong())
    }

    fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
        val current = currentSong ?: return
        onPlaybackRatioChanged?.invoke(playbackPosition.toFloat() / current.duration)
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
        onRepeatModeChanged?.invoke(player.repeatMode)
    }

    fun onRequestedStopService() {
        if (player.playWhenReady.not()) {
            clear()
            stopSelf()
        }
    }

    fun publishStatus() {
        onQueueChanged?.invoke(queue)
        onCurrentPositionChanged?.invoke(currentPosition)
        currentSong?.apply {
            onPlaybackRatioChanged?.invoke(player.currentPosition.toFloat() / this.duration)
        }
        onPlaybackStateChanged?.invoke(player.playbackState, player.playWhenReady)
        onRepeatModeChanged?.invoke(player.repeatMode)
    }

    private fun onUnplugged() {
        pause()
    }

    private fun Notification.show(playWhenReady: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playWhenReady) {
            startForeground(NOTIFICATION_ID_PLAYER, this)
        } else {
            getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID_PLAYER, this)
        }
    }

    private fun destroyNotification() {
        stopForeground(true)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_PLAYER)
    }
}