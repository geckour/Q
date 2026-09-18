package com.geckour.q.service

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.geckour.q.util.SpotifyPlaybackErrorState
import com.geckour.q.util.SpotifyPlaybackStartTimeoutException
import com.geckour.q.util.SpotifyPremiumRequiredException
import com.geckour.q.util.createSpotifyConnectionParams
import com.geckour.q.util.isSpotifySourcePath
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Capabilities
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class SpotifyPlaybackSync(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val onSpotifyActiveChanged: (active: Boolean) -> Unit,
) {

    companion object {

        private const val START_CONFIRM_TIMEOUT_MILLIS = 10_000L

        private const val COMMAND_SETTLE_MILLIS = 1_500L

        private const val SEEK_DEBOUNCE_MILLIS = 300L

        private const val POLL_INTERVAL_MILLIS = 2_000L

        private const val AHEAD_DRIFT_TOLERANCE_MILLIS = 300L

        private const val BEHIND_DRIFT_TOLERANCE_MILLIS = 1_000L

        private const val TRACK_END_MARGIN_MILLIS = 5_000L

        private const val START_POSITION_TOLERANCE_MILLIS = 3_000L
    }

    private var appRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var capabilitiesSubscription: Subscription<Capabilities>? = null
    private var canPlayOnDemand: Boolean? = null
    private var isConnecting = false
    private var isReleased = false

    private var activeUri: String? = null
    private var loadedUri: String? = null
    private var isOwningPlayback = false
    private var isPausedByUs = true
    private var isStartConfirmed = false
    private var lastCommandedAt = 0L
    private var isSeekingQuietly = false
    private var pendingStart: Pair<String, Long>? = null
    private var isFinishingTrack = false

    private var seekJob: Job? = null
    private var pollJob: Job? = null

    private val listener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ||
                mediaItem?.mediaId != loadedUri
            ) {
                loadedUri = null
                isFinishingTrack = false
            }
            sync()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = sync()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sync()

        override fun onPlaybackStateChanged(playbackState: Int) = sync()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                onPlayerSeek()
            }
        }
    }

    fun start() {
        player.addListener(listener)
        sync()
    }

    fun release() {
        isReleased = true
        player.removeListener(listener)
        if (isOwningPlayback && isPausedByUs.not()) appRemote?.playerApi?.pause()
        disconnect()
    }

    private fun sync() {
        if (isReleased) return

        val uri = player.currentMediaItem
            ?.takeIf { it.mediaId.isSpotifySourcePath && it.mediaMetadata.durationMs != null }
            ?.mediaId
        if (uri != activeUri) {
            activeUri = uri
            onSpotifyActiveChanged(uri != null)
        }

        if (uri == null) {
            stopPolling()
            if (isOwningPlayback) {
                pauseSpotify()
                isOwningPlayback = false
                loadedUri = null
            }
            return
        }

        val shouldPlay = player.playWhenReady &&
                (player.playbackState == Player.STATE_READY ||
                        player.playbackState == Player.STATE_BUFFERING)
        if (shouldPlay.not()) {
            pendingStart = null
            stopPolling()
            if (isOwningPlayback && isPausedByUs.not()) pauseSpotify()
            return
        }
        if (isFinishingTrack) return

        val remote = appRemote?.takeIf { it.isConnected } ?: run {
            markPendingStart(uri)
            connect()
            return
        }
        when (canPlayOnDemand) {
            null -> {
                markPendingStart(uri)
                return
            }

            false -> {
                pendingStart = null
                onPlaybackError(SpotifyPremiumRequiredException())
                return
            }

            true -> Unit
        }
        if (loadedUri != uri) playSpotify(remote, uri)
        else if (isPausedByUs) resumeSpotify(remote)
        startPolling()
    }

    private fun markPendingStart(uri: String) {
        if (loadedUri == uri || pendingStart?.first == uri) return

        pendingStart = uri to player.currentPosition
    }

    private fun playSpotify(remote: SpotifyAppRemote, uri: String) {
        Timber.d("qgeck spotify play: $uri")
        val startPosition = pendingStart?.takeIf { it.first == uri }?.second
        pendingStart = null
        loadedUri = uri
        isOwningPlayback = true
        isPausedByUs = false
        isStartConfirmed = false
        isFinishingTrack = false
        markCommanded()
        seekJob?.cancel()
        startPosition?.let { seekPlayerQuietly(it) }
        remote.playerApi.play(uri).setErrorCallback { onPlaybackError(it) }
    }

    private fun resumeSpotify(remote: SpotifyAppRemote) {
        Timber.d("qgeck spotify resume")
        isPausedByUs = false
        markCommanded()
        remote.playerApi.resume().setErrorCallback { onPlaybackError(it) }
    }

    private fun pauseSpotify() {
        Timber.d("qgeck spotify pause")
        isPausedByUs = true
        markCommanded()
        seekJob?.cancel()
        appRemote?.playerApi?.pause()?.setErrorCallback { Timber.e(it) }
    }

    private fun onPlayerSeek() {
        if (isSeekingQuietly) return
        val uri = activeUri ?: return
        if (pendingStart?.first == uri) pendingStart = uri to player.currentPosition
        if (isFinishingTrack) {
            isFinishingTrack = false
            loadedUri = null
        }
        if (loadedUri != uri) return

        markCommanded()
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(SEEK_DEBOUNCE_MILLIS.milliseconds)
            markCommanded()
            appRemote?.playerApi
                ?.seekTo(player.currentPosition)
                ?.setErrorCallback { Timber.e(it) }
        }
    }

    private fun seekPlayerQuietly(positionMs: Long) {
        isSeekingQuietly = true
        try {
            player.seekTo(positionMs)
        } finally {
            isSeekingQuietly = false
        }
    }

    private fun seekPlayerQuietly(mediaItemIndex: Int, positionMs: Long) {
        isSeekingQuietly = true
        try {
            player.seekTo(mediaItemIndex, positionMs)
        } finally {
            isSeekingQuietly = false
        }
    }

    private fun onSpotifyPlayerState(state: PlayerState, isFresh: Boolean) {
        if (isReleased || isFinishingTrack) return
        val uri = loadedUri ?: return
        if (isOwningPlayback.not() || activeUri != uri) return

        val trackUri = state.track?.uri
        val elapsedSinceCommand = SystemClock.elapsedRealtime() - lastCommandedAt

        if (isStartConfirmed.not()) {
            if (trackUri == uri && state.isPaused.not()) {
                isStartConfirmed = true
                onStartConfirmed(state)
            } else if (elapsedSinceCommand > START_CONFIRM_TIMEOUT_MILLIS) {
                loadedUri = null
                onPlaybackError(SpotifyPlaybackStartTimeoutException())
            }
            return
        }

        if (elapsedSinceCommand < COMMAND_SETTLE_MILLIS || seekJob?.isActive == true) return

        when {
            trackUri != uri -> {
                if (isNearTrackEnd()) {
                    finishCurrentTrack()
                } else {
                    Timber.d("qgeck spotify switched to another item: $trackUri")
                    isOwningPlayback = false
                    loadedUri = null
                    player.pause()
                }
            }

            state.isPaused && isPausedByUs.not() -> {
                if (isNearTrackEnd()) {
                    finishCurrentTrack()
                } else {
                    Timber.d("qgeck spotify paused outside the app")
                    isPausedByUs = true
                    player.pause()
                }
            }

            state.isPaused.not() && isPausedByUs -> {
                Timber.d("qgeck spotify resumed outside the app")
                isPausedByUs = false
                player.play()
            }

            state.isPaused.not() && isFresh -> {
                val drift = player.currentPosition - state.playbackPosition
                if (drift > AHEAD_DRIFT_TOLERANCE_MILLIS || -drift > BEHIND_DRIFT_TOLERANCE_MILLIS) {
                    Timber.d("qgeck spotify drift corrected: $drift")
                    seekPlayerQuietly(state.playbackPosition)
                }
            }
        }
    }

    private fun onStartConfirmed(state: PlayerState) {
        val position = player.currentPosition
        when {
            position > START_POSITION_TOLERANCE_MILLIS -> {
                markCommanded()
                appRemote?.playerApi?.seekTo(position)?.setErrorCallback { Timber.e(it) }
            }

            state.playbackPosition <= START_POSITION_TOLERANCE_MILLIS -> {
                seekPlayerQuietly(state.playbackPosition)
            }
        }
    }

    private fun isNearTrackEnd(): Boolean {
        val duration = player.duration
        return duration != C.TIME_UNSET &&
                duration - player.currentPosition <= TRACK_END_MARGIN_MILLIS
    }

    private fun finishCurrentTrack() {
        Timber.d("qgeck spotify finished the current track")

        val nextIndex = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> player.currentMediaItemIndex
            else -> player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }
        } ?: run {
            val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
            isFinishingTrack = true
            seekPlayerQuietly(duration)
            return
        }

        if (nextIndex == player.currentMediaItemIndex) {
            loadedUri = null
            isFinishingTrack = false
        } else {
            isFinishingTrack = true
        }
        seekPlayerQuietly(nextIndex, 0)
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return

        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS.milliseconds)
                appRemote?.playerApi?.playerState
                    ?.setResultCallback { state ->
                        scope.launch { onSpotifyPlayerState(state, isFresh = true) }
                    }
                    ?.setErrorCallback { Timber.e(it) }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun markCommanded() {
        lastCommandedAt = SystemClock.elapsedRealtime()
    }

    private fun connect() {
        if (isConnecting) return
        if (SpotifyAppRemote.isSpotifyInstalled(context).not()) {
            onPlaybackError(CouldNotFindSpotifyApp())
            return
        }

        isConnecting = true
        SpotifyAppRemote.connect(
            context,
            createSpotifyConnectionParams(showAuthView = false),
            object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    scope.launch { onAppRemoteConnected(remote) }
                }

                override fun onFailure(throwable: Throwable) {
                    scope.launch { onConnectionFailure(throwable) }
                }
            }
        )
    }

    private fun onAppRemoteConnected(remote: SpotifyAppRemote) {
        isConnecting = false
        if (isReleased) {
            SpotifyAppRemote.disconnect(remote)
            return
        }
        if (appRemote === remote) return

        Timber.d("qgeck spotify connected")
        disconnect()
        appRemote = remote
        playerStateSubscription = remote.playerApi
            .subscribeToPlayerState()
            .setEventCallback { state ->
                scope.launch { onSpotifyPlayerState(state, isFresh = false) }
            }
            .also { subscription ->
                subscription.setErrorCallback { Timber.e(it) }
            }
        capabilitiesSubscription = remote.userApi
            .subscribeToCapabilities()
            .setEventCallback { capabilities ->
                scope.launch { onCapabilitiesChanged(capabilities.canPlayOnDemand) }
            }
            .also { subscription ->
                subscription.setErrorCallback { Timber.e(it) }
            }
        remote.userApi.capabilities
            .setResultCallback { capabilities ->
                scope.launch { onCapabilitiesChanged(capabilities.canPlayOnDemand) }
            }
            .setErrorCallback {
                Timber.e(it)
                scope.launch { onCapabilitiesUnavailable(remote) }
            }
        sync()
    }

    private fun onCapabilitiesChanged(canPlayOnDemand: Boolean) {
        if (isReleased || this.canPlayOnDemand == canPlayOnDemand) return

        Timber.d("qgeck spotify can play on demand: $canPlayOnDemand")
        this.canPlayOnDemand = canPlayOnDemand
        sync()
    }

    private fun onCapabilitiesUnavailable(remote: SpotifyAppRemote) {
        if (isReleased || appRemote !== remote || canPlayOnDemand != null) return

        canPlayOnDemand = true
        sync()
    }

    private fun onConnectionFailure(throwable: Throwable) {
        isConnecting = false
        val wasOwningPlayback = isOwningPlayback
        disconnect()
        if (isReleased) return

        if (wasOwningPlayback || activeUri != null && player.playWhenReady) {
            onPlaybackError(throwable)
        } else {
            Timber.e(throwable)
        }
    }

    private fun disconnect() {
        stopPolling()
        seekJob?.cancel()
        playerStateSubscription?.cancel()
        playerStateSubscription = null
        capabilitiesSubscription?.cancel()
        capabilitiesSubscription = null
        canPlayOnDemand = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        isOwningPlayback = false
        loadedUri = null
        isFinishingTrack = false
    }

    private fun onPlaybackError(throwable: Throwable) {
        Timber.e(throwable)
        SpotifyPlaybackErrorState.emit(throwable)
        if (activeUri != null) player.pause()
    }
}
