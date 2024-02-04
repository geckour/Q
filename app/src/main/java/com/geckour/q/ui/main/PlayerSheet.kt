package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.util.ShuffleActionType
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PlayerSheet(
    maxHeight: Int? = null,
    queue: ImmutableList<DomainTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentBufferedPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
    routeInfo: QAudioDeviceInfo?,
    showLyric: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    resetPlaybackButton: () -> Unit,
    onNewProgress: (newProgress: Long) -> Unit,
    rotateRepeatMode: () -> Unit,
    shuffleQueue: (actionType: ShuffleActionType?) -> Unit,
    resetShuffleQueue: () -> Unit,
    moveToCurrentIndex: () -> Unit,
    clearQueue: () -> Unit,
    onSelectTrack: (track: DomainTrack) -> Unit,
    onToggleShowLyrics: () -> Unit,
    forceScrollToCurrent: Long,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (track: DomainTrack) -> Unit,
    onRemoveTrackFromQueue: (track: DomainTrack) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    Column(
        modifier = if (maxHeight == null) Modifier else {
            Modifier.heightIn(
                max = (with(LocalDensity.current) { maxHeight.toDp() } + 144.dp - 36.dp)
                    .coerceAtLeast(288.dp)
            )
        }
    ) {
        Controller(
            currentTrack = queue.getOrNull(currentIndex),
            progress = currentPlaybackPosition,
            bufferProgress = currentBufferedPosition,
            queueTotalDuration = queue.sumOf { it.duration },
            queueRemainingDuration = queue.drop(currentIndex + 1)
                .sumOf { it.duration }
                    + (queue.getOrNull(currentIndex)?.duration ?: 0)
                    - currentPlaybackPosition,
            playbackInfo = currentPlaybackInfo,
            repeatMode = currentRepeatMode,
            isLoading = isLoading.first,
            routeInfo = routeInfo,
            showLyric = showLyric,
            onTogglePlayPause = onTogglePlayPause,
            onPrev = onPrev,
            onNext = onNext,
            onRewind = onRewind,
            onFastForward = onFastForward,
            resetPlaybackButton = resetPlaybackButton,
            onNewProgress = onNewProgress,
            rotateRepeatMode = rotateRepeatMode,
            shuffleQueue = shuffleQueue,
            resetShuffleQueue = resetShuffleQueue,
            moveToCurrentIndex = moveToCurrentIndex,
            clearQueue = clearQueue,
            onTrackSelected = onSelectTrack,
            cancelLoad = { isLoading.second?.invoke() },
            onToggleShowLyrics = onToggleShowLyrics,
            onToggleFavorite = onToggleFavorite,
        )
        Queue(
            domainTracks = queue,
            forceScrollToCurrent = forceScrollToCurrent,
            showLyric = showLyric,
            onTrackSelected = onSelectTrack,
            currentPlaybackPosition = currentPlaybackPosition,
            onQueueMove = onQueueMove,
            onChangeRequestedTrackInQueue = onChangeRequestedTrackInQueue,
            onRemoveTrackFromQueue = onRemoveTrackFromQueue,
            onToggleFavorite = onToggleFavorite,
        )
    }
}