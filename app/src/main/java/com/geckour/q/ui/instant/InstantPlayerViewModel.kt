package com.geckour.q.ui.instant

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.concurrent.futures.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.service.InstantPlayerService
import com.geckour.q.ui.compose.ArtworkColors
import com.geckour.q.ui.compose.DefaultArtworkColors
import com.geckour.q.ui.compose.extractArtworkColors
import com.geckour.q.util.getUseArtworkColorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class InstantPlayerViewModel : ViewModel() {

    companion object {

        /** How long the player waits for the colors of the artwork based theme before it shows. */
        private val COLOR_THEME_WAIT_LIMIT = 500.milliseconds
    }

    private var mediaController: MediaController? = null

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            onStateChanged()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int
        ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            onStateChanged()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)

            // The embedded artwork comes with the metadata of the file's tracks, so the file is
            // known to have none only once its tracks are.
            val mayHaveNewArtwork = events.containsAny(
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
            if (mayHaveNewArtwork && player.currentTracks.isEmpty().not()) {
                embeddedArtwork.value = EmbeddedArtwork(player.mediaMetadata.artworkData)
            }
        }
    }

    internal val duration = MutableStateFlow(0L)
    internal val current = MutableStateFlow(0L)
    internal val currentBuffer = MutableStateFlow(0L)
    internal val isPlaying = MutableStateFlow(false)

    /** The colors of the artwork based theme, or null for the normal theme. */
    internal val artworkColors = MutableStateFlow<ArtworkColors?>(null)

    /**
     * Whether the player can show. With the artwork based theme it waits for the file's artwork,
     * for up to [COLOR_THEME_WAIT_LIMIT], so that it opens in the artwork's colors instead of fading
     * into them.
     */
    internal val isColorThemeReady = MutableStateFlow(false)

    /** The artwork embedded in the file, set once the file's tracks are known. */
    private val embeddedArtwork = MutableStateFlow<EmbeddedArtwork?>(null)

    internal fun initializeMediaController(context: Context, uri: Uri) {
        resolveColorTheme(context.applicationContext)

        viewModelScope.launch {
            mediaController = MediaController.Builder(
                context,
                SessionToken(
                    context,
                    ComponentName(context, InstantPlayerService::class.java)
                )
            ).buildAsync()
                .await()
                .apply {
                    addListener(playerListener)
                    submitMedia(this, uri)
                    viewModelScope.launch {
                        while (this.isActive) {
                            current.value = currentPosition
                            currentBuffer.value = bufferedPosition
                            delay(100)
                        }
                    }
                }
        }
    }

    internal fun releaseMediaController() {
        mediaController?.stop()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
    }

    private fun resolveColorTheme(context: Context) {
        viewModelScope.launch {
            delay(COLOR_THEME_WAIT_LIMIT)
            isColorThemeReady.value = true
        }
        viewModelScope.launch {
            if (context.getUseArtworkColorTheme().first().not()) {
                isColorThemeReady.value = true
                return@launch
            }

            embeddedArtwork
                .filterNotNull()
                .distinctUntilChanged { old, new -> old.data contentEquals new.data }
                .collectLatest { artwork ->
                    artworkColors.value = withContext(Dispatchers.Default) {
                        artwork.data?.extractArtworkColors() ?: DefaultArtworkColors
                    }
                    isColorThemeReady.value = true
                }
        }
    }

    private fun onStateChanged() {
        mediaController?.let {
            duration.value = it.duration
            current.value = it.currentPosition
            currentBuffer.value = it.bufferedPosition
            isPlaying.value = it.isPlaying
        }
    }

    private fun submitMedia(mediaController: MediaController, uri: Uri) {
        mediaController.addMediaItem(MediaItem.fromUri(uri))
        mediaController.play()
    }

    internal fun onPlaybackButtonPressed(playbackButton: PlaybackButton) {
        when (playbackButton) {
            PlaybackButton.PLAY -> mediaController?.play()
            PlaybackButton.PAUSE -> mediaController?.pause()
            PlaybackButton.NEXT -> mediaController?.seekToNext()
            PlaybackButton.PREV -> mediaController?.seekToPrevious()
            PlaybackButton.FF -> {
                mediaController?.sendCustomCommand(
                    SessionCommand(
                        InstantPlayerService.ACTION_COMMAND_FAST_FORWARD,
                        Bundle.EMPTY
                    ), Bundle.EMPTY
                )
            }

            PlaybackButton.REWIND -> {
                mediaController?.sendCustomCommand(
                    SessionCommand(
                        InstantPlayerService.ACTION_COMMAND_REWIND,
                        Bundle.EMPTY
                    ), Bundle.EMPTY
                )
            }

            PlaybackButton.UNDEFINED -> {
                mediaController?.sendCustomCommand(
                    SessionCommand(
                        InstantPlayerService.ACTION_COMMAND_STOP_FAST_SEEK,
                        Bundle.EMPTY
                    ), Bundle.EMPTY
                )
            }
        }
    }

    internal fun onSeekBarProgressChanged(progressRatio: Float) {
        mediaController?.let {
            it.seekTo((it.duration * progressRatio).toLong())
        }
    }
}

/** The image data of the artwork embedded in the file, or null when the file has none. */
private class EmbeddedArtwork(val data: ByteArray?)
