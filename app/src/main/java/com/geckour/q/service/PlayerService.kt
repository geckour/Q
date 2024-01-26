package com.geckour.q.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerNotificationManager
import com.geckour.q.App
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.currentSourcePaths
import com.geckour.q.util.getMediaItem
import com.geckour.q.util.toDomainTrack
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import timber.log.Timber

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService(), LifecycleOwner {

    companion object {

        private const val NOTIFICATION_ID_PLAYER = 320

        const val ACTION_COMMAND_STORE_STATE = "action_command_store_state"
        const val ACTION_COMMAND_RESTORE_STATE = "action_command_restore_state"
        const val ACTION_COMMAND_STOP_FAST_SEEK = "action_command_stop_fast_seek"
        private const val ACTION_COMMAND_TOGGLE_FAVORITE = "action_command_toggle_favorite"

        const val PREF_KEY_PLAYER_STATE = "pref_key_player_state"

        fun createIntent(context: Context): Intent = Intent(context, PlayerService::class.java)
    }

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult =
            ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_COMMAND_STORE_STATE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_RESTORE_STATE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_STOP_FAST_SEEK, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
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
                    ACTION_COMMAND_STORE_STATE -> {
                        storeState()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_RESTORE_STATE -> {
                        restoreState()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_STOP_FAST_SEEK -> {
                        stopFastSeek()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_TOGGLE_FAVORITE -> {
                        player.currentSourcePaths.getOrNull(currentIndex)?.let {
                            lifecycleScope.launch {
                                val trackDao = DB.getInstance(this@PlayerService).trackDao()
                                val track = trackDao.getBySourcePath(it)?.track ?: return@launch
                                trackDao.insert(track.copy(isFavorite = track.isFavorite.not()))
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                            ?: Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
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
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private val currentIndex
        get() =
            if (player.currentMediaItemIndex == -1) 0
            else player.currentMediaItemIndex

    private lateinit var db: DB

    private var seekJob: Job = Job()

    private val sharedPreferences by inject<SharedPreferences>()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()

        super.onCreate()

        Timber.d("qgeck create PlayerService")

        db = DB.getInstance(this@PlayerService)
        val trackSelector = DefaultTrackSelector(this)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        super.onTracksChanged(tracks)

                        storeState()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        super.onTimelineChanged(timeline, reason)

                        storeState()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)

                        storeState()
                    }

                    override fun onPlayWhenReadyChanged(
                        playWhenReady: Boolean,
                        reason: Int
                    ) {
                        super.onPlayWhenReadyChanged(playWhenReady, reason)

                        storeState()
                    }
                })
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
            }
        forwardingPlayer = object : ForwardingPlayer(player) {

            override fun play() {
                Timber.d("qgeck play forwarded")
                this@PlayerService.play()
            }

            override fun pause() {
                Timber.d("qgeck pause forwarded")
                this@PlayerService.pause()
            }

            override fun stop() {
                Timber.d("qgeck stop forwarded")
                this@PlayerService.stop()
            }

            override fun seekToNext() {
                Timber.d("qgeck seekToNext forwarded")
                this@PlayerService.next()
            }

            override fun seekToPrevious() {
                Timber.d("qgeck seekToPrevious forwarded")
                this@PlayerService.headOrPrev()
            }

            override fun seekForward() {
                Timber.d("qgeck seekForward forwarded")
                this@PlayerService.fastForward()
            }

            override fun seekBack() {
                Timber.d("qgeck seekBack forwarded")
                this@PlayerService.rewind()
            }

            override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
                lifecycleScope.launch {
                    val trackDao = db.trackDao()
                    val fullMediaItems = mediaItems.mapNotNull {
                        trackDao.getBySourcePath(it.mediaId)
                            ?.toDomainTrack()
                            ?.getMediaItem(this@PlayerService)
                    }
                    player.addMediaItems(index, fullMediaItems)

                    Timber.d("qgeck added full media items")
                }

                Timber.d("qgeck called addMediaItems")
            }
        }
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(mediaSessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this@PlayerService,
                    App.REQUEST_CODE_LAUNCH_APP,
                    LauncherActivity.createIntent(this@PlayerService),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.isPlaying.not() || player.mediaItemCount == 0) {
            stopSelf()
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Timber.d("qgeck onDestroy called")

        stop()
        player.stop()
        player.clearMediaItems()
        dispatcher.onServicePreSuperOnDestroy()
        player.release()
        playerNotificationManager.setPlayer(null)

        super.onDestroy()
    }

    private fun storeState() {
        val state = PlayerState(
            player.playWhenReady,
            player.currentSourcePaths,
            currentIndex,
            player.duration,
            player.currentPosition,
            player.repeatMode
        )
        Timber.d("qgeck storing state: $state")
        sharedPreferences.edit {
            putString(PREF_KEY_PLAYER_STATE, Json.encodeToString(state))
        }
    }

    private fun PlayerState.set() {
        Timber.d("qgeck set state: $this")
        lifecycleScope.launch {
            player.setMediaItems(sourcePaths.map { it.getMediaItem() })
            val windowIndex =
                player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
            player.seekToDefaultPosition(windowIndex + currentIndex)
            player.seekTo(progress)
            player.repeatMode = repeatMode
        }
    }

    private fun restoreState() {
        if (player.playWhenReady.not()) {
            sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)
                ?.let { catchAsNull { Json.decodeFromString<PlayerState>(it) } }
                ?.set()
        }
    }

    private fun forceIndex(index: Int) {
        val windowIndex = player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        player.seekToDefaultPosition(windowIndex + index)
    }

    internal fun play() {
        Timber.d("qgeck play invoked")

        resume()
    }

    private fun resume() {
        Timber.d("qgeck resume invoked")

        if (player.playWhenReady.not()) {
            player.play()
        }
    }

    fun pause() {
        Timber.d("qgeck pause invoked")

        player.pause()
    }

    fun stop() {
        pause()
        forceIndex(0)
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
    }

    fun fastForward() {
        lifecycleScope.launch {
            player.currentMediaItem?.toDomainTrack(db)?.let { track ->
                seekJob.cancel()
                seekJob = lifecycleScope.launch {
                    while (true) {
                        withContext(Dispatchers.Main) {
                            val seekTo = (player.currentPosition + 1000).let {
                                if (it > track.duration) track.duration else it
                            }
                            player.seekTo(seekTo)
                        }
                        delay(100)
                    }
                }
            }
        }
    }

    fun rewind() {
        if (player.mediaItemCount > 0 && currentIndex > -1) {
            seekJob.cancel()
            seekJob = lifecycleScope.launch {
                while (true) {
                    val seekTo = (player.currentPosition - 1000).let {
                        if (it < 0) 0 else it
                    }
                    player.seekTo(seekTo)
                    delay(100)
                }
            }
        }
    }

    fun stopFastSeek() {
        seekJob.cancel()
    }

    private fun seekToHead() {
        player.seekTo(0)
    }

    private fun seekToTail() {
        lifecycleScope.launch {
            player.currentMediaItem?.toDomainTrack(db)?.duration?.let { player.seekTo(it) }
        }
    }

    fun headOrPrev() {
        lifecycleScope.launch {
            val currentDuration =
                player.currentMediaItem?.toDomainTrack(db)?.duration ?: return@launch

            if (currentIndex > 0 && player.contentPosition < currentDuration / 100) prev()
            else seekToHead()
        }
    }
}