package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.util.ShuffleActionType
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    isPortrait: Boolean = true,
    sheetProgress: Float = 1f,
    libraryHeight: Int? = null,
    bottomSheetValue: SheetValue? = null,
    queue: ImmutableList<UiTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentBufferedPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
    routeInfo: QAudioDeviceInfo?,
    showLyric: Boolean,
    forceScrollToCurrent: Long,
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
    onSelectTrack: (track: UiTrack) -> Unit,
    onToggleShowLyrics: () -> Unit,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (track: UiTrack) -> Unit,
    onRemoveTrackFromQueue: (track: UiTrack) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    Column(
        modifier = if (libraryHeight == null) Modifier else {
            Modifier.heightIn(
                max = (with(LocalDensity.current) { libraryHeight.toDp() } + 144.dp - 36.dp)
                    .coerceAtLeast(288.dp)
            )
        }
    ) {
        Controller(
            sheetProgress = if (isPortrait) sheetProgress else 0f,
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
            uiTracks = queue,
            bottomSheetValue = bottomSheetValue,
            showLyric = showLyric,
            onTrackSelected = onSelectTrack,
            currentPlaybackPosition = currentPlaybackPosition,
            forceScrollToCurrent = forceScrollToCurrent,
            onQueueMove = onQueueMove,
            onChangeRequestedTrackInQueue = onChangeRequestedTrackInQueue,
            onRemoveTrackFromQueue = onRemoveTrackFromQueue,
            onToggleFavorite = onToggleFavorite,
        )
    }
}