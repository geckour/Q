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
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.service.media.MediaBrowserService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import com.bumptech.glide.Glide
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainActivity
import com.geckour.q.util.MediaRetrieveWorker
import com.geckour.q.util.getArtworkUriFromAlbumId
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import timber.log.Timber

class PlayerService : MediaBrowserService() {

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

        const val BROWSER_ROOT_ID = "com.geckour.q.browser.root"

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
    }
    private val trackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(null))
    private lateinit var player: SimpleExoPlayer
    private val queue: ArrayList<Song> = ArrayList()
    private var currentPosition: Int = 0
    private val currentSong: Song?
        get() = if (currentPosition in queue.indices) queue[currentPosition] else null

    private var onQueueChanged: ((List<Song>) -> Unit)? = null
    private var onCurrentPositionChanged: ((Int) -> Unit)? = null
    private var onPlaybackStateChanged: ((Int, Boolean) -> Unit)? = null
    private var onPlaybackRatioChanged: ((Float) -> Unit)? = null

    private val mediaSourceFactory: ExtractorMediaSource.Factory by lazy {
        ExtractorMediaSource.Factory(DefaultDataSourceFactory(applicationContext,
                Util.getUserAgent(applicationContext, packageName)))
                .setExtractorsFactory(DefaultExtractorsFactory())
    }

    private var notifyPlaybackRatioJob: Job? = null

    private val eventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {

        }

        override fun onSeekProcessed() {

        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?,
                                     trackSelections: TrackSelectionArray?) {

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
            // TODO: Bottom Navigationのボタンに反映する
            when (playbackState) {
                Player.STATE_ENDED -> next()
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

    override fun onLoadChildren(parentId: String,
                                result: Result<MutableList<MediaBrowser.MediaItem>>) {
        val mediaItems: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()

        if (parentId == BROWSER_ROOT_ID) {
            val db = DB.getInstance(this)
            mediaItems.addAll(db.trackDao().getAll().map {
                val artist = db.artistDao().get(it.artistId)?.title ?: MediaRetrieveWorker.UNKNOWN
                val album = db.albumDao().get(it.albumId).title

                MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder()
                        .setMediaId(it.id.toString())
                        .setTitle(it.title)
                        .setSubtitle(artist)
                        .setIconUri(getArtworkUriFromAlbumId(it.albumId))
                        .setDescription(album)
                        .build(),
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
            })
        } else {
            // TODO: 階層的なブラウジングを実装する
        }
    }

    override fun onGetRoot(clientPackageName: String,
                           clientUid: Int, rootHints: Bundle?): BrowserRoot? =
            BrowserRoot(BROWSER_ROOT_ID, null)

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            Service.START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()

        parentJob = Job()

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector).apply {
            addListener(eventListener)
        }

        registerReceiver(headsetStateReceiver, IntentFilter().apply {
            addAction(SOURCE_ACTION_WIRED_STATE)
            addAction(SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE)
        })

        sessionToken = mediaSession.sessionToken
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaSession.isActive = false
        unregisterReceiver(headsetStateReceiver)
        parentJob.cancel()
        player.release()
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
        when (queue.metadata.actionType) {
            InsertActionType.NEXT -> {
                if (this.queue.isEmpty()) {
                    this.queue.addAll(queue.queue)
                } else {
                    this.queue.addAll(currentPosition + 1, queue.queue)
                }
            }
            InsertActionType.LAST -> {
                if (this.queue.isEmpty()) {
                    this.queue.addAll(queue.queue)
                } else {
                    this.queue.addAll(this.queue.size, queue.queue)
                }
            }
            InsertActionType.OVERRIDE -> {
                this.queue.clear()
                this.queue.addAll(queue.queue)
            }
            InsertActionType.SHUFFLE_NEXT -> {
                if (this.queue.isEmpty()) {
                    this.queue.addAll(queue.queue.shuffleByClassType(queue.metadata.classType))
                } else {
                    this.queue.addAll(currentPosition + 1,
                            queue.queue.shuffleByClassType(queue.metadata.classType))
                }
            }
            InsertActionType.SHUFFLE_LAST -> {
                if (this.queue.isEmpty()) {
                    this.queue.addAll(queue.queue.shuffleByClassType(queue.metadata.classType))
                } else {
                    this.queue.addAll(this.queue.size,
                            queue.queue.shuffleByClassType(queue.metadata.classType))
                }
            }
            InsertActionType.SHUFFLE_OVERRIDE -> {
                this.queue.clear()
                this.queue.addAll(queue.queue.shuffleByClassType(queue.metadata.classType))
            }
        }

        onQueueChanged?.invoke(this.queue)
        onCurrentPositionChanged?.invoke(currentPosition)
    }

    fun play(position: Int = currentPosition) {
        Timber.d("qgeck play invoked")
        if (position > queue.lastIndex) return

        this.currentPosition = position
        onCurrentPositionChanged?.invoke(currentPosition)

        val song = currentSong
        val uri = try {
            Uri.parse(song?.sourcePath ?: return)
        } catch (t: Throwable) {
            Timber.e(t)
            return
        }

        seekToHead()
        player.playWhenReady = true
        mediaSession.isActive = true
        val mediaSource = mediaSourceFactory.createMediaSource(uri)
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
        player.prepare(mediaSource)

        notifyPlaybackRatioJob?.cancel()
        notifyPlaybackRatioJob = launch(UI + parentJob) {
            while (true) {
                onPlaybackRatioChanged?.invoke(player.contentPosition.toFloat() / song.duration)
                delay(100)
            }
        }

        launch(parentJob) {
            val albumTitle = DB.getInstance(applicationContext).albumDao().get(song.albumId).title
            mediaSession.setMetadata(song.getMediaMetadata(albumTitle).await())
            getNotification(song, albumTitle).await().show()
        }
    }

    private fun Notification.show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID_PLAYER, this)
        } else {
            getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID_PLAYER, this)
        }
    }

    fun resume() {
        Timber.d("qgeck resume invoked")
        player.playWhenReady = true
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
        seekToHead()
    }

    fun clear() {
        stop()
        this.queue.clear()
    }

    fun next() {
        play(currentPosition + 1)
    }

    fun prev() {
        play(currentPosition - 1)
    }

    fun fastForward() {

    }

    fun rewind() {

    }

    fun seekToHead() {
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
    }

    fun shuffle() {
        if (this.queue.isEmpty()) return
        val toHold = this.queue.subList(0, currentPosition + 1)
        val toShuffle = this.queue.subList(currentPosition + 1, this.queue.size)
        this.queue.clear()
        this.queue.addAll(toHold + toShuffle.shuffled())
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
                                .setMediaSession(mediaSession.sessionToken))
                        .setContentIntent(PendingIntent.getActivity(applicationContext,
                                App.REQUEST_CODE_OPEN_DEFAULT_ACTIVITY,
                                MainActivity.createIntent(applicationContext),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build()
            }
}