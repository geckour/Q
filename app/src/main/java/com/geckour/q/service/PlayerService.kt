package com.geckour.q.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import com.bumptech.glide.Glide
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainActivity
import com.geckour.q.ui.sheet.BottomSheetViewModel
import com.geckour.q.util.getArtworkUriFromAlbumId
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import timber.log.Timber

class PlayerService : Service() {

    enum class InsertActionType {
        NEXT,
        LAST,
        OVERRIDE,
        SHUFFLE_NEXT,
        SHUFFLE_LAST,
        SHUFFLE_OVERRIDE
    }

    enum class OrientedClassType {
        ARTIST,
        ALBUM,
        SONG,
        GENRE,
        PLAYLIST
    }

    enum class OutputSourceType {
        WIRED,
        BLUETOOTH
    }

    data class QueueMetadata(
            val actionType: InsertActionType,
            val classType: OrientedClassType
    )

    data class InsertQueue(
            val metadata: QueueMetadata,
            val queue: List<Song>
    )

    inner class PlayerBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, PlayerService::class.java)

        private val TAG: String = PlayerService::class.java.simpleName

        private const val ARGS_KEY_CONTROL_COMMAND = "args_key_control_command"

        private const val SOURCE_ACTION_WIRED_STATE = Intent.ACTION_HEADSET_PLUG
        private const val SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE =
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED

        const val NOTIFICATION_CHANNEL_ID_PLAYER = "notification_channel_id_player"
        private const val NOTIFICATION_ID_PLAYER = 320
    }

    private val binder = PlayerBinder()

    private val mediaSession: MediaSession by lazy {
        MediaSession(applicationContext, TAG).apply {
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
            prev()
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

    private val mediaSourceFactory: ExtractorMediaSource.Factory by lazy {
        ExtractorMediaSource.Factory(DefaultDataSourceFactory(applicationContext,
                Util.getUserAgent(applicationContext, packageName)))
                .setExtractorsFactory(DefaultExtractorsFactory())
    }
    private var source = ConcatenatingMediaSource()

    private var notifyPlaybackRatioJob: Job? = null
    private var seekJob: Job? = null

    private val eventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {

        }

        override fun onSeekProcessed() {

        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?,
                                     trackSelections: TrackSelectionArray?) {
            onCurrentPositionChanged?.invoke(currentPosition)
            launch(UI + parentJob) {
                val song = currentSong ?: return@launch
                val albumTitle = async(parentJob) {
                    DB.getInstance(applicationContext).albumDao().get(song.albumId).title
                }.await()
                mediaSession.setMetadata(song.getMediaMetadata(albumTitle).await())
                getNotification(song, albumTitle).await().show()
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

        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
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
                    if (state > 0) {
                        onOutputSourceChange(OutputSourceType.WIRED)
                    } else {
                        onUnplugged()
                    }
                }

                SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    Timber.d("qgeck bl connection state: $state")
                    when (state) {
                        BluetoothHeadset.STATE_CONNECTED -> {
                            onOutputSourceChange(OutputSourceType.BLUETOOTH)
                        }
                        BluetoothHeadset.STATE_DISCONNECTED -> {
                            onUnplugged()
                        }
                    }
                }
            }
        }
    }

    private var parentJob = Job()

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
        player.release()
    }

    private fun onNotificationAction(intent: Intent) {
        if (intent.hasExtra(ARGS_KEY_CONTROL_COMMAND)) {
            val key = intent.extras?.getInt(ARGS_KEY_CONTROL_COMMAND, -1) ?: return
            val command = BottomSheetViewModel.PlaybackButton.values()[key]
            when (command) {
                BottomSheetViewModel.PlaybackButton.PREV -> headOrPrev()
                BottomSheetViewModel.PlaybackButton.PLAY_OR_PAUSE -> togglePlayPause()
                BottomSheetViewModel.PlaybackButton.NEXT -> next()
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

    fun submitQueue(queue: InsertQueue) {
        var needPrepare = this.source.size == 0

        when (queue.metadata.actionType) {
            InsertActionType.NEXT -> {
                this.queue.addAll(currentPosition, queue.queue)
                source.addMediaSources(currentPosition,
                        queue.queue.map { it.getMediaSource() })
            }
            InsertActionType.LAST -> {
                this.queue.addAll(this.queue.size, queue.queue)
                source.addMediaSources(source.size,
                        queue.queue.map { it.getMediaSource() })
            }
            InsertActionType.OVERRIDE -> {
                clear()
                this.queue.addAll(queue.queue)
                source.addMediaSources(queue.queue.map { it.getMediaSource() })
                needPrepare = true
            }
            InsertActionType.SHUFFLE_NEXT -> {
                this.queue.addAll(currentPosition,
                        queue.queue.shuffleByClassType(queue.metadata.classType))
                source.addMediaSources(currentPosition,
                        queue.queue
                                .shuffleByClassType(queue.metadata.classType)
                                .map { it.getMediaSource() })
            }
            InsertActionType.SHUFFLE_LAST -> {
                this.queue.addAll(this.queue.size,
                        queue.queue.shuffleByClassType(queue.metadata.classType))
                source.addMediaSources(source.size,
                        queue.queue
                                .shuffleByClassType(queue.metadata.classType)
                                .map { it.getMediaSource() })
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                clear()
                this.queue.addAll(queue.queue.shuffleByClassType(queue.metadata.classType))
                source.addMediaSources(queue.queue
                        .shuffleByClassType(queue.metadata.classType)
                        .map { it.getMediaSource() })
                needPrepare = true
            }
        }

        onQueueChanged?.invoke(this.queue)
        onCurrentPositionChanged?.invoke(currentPosition)
        if (needPrepare) player.prepare(source)
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
            launch(parentJob) {
                val song = currentSong ?: return@launch
                val albumTitle = DB.getInstance(applicationContext).albumDao().get(song.albumId).title
                getNotification(song, albumTitle).await().show()
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
        launch(parentJob) {
            val song = currentSong ?: return@launch
            val albumTitle = DB.getInstance(applicationContext).albumDao().get(song.albumId).title
            getNotification(song, albumTitle).await().show()
        }
        stopForeground(false)
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

    fun clear() {
        stop()
        this.queue.clear()
        source.clear()
        onCurrentPositionChanged?.invoke(currentPosition)
        stopForeground(true)
    }

    fun next() {
        if (player.currentWindowIndex < source.size - 1) {
            val index = source.size - 1
            player.seekToDefaultPosition(index)
        } else stop()
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
        val startIndex = currentPosition + 1
        if (source.size < 1 || startIndex == source.size) return

        val shuffled = (startIndex until source.size).toList().shuffled()

        (startIndex until source.size).forEach {
            val moveTo = startIndex + shuffled.indexOf(it)
            source.moveMediaSource(it, moveTo)
            val toMove = this.queue[it]
            this.queue.removeAt(it)
            this.queue.add(moveTo, toMove)
        }

        onQueueChanged?.invoke(this.queue)
        onCurrentPositionChanged?.invoke(currentPosition)
    }

    private fun Notification.show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID_PLAYER, this)
        } else {
            getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID_PLAYER, this)
        }
    }

    fun onActivityDestroy() {
        if (player.playWhenReady.not()) {
            stopForeground(true)
            stopSelf()
        }
    }

    fun publishStatus() {
        onQueueChanged?.invoke(queue)
        onCurrentPositionChanged?.invoke(currentPosition)
        onPlaybackStateChanged?.invoke(player.playbackState, player.playWhenReady)
    }

    fun onOutputSourceChange(outputSourceType: OutputSourceType) {
        when (outputSourceType) {
            OutputSourceType.WIRED -> Unit
            OutputSourceType.BLUETOOTH -> Unit
        }
    }

    fun onUnplugged() {
        pause()
    }

    private fun Song.getMediaSource(): MediaSource =
            mediaSourceFactory.createMediaSource(Uri.parse(sourcePath))

    private fun List<Song>.shuffleByClassType(classType: OrientedClassType): List<Song> =
            when (classType) {
                OrientedClassType.ARTIST -> {
                    val artists = this.map { it.artist }.distinct().shuffled()
                    artists.map { artist ->
                        this.filter { it.artist == artist }
                    }.flatten()
                }
                OrientedClassType.ALBUM -> {
                    val albumIds = this.map { it.albumId }.distinct().shuffled()
                    albumIds.map { id ->
                        this.filter { it.albumId == id }
                    }.flatten()
                }
                OrientedClassType.SONG -> {
                    this.shuffled()
                }
                OrientedClassType.GENRE -> {
                    val genreIds = this.map { it.genreId }.distinct().shuffled()
                    genreIds.map { id ->
                        this.filter { it.genreId == id }
                    }.flatten()
                }
                OrientedClassType.PLAYLIST -> {
                    val playlistIds = this.map { it.playlistId }.distinct().shuffled()
                    playlistIds.map { id ->
                        this.filter { it.playlistId == id }
                    }.flatten()
                }
            }

    private fun Song.getMediaMetadata(albumTitle: String? = null): Deferred<MediaMetadata> = async {
        val album = albumTitle
                ?: DB.getInstance(this@PlayerService)
                        .albumDao()
                        .get(this@getMediaMetadata.albumId)
                        .title

        MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                        this@getMediaMetadata.id.toString())
                .putString(MediaMetadata.METADATA_KEY_TITLE, this@getMediaMetadata.name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, this@getMediaMetadata.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                        getArtworkUriFromAlbumId(this@getMediaMetadata.albumId).toString())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, this@getMediaMetadata.duration)
                .build()
    }

    private fun getNotification(song: Song, albumTitle: String): Deferred<Notification> =
            async {
                val artwork = Glide.with(this@PlayerService)
                        .asBitmap()
                        .load(getArtworkUriFromAlbumId(song.albumId))
                        .submit()
                        .get()
                val builder =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID_PLAYER)
                        else Notification.Builder(applicationContext)
                builder.setSmallIcon(R.drawable.ic_notification)
                        .setLargeIcon(artwork)
                        .setContentTitle(song.name)
                        .setContentText(song.artist)
                        .setSubText(albumTitle)
                        .setStyle(Notification.MediaStyle()
                                .setShowActionsInCompactView(0, 1, 2)
                                .setMediaSession(mediaSession.sessionToken))
                        .setContentIntent(PendingIntent.getActivity(applicationContext,
                                App.REQUEST_CODE_OPEN_DEFAULT_ACTIVITY,
                                MainActivity.createIntent(applicationContext),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .setActions(Notification.Action.Builder(
                                Icon.createWithResource(this@PlayerService,
                                        R.drawable.ic_backward),
                                getString(R.string.notification_action_prev),
                                getCommandPendingIntent(
                                        BottomSheetViewModel.PlaybackButton.PREV)).build(),
                                if (player.playWhenReady) {
                                    Notification.Action.Builder(
                                            Icon.createWithResource(this@PlayerService,
                                                    R.drawable.ic_pause),
                                            getString(R.string.notification_action_pause),
                                            getCommandPendingIntent(
                                                    BottomSheetViewModel.PlaybackButton.PLAY_OR_PAUSE)).build()
                                } else {
                                    Notification.Action.Builder(
                                            Icon.createWithResource(this@PlayerService,
                                                    R.drawable.ic_play),
                                            getString(R.string.notification_action_play),
                                            getCommandPendingIntent(
                                                    BottomSheetViewModel.PlaybackButton.PLAY_OR_PAUSE)).build()
                                },
                                Notification.Action.Builder(
                                        Icon.createWithResource(this@PlayerService,
                                                R.drawable.ic_forward),
                                        getString(R.string.notification_action_next),
                                        getCommandPendingIntent(
                                                BottomSheetViewModel.PlaybackButton.NEXT)).build())
                        .build()
            }

    private fun getCommandPendingIntent(command: BottomSheetViewModel.PlaybackButton): PendingIntent =
            PendingIntent.getService(this, 0,
                    createIntent(this).apply {
                        action = command.name
                        putExtra(ARGS_KEY_CONTROL_COMMAND, command.ordinal)
                    },
                    PendingIntent.FLAG_CANCEL_CURRENT)
}