package com.geckour.q.service

import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.geckour.q.domain.model.PlaybackButton
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class InstantPlayerService : Service() {

    inner class PlayerBinder : Binder() {
        val service: InstantPlayerService get() = this@InstantPlayerService
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, InstantPlayerService::class.java)

        private const val SOURCE_ACTION_WIRED_STATE = Intent.ACTION_HEADSET_PLUG
        private const val SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE =
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
    }

    private val binder = PlayerBinder()

    private val eventListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            if (playbackState == Player.STATE_ENDED) stop()

            isPlayingListener?.invoke(player.playWhenReady && playbackState == Player.STATE_READY)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            isPlayingListener?.invoke(playWhenReady && player.playbackState == Player.STATE_READY)
        }
    }

    private val player: ExoPlayer by lazy {
        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                addListener(eventListener)
                addAnalyticsListener(EventLogger())
            }
    }

    private lateinit var mediaSourceFactory: ProgressiveMediaSource.Factory
    private var source = ConcatenatingMediaSource()

    private val headsetStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SOURCE_ACTION_WIRED_STATE -> {
                    val state = intent.getIntExtra("state", 1)
                    if (state <= 0) onUnplugged()
                }

                SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE -> {
                    when (intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)) {
                        BluetoothHeadset.STATE_CONNECTED -> Unit
                        BluetoothHeadset.STATE_DISCONNECTED -> onUnplugged()
                    }
                }
            }
        }
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var seekJob: Job = Job()
    private var progressJob: Job = Job()

    internal var isPlayingListener: ((Boolean) -> Unit)? = null
    internal var progressListener: ((Pair<Long, Long>) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY_COMPATIBILITY
    }

    override fun onCreate() {
        super.onCreate()

        mediaSourceFactory = ProgressiveMediaSource.Factory(
            DefaultDataSource.Factory(applicationContext)
        )

        registerReceiver(headsetStateReceiver, IntentFilter().apply {
            addAction(SOURCE_ACTION_WIRED_STATE)
            addAction(SOURCE_ACTION_BLUETOOTH_CONNECTION_STATE)
        })

        progressJob = coroutineScope.launch {
            while (true) {
                withContext(Dispatchers.Main) {
                    progressListener?.invoke(
                        if (player.contentDuration > 0) {
                            player.currentPosition to player.contentDuration
                        } else 0L to 0L
                    )
                }
                delay(100)
            }
        }
    }

    override fun onDestroy() {
        progressJob.cancel()
        stop()
        player.stop()
        player.clearMediaItems()
        unregisterReceiver(headsetStateReceiver)
        player.release()

        super.onDestroy()
    }

    fun submit(path: String) {
        Timber.d("qgeck path: $path")
        source.clear()
        source.addMediaSource(
            mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.fromFile(File(path))))
        )
        player.setMediaSource(source)
        player.prepare()
    }

    fun onPlaybackButtonCommitted(playbackButton: PlaybackButton) {
        when (playbackButton) {
            PlaybackButton.PLAY -> togglePlayPause()
            PlaybackButton.PAUSE -> togglePlayPause()
            PlaybackButton.NEXT -> seekToTail()
            PlaybackButton.PREV -> seekToHead()
            PlaybackButton.FF -> fastForward()
            PlaybackButton.REWIND -> rewind()
            PlaybackButton.UNDEFINED -> stopFastSeek()
        }
    }

    private fun play() {
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

    private fun resume() {
        if (player.playWhenReady.not()) {
            player.playWhenReady = true
        }
    }

    private fun pause() {
        player.playWhenReady = false
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

    private fun togglePlayPause() {
        if (player.playWhenReady) pause()
        else {
            if (player.playbackState == Player.STATE_READY) resume()
            else play()
        }
    }

    fun stop() {
        pause()
    }

    private fun fastForward() {
        seekJob.cancel()
        seekJob = coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                val seekTo = (player.currentPosition + 1000).let {
                    if (it > player.contentDuration) player.contentDuration else it
                }
                seek(seekTo)
                delay(100)
            }
        }
    }

    private fun rewind() {
        seekJob.cancel()
        seekJob = coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                val seekTo = (player.currentPosition - 1000).let {
                    if (it < 0) 0 else it
                }
                seek(seekTo)
                delay(100)
            }
        }
    }

    private fun stopFastSeek() {
        seekJob.cancel()
    }

    private fun seekToHead() {
        seek(0)
    }

    private fun seekToTail() {
        seek(player.contentDuration)
    }

    fun seek(ratio: Float) {
        if (ratio !in 0f..1f) return
        seek((player.contentDuration * ratio).toLong())
    }

    private fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
    }

    private fun onUnplugged() {
        pause()
    }
}