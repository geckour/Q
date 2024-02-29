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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InstantPlayerViewModel : ViewModel() {

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
    }

    internal val duration = MutableStateFlow(0L)
    internal val current = MutableStateFlow(0L)
    internal val currentBuffer = MutableStateFlow(0L)
    internal val isPlaying = MutableStateFlow(false)

    internal fun initializeMediaController(context: Context, uri: Uri) {
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