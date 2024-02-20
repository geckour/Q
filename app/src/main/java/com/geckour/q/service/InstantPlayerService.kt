package com.geckour.q.service

import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.geckour.q.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class InstantPlayerService : MediaSessionService() {

    companion object {

        const val ACTION_COMMAND_FAST_FORWARD = "action_command_fast_forward"
        const val ACTION_COMMAND_REWIND = "action_command_rewind"
        const val ACTION_COMMAND_STOP_FAST_SEEK = "action_command_stop_fast_seek"
    }

    private val eventListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            if (playbackState == Player.STATE_ENDED) stop()
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_COMMAND_FAST_FORWARD, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_REWIND, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_STOP_FAST_SEEK, Bundle.EMPTY))
                        .build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.commandCode == SessionCommand.COMMAND_CODE_CUSTOM) {
                return when (customCommand.customAction) {
                    ACTION_COMMAND_FAST_FORWARD -> {
                        fastForward()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_REWIND -> {
                        rewind()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_STOP_FAST_SEEK -> {
                        stopFastSeek()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    else -> super.onCustomCommand(session, controller, customCommand, args)
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: Player

    private lateinit var mediaSession: MediaSession

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var seekJob: Job = Job()
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onCreate() {
        super.onCreate()

        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(eventListener)
                addAnalyticsListener(EventLogger())
            }
        forwardingPlayer = object : ForwardingPlayer(player) {

            override fun addMediaItem(mediaItem: MediaItem) {
                player.clearMediaItems()
                super.addMediaItem(mediaItem)
            }

            override fun play() {
                togglePlayPause()
            }

            override fun pause() {
                togglePlayPause()
            }

            override fun stop() {
                purge()
                stopSelf()
            }

            override fun seekToNext() {
                seekToTail()
            }

            override fun seekToPrevious() {
                seekToHead()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setId(InstantPlayerService::class.java.name)
            .setCallback(mediaSessionCallback)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_player) }
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        purge()
        stopSelf()

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        purge()

        super.onDestroy()
    }

    private fun purge() {
        stop()
        player.stop()
        player.clearMediaItems()
        player.release()
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

    private fun seek(playbackPosition: Long) {
        player.seekTo(playbackPosition)
    }
}