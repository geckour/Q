package com.geckour.q.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.EqualizerLevelRatio
import com.geckour.q.data.db.model.EqualizerPreset
import com.geckour.q.domain.model.EqualizerParams
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.currentSourcePaths
import com.geckour.q.util.dropboxCachePathPattern
import com.geckour.q.util.getEqualizerEnabled
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getMediaItem
import com.geckour.q.util.getSelectedEqualizerPresetId
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.orderModified
import com.geckour.q.util.removedAt
import com.geckour.q.util.setActiveQAudioDeviceInfo
import com.geckour.q.util.setEqualizerParams
import com.geckour.q.util.setSelectedEqualizerPresetId
import com.geckour.q.util.toDomainTracks
import com.geckour.q.util.toUiTrack
import com.geckour.q.util.verifiedWithDropbox
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.FileNotFoundException

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService(), LifecycleOwner {

    companion object {
        const val ACTION_COMMAND_SUBMIT_QUEUE = "action_command_submit_queue"
        const val ACTION_EXTRA_SUBMIT_QUEUE_ACTION_TYPE = "action_extra_submit_queue_action_type"
        const val ACTION_EXTRA_SUBMIT_QUEUE_CLASS_TYPE = "action_extra_submit_queue_class_type"
        const val ACTION_EXTRA_SUBMIT_QUEUE_QUEUE = "action_extra_submit_queue_queue"

        const val ACTION_COMMAND_CANCEL_SUBMIT = "action_command_cancel_submit"

        const val ACTION_COMMAND_REMOVE_QUEUE = "action_command_remove_queue"
        const val ACTION_EXTRA_REMOVE_QUEUE_TARGET_SOURCE_PATH =
            "action_extra_remove_queue_target_source_path"
        const val ACTION_EXTRA_REMOVE_QUEUE_TARGET_INDEX =
            "action_extra_remove_queue_target_index"

        const val ACTION_COMMAND_CLEAR_QUEUE = "action_command_clear_queue"
        const val ACTION_EXTRA_CLEAR_QUEUE_NEED_TO_KEEP_CURRENT =
            "action_extra_clear_queue_need_to_keep_current"

        const val ACTION_COMMAND_MOVE_QUEUE = "action_command_move_queue"
        const val ACTION_EXTRA_MOVE_QUEUE_FROM = "action_extra_move_queue_from"
        const val ACTION_EXTRA_MOVE_QUEUE_TO = "action_extra_move_queue_to"

        const val ACTION_COMMAND_SHUFFLE_QUEUE = "action_command_shuffle_queue"
        const val ACTION_EXTRA_SHUFFLE_ACTION_TYPE = "action_extra_shuffle_action_type"

        const val ACTION_COMMAND_RESET_QUEUE_ORDER = "action_command_reset_queue_order"

        const val ACTION_COMMAND_RESET_QUEUE_INDEX = "action_command_reset_queue_index"
        const val ACTION_EXTRA_RESET_QUEUE_INDEX_FORCE = "action_extra_reset_queue_index_force"
        const val ACTION_EXTRA_RESET_QUEUE_INDEX_INDEX = "action_extra_reset_queue_index_index"

        const val ACTION_COMMAND_ROTATE_REPEAT_MODE = "action_command_rotate_repeat_mode"

        const val ACTION_COMMAND_RESTORE_STATE = "action_command_restore_state"

        const val ACTION_COMMAND_FAST_FORWARD = "action_command_fast_forward"
        const val ACTION_COMMAND_REWIND = "action_command_rewind"
        const val ACTION_COMMAND_STOP_FAST_SEEK = "action_command_stop_fast_seek"

        private const val ACTION_COMMAND_TOGGLE_FAVORITE = "action_command_toggle_favorite"

        const val PREF_KEY_PLAYER_STATE = "pref_key_player_state"
    }

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private val playerListener = object : Player.Listener {

        var lastMediaItem: MediaItem? = null

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)

            Timber.d(
                "qgeck player tracks changed: ${
                    tracks.groups.map { group ->
                        List(
                            group.length
                        ) { group.getTrackFormat(it) }
                    }
                }"
            )

            onStateChanged()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)

            Timber.d("qgeck player on timeline changed: $timeline, $reason")

            onStateChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            Timber.d(
                "qgeck player playback state: ${
                    when (playbackState) {
                        Player.STATE_IDLE -> "STATE_IDLE"
                        Player.STATE_BUFFERING -> "STATE_BUFFERING"
                        Player.STATE_READY -> "STATE_READY"
                        Player.STATE_ENDED -> "STATE_ENDED"
                        else -> "UNKNOWN"
                    }
                }"
            )

            onStateChanged()

            if (currentIndex == player.mediaItemCount - 1
                && playbackState == Player.STATE_ENDED
                && player.repeatMode == Player.REPEAT_MODE_OFF
            ) {
                stop()
            }

            if (playbackState == Player.STATE_READY &&
                player.playWhenReady &&
                player.currentMediaItem != lastMediaItem
            ) {
                lastMediaItem = player.currentMediaItem
                increasePlaybackCount()
            }
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int
        ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            Timber.d("qgeck player play when ready: $playWhenReady")

            onStateChanged()

            if (player.playbackState == Player.STATE_READY &&
                playWhenReady &&
                player.currentMediaItem != lastMediaItem
            ) {
                lastMediaItem = player.currentMediaItem
                increasePlaybackCount()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            Timber.d("qgeck player old position: ${oldPosition.positionMs} new position: ${newPosition.positionMs} discontinuity reason: $reason")

            onStateChanged()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error)
            FirebaseCrashlytics.getInstance().recordException(error)
            val position = player.currentPosition
            val playWhenReady = player.playWhenReady
            val isVerified = verifyByCauseIfNeeded(error)

            super.onPlayerError(error)

            if (isVerified) {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.seekTo(position)
                if (playWhenReady) resume()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
            Timber.d("qgeck audio session ID: $audioSessionId")

            setEqualizer(audioSessionId)
        }
    }

    private val playerAnalyticsListener = object : AnalyticsListener {

        override fun onAudioSessionIdChanged(
            eventTime: AnalyticsListener.EventTime,
            audioSessionId: Int
        ) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            Timber.d("qgeck audio session ID: $audioSessionId")

            setEqualizer(audioSessionId)
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult =
            ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_COMMAND_SUBMIT_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_CANCEL_SUBMIT, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_REMOVE_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_CLEAR_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_MOVE_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_SHUFFLE_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_RESET_QUEUE_ORDER, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_RESET_QUEUE_INDEX, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_ROTATE_REPEAT_MODE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_RESTORE_STATE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_FAST_FORWARD, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_COMMAND_REWIND, Bundle.EMPTY))
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
                    ACTION_COMMAND_SUBMIT_QUEUE -> {
                        val actionType = args.getParcelableCompat(
                            ACTION_EXTRA_SUBMIT_QUEUE_ACTION_TYPE,
                            InsertActionType::class.java
                        )
                            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                        val classType = args.getParcelableCompat(
                            ACTION_EXTRA_SUBMIT_QUEUE_CLASS_TYPE,
                            OrientedClassType::class.java
                        )
                            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                        val sourcePaths = args.getStringArrayList(ACTION_EXTRA_SUBMIT_QUEUE_QUEUE)
                            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                        lifecycleScope.launch {
                            val trackDao = db.trackDao()
                            val newQueue = sourcePaths.mapNotNull {
                                trackDao.getBySourcePath(it)
                            }
                            submitQueue(QueueInfo(QueueMetadata(actionType, classType), newQueue))
                        }
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_CANCEL_SUBMIT -> {
                        aliveSubmitQueueTask = false
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_REMOVE_QUEUE -> {
                        val targetIndex = args.getInt(ACTION_EXTRA_REMOVE_QUEUE_TARGET_INDEX, -1)
                        if (targetIndex > -1) {
                            removeQueue(targetIndex)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }

                        val targetSourcePath = args.getString(
                            ACTION_EXTRA_REMOVE_QUEUE_TARGET_SOURCE_PATH
                        ) ?: return Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
                        )

                        removeQueue(targetSourcePath, force = true)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_CLEAR_QUEUE -> {
                        val needToKeepCurrent = args.getBoolean(
                            ACTION_EXTRA_CLEAR_QUEUE_NEED_TO_KEEP_CURRENT,
                            true
                        )

                        clear(needToKeepCurrent)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_MOVE_QUEUE -> {
                        val from = args.getInt(ACTION_EXTRA_MOVE_QUEUE_FROM, -1)
                        if (from < 0) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

                        val to = args.getInt(ACTION_EXTRA_MOVE_QUEUE_TO, -1)
                        if (to < 0) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

                        moveQueuePosition(from, to)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_SHUFFLE_QUEUE -> {
                        val actionType = args.getParcelableCompat(
                            ACTION_EXTRA_SHUFFLE_ACTION_TYPE,
                            ShuffleActionType::class.java
                        )

                        shuffle(actionType)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_RESET_QUEUE_ORDER -> {
                        resetQueueOrder()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_RESET_QUEUE_INDEX -> {
                        val force = args.getBoolean(ACTION_EXTRA_RESET_QUEUE_INDEX_FORCE, false)
                        val index = args.getInt(ACTION_EXTRA_RESET_QUEUE_INDEX_INDEX, -1)
                        if (index < 0) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

                        if (force || currentIndex != index) {
                            forceIndex(index)
                        }
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_ROTATE_REPEAT_MODE -> {
                        rotateRepeatMode()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

                    ACTION_COMMAND_RESTORE_STATE -> {
                        restoreState()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }

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

                    ACTION_COMMAND_TOGGLE_FAVORITE -> {
                        player.currentSourcePaths.getOrNull(currentIndex)?.let {
                            lifecycleScope.launch {
                                val trackDao = DB.getInstance(this@PlayerService).trackDao()
                                val track = trackDao.getBySourcePath(it)?.track ?: return@launch
                                trackDao.insert(track.copy(isFavorite = track.isFavorite.not()))
                            }
                            onStateChanged()
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

    private val mediaRouterCallback = object : MediaRouter.Callback() {

        override fun onRouteSelected(
            router: MediaRouter,
            route: MediaRouter.RouteInfo,
            reason: Int
        ) {
            super.onRouteSelected(router, route, reason)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onRouteUnselected(
            router: MediaRouter,
            route: MediaRouter.RouteInfo,
            reason: Int
        ) {
            super.onRouteUnselected(router, route, reason)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            super.onRouteAdded(router, route)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            super.onRouteRemoved(router, route)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            super.onRouteChanged(router, route)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onProviderAdded(router: MediaRouter, provider: MediaRouter.ProviderInfo) {
            super.onProviderAdded(router, provider)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onProviderRemoved(router: MediaRouter, provider: MediaRouter.ProviderInfo) {
            super.onProviderRemoved(router, provider)

            onUpdateQAudioDeviceInfoList(router)
        }

        override fun onProviderChanged(router: MediaRouter, provider: MediaRouter.ProviderInfo) {
            super.onProviderChanged(router, provider)

            onUpdateQAudioDeviceInfoList(router)
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: Player

    private lateinit var mediaSession: MediaSession
    private var equalizer: Equalizer? = null
    private val currentIndex
        get() =
            if (player.currentMediaItemIndex == -1) 0
            else player.currentMediaItemIndex

    private lateinit var mediaRouter: MediaRouter
    private var audioManager: AudioManager? = null

    private lateinit var db: DB

    private var seekJob: Job = Job()

    private val sharedPreferences by inject<SharedPreferences>()

    private var inPurge = false
    private var aliveSubmitQueueTask = false

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
                addListener(playerListener)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                addAnalyticsListener(playerAnalyticsListener)
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
                Timber.d("qgeck addMediaItems forwarded")

                lifecycleScope.launch {
                    val trackDao = db.trackDao()
                    val fullMediaItems = mediaItems.mapNotNull {
                        trackDao.getBySourcePath(it.mediaId)
                            ?.getMediaItem()
                    }
                    player.addMediaItems(index, fullMediaItems)

                    Timber.d("qgeck added full media items")
                }
            }
        }
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(mediaSessionCallback)
            .setId(PlayerService::class.java.name)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this@PlayerService,
                    App.REQUEST_CODE_LAUNCH_APP,
                    LauncherActivity.createIntent(this@PlayerService),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_player) }
        )

        mediaRouter = MediaRouter.getInstance(this).apply {
            addCallback(
                MediaRouteSelector.EMPTY,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
        }
        audioManager = getSystemService()

        onUpdateQAudioDeviceInfoList()

        setEqualizer(player.audioSessionId)

        lifecycleScope.launch {
            db.equalizerPresetDao().getEqualizerPresets()
                .combine(getSelectedEqualizerPresetId()) { presets, selectedId ->
                    presets.entries.toList().firstOrNull { it.key.id == selectedId }
                }
                .collectLatest { selectedPreset ->
                    selectedPreset ?: return@collectLatest
                    val params = getEqualizerParams().take(1).lastOrNull() ?: return@collectLatest
                    reflectEqualizerSettings(
                        params = params,
                        equalizerPresetMapEntry = selectedPreset
                    )
                }
        }
        lifecycleScope.launch {
            getEqualizerEnabled().collectLatest { enabled ->
                setEqualizer(if (enabled) player.audioSessionId else null)
            }
        }

        restoreState()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.d("qgeck onTaskRemoved called")
        if (player.playWhenReady.not()) {
            purge()
            stopSelf()
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Timber.d("qgeck onDestroy called")
        purge()

        super.onDestroy()
    }

    private fun purge() {
        Timber.d("qgeck purge called")
        inPurge = true

        dispatcher.onServicePreSuperOnDestroy()
        player.removeListener(playerListener)
        player.removeAnalyticsListener(playerAnalyticsListener)
        mediaSession.release()
        stop()
        player.stop()
        player.release()
        mediaRouter.removeCallback(mediaRouterCallback)
    }

    private fun onStateChanged() {
        if (inPurge) return

        val state = PlayerState(
            player.playWhenReady,
            player.currentSourcePaths,
            currentIndex,
            player.duration,
            player.currentPosition,
            player.repeatMode
        )
        sharedPreferences.edit(commit = true) {
            putString(PREF_KEY_PLAYER_STATE, Json.encodeToString(state))
        }
        lifecycleScope.launch {
            val sourcePath = player.currentMediaItem?.let {
                it.localConfiguration?.uri?.toString() ?: it.mediaId
            } ?: run {
                mediaSession.setCustomLayout(emptyList())
                return@launch
            }
            val isFavorite = db.trackDao().getBySourcePath(sourcePath)?.track?.isFavorite ?: run {
                mediaSession.setCustomLayout(emptyList())
                return@launch
            }

            mediaSession.setCustomLayout(
                listOf(
                    CommandButton.Builder()
                        .setIconResId(if (isFavorite) R.drawable.star_filled else R.drawable.star)
                        .setDisplayName(getString(R.string.notification_action_toggle_favorite))
                        .setSessionCommand(
                            SessionCommand(ACTION_COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY)
                        ).build()
                )
            )
        }
    }

    private fun restoreState() {
        if (player.playWhenReady) player.playWhenReady = false

        val playerState = sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)
            ?.let { catchAsNull { Json.decodeFromString<PlayerState>(it) } }
            ?: return

        Timber.d("qgeck set state: $playerState")
        lifecycleScope.launch {
            player.setMediaItems(
                playerState.sourcePaths
                    .map { it.getMediaItem(this@PlayerService) }
            )
            player.prepare()
            val windowIndex = player.currentTimeline
                .getFirstWindowIndex(false)
                .coerceAtLeast(0)
            player.seekToDefaultPosition(windowIndex + playerState.currentIndex)
            player.seekTo(playerState.progress)
            player.repeatMode = playerState.repeatMode
        }
    }

    private fun onUpdateQAudioDeviceInfoList(router: MediaRouter = mediaRouter) {
        val audioDeviceInfoList =
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        val activeAudioDeviceInfo =
            if (Build.VERSION.SDK_INT > 30) {
                audioManager?.activePlaybackConfigurations
                    ?.firstOrNull { it.audioDeviceInfo != null }
                    ?.audioDeviceInfo
            } else null
        val activeQAudioDeviceInfo =
            getActiveQAudioDeviceInfo(router.routes, audioDeviceInfoList, activeAudioDeviceInfo)
        lifecycleScope.launch {
            setActiveQAudioDeviceInfo(activeQAudioDeviceInfo)
            activeQAudioDeviceInfo?.let { info ->
                db.audioDeviceEqualizerInfoDao()
                    .get(info.routeId, info.audioDeviceId, info.address, info.audioDeviceName)
                    ?.defaultEqualizerPresetId
                    ?.let { setSelectedEqualizerPresetId(it) }
            }
        }
    }

    private fun getActiveQAudioDeviceInfo(
        mediaRouteInfoList: List<MediaRouter.RouteInfo>,
        audioDeviceInfoList: List<AudioDeviceInfo>,
        activeAudioDeviceInfo: AudioDeviceInfo?,
    ) = mediaRouteInfoList.flatMap { mediaRouteInfo ->
        audioDeviceInfoList.mapNotNull { audioDeviceInfo ->
            val info = QAudioDeviceInfo.from(
                mediaRouteInfo = mediaRouteInfo,
                audioDeviceInfo = audioDeviceInfo,
                activeAudioDeviceInfo = activeAudioDeviceInfo
            )
            if (info.selected) info else null
        }
    }.lastOrNull().let { activeQAudioDeviceInfo ->
        val selectedMediaRouteInfo =
            mediaRouteInfoList.firstOrNull { it.isSelected } ?: return@let activeQAudioDeviceInfo
        activeQAudioDeviceInfo ?: QAudioDeviceInfo.getDefaultQAudioDeviceInfo(
            this,
            selectedMediaRouteInfo
        )
    }


    private suspend fun submitQueue(
        queueInfo: QueueInfo,
        positionToKeep: Int? = null,
        needSorted: Boolean = true,
    ) {
        aliveSubmitQueueTask = true

        val newQueue = queueInfo.queue
            .let {
                when {
                    needSorted -> it.orderModified(
                        queueInfo.metadata.classType,
                        queueInfo.metadata.actionType
                    )

                    else -> it
                }
            }
            .map { track ->
                if (aliveSubmitQueueTask.not()) {
                    return
                }
                (obtainDbxClient(this).take(1).lastOrNull()?.let {
                    track.verifiedWithDropbox(this, it)
                } ?: track)
                    .track
                    .sourcePath
                    .getMediaItem(this)
            }
        when (queueInfo.metadata.actionType) {
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                if (positionToKeep == null) clear()
                else clear(positionToKeep)
            }

            else -> Unit
        }
        when (queueInfo.metadata.actionType) {
            InsertActionType.NEXT,
            InsertActionType.OVERRIDE,
            InsertActionType.SHUFFLE_NEXT,
            InsertActionType.SHUFFLE_OVERRIDE,
            InsertActionType.SHUFFLE_SIMPLE_NEXT,
            InsertActionType.SHUFFLE_SIMPLE_OVERRIDE -> {
                player.addMediaItems(currentIndex + 1, newQueue)
            }

            InsertActionType.LAST,
            InsertActionType.SHUFFLE_LAST,
            InsertActionType.SHUFFLE_SIMPLE_LAST -> {
                player.addMediaItems(newQueue)
            }
        }

        player.prepare()
    }

    private fun clear(keepCurrentIfPlaying: Boolean = true) {
        val needToKeepCurrent = keepCurrentIfPlaying
                && player.playbackState == Player.STATE_READY
                && player.playWhenReady
        clear(if (needToKeepCurrent) currentIndex else -1)
    }

    private fun clear(positionToKeep: Int) {
        if (positionToKeep !in 0 until player.mediaItemCount) {
            player.clearMediaItems()
        } else {
            player.removeMediaItems(0, positionToKeep)
            player.removeMediaItems(1, player.mediaItemCount)
        }
    }

    private fun moveQueuePosition(from: Int, to: Int) {
        if (from == to) return
        val sourceRange = 0 until player.mediaItemCount
        if (from !in sourceRange || to !in sourceRange) return
        player.moveMediaItem(from, to)
    }

    private fun removeQueue(position: Int, force: Boolean = false) {
        if (position in 0 until player.mediaItemCount &&
            (force ||
                    player.playWhenReady.not() ||
                    player.playbackState != Player.STATE_READY ||
                    player.playbackState != Player.STATE_BUFFERING ||
                    position != currentIndex)
        ) {
            player.removeMediaItem(position)
        }
    }

    private fun removeQueue(sourcePath: String, force: Boolean = false) {
        val position = player.currentSourcePaths.indexOfFirst { it == sourcePath }
        if (position < 0) return

        removeQueue(position, force)
        removeQueue(sourcePath, force)
    }

    private fun shuffle(actionType: ShuffleActionType? = null) {
        lifecycleScope.launch {
            val currentQueue = player.currentSourcePaths
            if (player.mediaItemCount > 0) {
                val shuffled = when (actionType) {
                    null,
                    ShuffleActionType.SHUFFLE_SIMPLE -> {
                        currentQueue.shuffled()
                    }

                    ShuffleActionType.SHUFFLE_ALBUM_ORIENTED -> {
                        currentQueue.toDomainTracks(db)
                            .groupBy { it.album.id }
                            .map { it.value }
                            .shuffled()
                            .flatten()
                            .map { it.sourcePath }
                    }

                    ShuffleActionType.SHUFFLE_ARTIST_ORIENTED -> {
                        currentQueue.toDomainTracks(db)
                            .groupBy { it.artist.id }
                            .map { it.value }
                            .shuffled()
                            .flatten()
                            .map { it.sourcePath }
                    }
                }

                reorderQueue(shuffled)
            }
        }
    }

    private fun resetQueueOrder() {
        if (player.mediaItemCount < 1) return
        val sourcePaths = player.currentSourcePaths
        val cachedSourcePaths =
            sharedPreferences.getString(PREF_KEY_PLAYER_STATE, null)
                ?.let { catchAsNull { Json.decodeFromString<PlayerState>(it) } }
                ?.sourcePaths
                .orEmpty()
        val isCacheValid =
            sourcePaths.size == cachedSourcePaths.size &&
                    sourcePaths.containsAll(cachedSourcePaths)
        if (isCacheValid.not()) return

        reorderQueue(cachedSourcePaths)
    }

    private fun reorderQueue(newSourcePaths: List<String>) {
        lifecycleScope.launch {
            val targetIndex = newSourcePaths.indexOfFirst {
                it == player.currentSourcePaths.getOrNull(currentIndex)
            }.coerceAtLeast(0)
            submitQueue(
                queueInfo = QueueInfo(
                    QueueMetadata(
                        InsertActionType.OVERRIDE,
                        OrientedClassType.TRACK
                    ),
                    db.trackDao().getAllBySourcePaths(newSourcePaths.removedAt(targetIndex))
                ),
                positionToKeep = currentIndex,
                needSorted = false
            )
            moveQueuePosition(currentIndex, targetIndex)
        }
    }

    fun rotateRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
    }

    private fun forceIndex(index: Int) {
        val windowIndex = player.currentTimeline.getFirstWindowIndex(false).coerceAtLeast(0)
        player.seekToDefaultPosition(windowIndex + index)
    }

    private fun increasePlaybackCount() = lifecycleScope.launch {
        player.currentMediaItem?.toUiTrack(db)
            ?.let { track ->
                db.trackDao().increasePlaybackCount(track.id)
                db.albumDao().increasePlaybackCount(track.album.id)
                db.artistDao().increasePlaybackCount(track.artist.id)
            }
    }

    private fun verifyByCauseIfNeeded(throwable: Throwable): Boolean {
        val isTarget =
            throwable.getCausesRecursively().any {
                it is HttpDataSource.InvalidResponseCodeException ||
                        (it is FileNotFoundException &&
                                player.currentSourcePaths
                                    .getOrNull(currentIndex)
                                    ?.matches(dropboxCachePathPattern) == true)
            }

        if (isTarget) {
            lifecycleScope.launch {
                val index = currentIndex
                val dropboxClient = obtainDbxClient(this@PlayerService)
                    .firstOrNull()
                    ?: return@launch
                player.currentSourcePaths.getOrNull(currentIndex)?.let { sourcePath ->
                    val track = db.trackDao().getBySourcePath(sourcePath) ?: return@let
                    val new = track.verifiedWithDropbox(this@PlayerService, dropboxClient, true)
                        ?.getMediaItem()
                        ?: return@let

                    player.replaceMediaItem(index, new)
                }
            }
        } else {
            pause()
            removeQueue(currentIndex)
        }

        return isTarget
    }

    private fun Throwable.getCausesRecursively(initial: List<Throwable> = emptyList()): List<Throwable> {
        return cause?.let { it.getCausesRecursively(initial + it) } ?: initial
    }

    private fun setEqualizer(audioSessionId: Int?) {
        lifecycleScope.launch {
            if (audioSessionId != null && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (equalizer == null) {
                    try {
                        equalizer = Equalizer(0, audioSessionId).also { eq ->
                            if (getEqualizerParams().take(1).lastOrNull() == null) {
                                setEqualizerParams(
                                    EqualizerParams(
                                        levelRange = eq.bandLevelRange.let {
                                            it.first().toInt() to it.last().toInt()
                                        },
                                        bands = List(eq.numberOfBands.toInt()) { index ->
                                            EqualizerParams.Band(
                                                freqRange = eq.getBandFreqRange(index.toShort())
                                                    .let { it.first() to it.last() },
                                                centerFreq = eq.getCenterFreq(index.toShort())
                                            )
                                        }
                                    )
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        Timber.e(t)
                    }
                }
                getEqualizerParams().take(1).lastOrNull()?.let {
                    val selectedPresetId =
                        getSelectedEqualizerPresetId().take(1).lastOrNull() ?: return@let
                    val preset =
                        db.equalizerPresetDao()
                            .getEqualizerPreset(selectedPresetId).entries.firstOrNull()
                            ?: return@let
                    reflectEqualizerSettings(params = it, equalizerPresetMapEntry = preset)
                }
                equalizer?.enabled = getEqualizerEnabled().take(1).lastOrNull() == true
            } else {
                equalizer?.enabled = false
            }
        }
    }

    private fun reflectEqualizerSettings(
        params: EqualizerParams,
        equalizerPresetMapEntry: Map.Entry<EqualizerPreset, List<EqualizerLevelRatio>>
    ) {
        equalizerPresetMapEntry.value.forEachIndexed { i, ratio ->
            try {
                equalizer?.setBandLevel(
                    i.toShort(),
                    params.normalizedLevel(ratio = ratio.ratio).toShort()
                )
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
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
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
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
            player.currentMediaItem?.toUiTrack(db)?.let { track ->
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
            player.currentMediaItem?.toUiTrack(db)?.duration?.let { player.seekTo(it) }
        }
    }

    fun headOrPrev() {
        lifecycleScope.launch {
            val currentDuration =
                player.currentMediaItem?.toUiTrack(db)?.duration ?: return@launch

            if (currentIndex > 0 && player.contentPosition < currentDuration / 100) prev()
            else seekToHead()
        }
    }

    fun <T> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? =
        if (Build.VERSION.SDK_INT > 32) {
            getParcelable(key, clazz)
        } else {
            getParcelable(key) as? T
        }
}