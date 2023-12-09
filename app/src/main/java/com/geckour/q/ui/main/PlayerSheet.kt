package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.util.ShuffleActionType

@Composable
fun PlayerSheet(
    queue: List<DomainTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
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
    onRemoveTrackFromQueue: (track: DomainTrack) -> Unit
) {
    Column {
        Controller(
            currentTrack = queue.getOrNull(currentIndex),
            progress = currentPlaybackPosition,
            queueTotalDuration = queue.sumOf { it.duration },
            queueRemainingDuration = queue.drop(currentIndex + 1)
                .sumOf { it.duration }
                    + (queue.getOrNull(currentIndex)?.duration ?: 0)
                    - currentPlaybackPosition,
            playbackInfo = currentPlaybackInfo,
            repeatMode = currentRepeatMode,
            isLoading = isLoading.first,
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
            onToggleShowLyrics = onToggleShowLyrics
        )
        Queue(
            domainTracks = queue,
            forceScrollToCurrent = forceScrollToCurrent,
            showLyric = showLyric,
            currentPlaybackPosition = currentPlaybackPosition,
            onQueueMove = onQueueMove,
            onChangeRequestedTrackInQueue = onChangeRequestedTrackInQueue,
            onRemoveTrackFromQueue = onRemoveTrackFromQueue
        )
    }
}