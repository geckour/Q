package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.util.ShuffleActionType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun PlayerSheet(
    isPortrait: Boolean = true,
    needToAnimateController: Boolean = false,
    libraryHeight: Int? = null,
    endItemMargin: Dp = 0.dp,
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
    onEnablePauseOnCurrentTrackEnd: () -> Unit,
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
    onChangeIndexRequested: (index: Int) -> Unit,
    onRemoveTrackFromQueue: (index: Int) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val currentDensity = LocalDensity.current
    var sheetSize by remember { mutableIntStateOf(0) }
    var sheetBounds by remember { mutableFloatStateOf(0f) }
    val sheetProgress: () -> Float =
        {
            if (needToAnimateController)
                ((sheetBounds - with(currentDensity) { 144.dp.toPx() }) /
                        (sheetSize - with(currentDensity) { 144.dp.toPx() })).coerceIn(0f, 1f)
            else 1f
        }
    val isInLyricEditMode = remember { mutableStateOf(false) }
    val isLyricScrolledByUser = remember { mutableStateOf(false) }

    Column(
        modifier = (if (libraryHeight == null) Modifier else {
            Modifier.heightIn(
                max = (with(LocalDensity.current) { libraryHeight.toDp() } + 144.dp - 36.dp)
                    .coerceAtLeast(288.dp)
            )
        }).onGloballyPositioned {
            sheetSize = it.size.height
            sheetBounds = it.boundsInWindow().height
        }
    ) {
        Controller(
            sheetProgress = if (isPortrait) sheetProgress else { -> 0f },
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
            isInLyricEditMode = isInLyricEditMode,
            isLyricScrolledByUser = isLyricScrolledByUser.value,
            onTogglePlayPause = onTogglePlayPause,
            onPrev = onPrev,
            onNext = onNext,
            onRewind = onRewind,
            onFastForward = onFastForward,
            onEnablePauseOnCurrentTrackEnd = onEnablePauseOnCurrentTrackEnd,
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
            endItemMargin = endItemMargin,
            uiTracks = queue,
            isPlaying = currentPlaybackInfo.first,
            showLyric = showLyric,
            isInLyricEditMode = isInLyricEditMode.value,
            onTrackSelected = onSelectTrack,
            currentPlaybackPosition = currentPlaybackPosition,
            forceScrollToCurrent = forceScrollToCurrent,
            isLyricScrolledByUser = isLyricScrolledByUser,
            onQueueMove = onQueueMove,
            onNewProgress = onNewProgress,
            onChangeIndexRequested = onChangeIndexRequested,
            onRemoveTrackFromQueue = onRemoveTrackFromQueue,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerSheetPreview() {
    PlayerSheet(
        queue = persistentListOf(
            UiTrack(
                key = "key1",
                id = 0L,
                mediaId = 0L,
                codec = "Sample Codec",
                bitrate = 1000L,
                sampleRate = 10.0f,
                album = Album(
                    id = 0L,
                    artistId = 0L,
                    title = "Sample Album",
                    titleSort = "Sample Album",
                    artworkUriString = null,
                    hasAlbumArtist = false,
                    playbackCount = 0L,
                    totalDuration = 3000L,
                ),
                title = "Sample Track",
                titleSort = "Sample Track",
                artist = Artist(
                    id = 0L,
                    title = "Sample Artist",
                    titleSort = "Sample Artist",
                    playbackCount = 0L,
                    totalDuration = 10000L,
                    artworkUriString = null,
                ),
                albumArtist = null,
                composer = null,
                composerSort = null,
                thumbUriString = null,
                duration = 1000L,
                trackNum = 1,
                trackTotal = 3,
                discNum = 1,
                discTotal = 1,
                releaseYear = 2026,
                releaseMonth = 9,
                releaseDay = 6,
                genreName = null,
                sourcePath = "",
                dropboxPath = null,
                dropboxExpiredAt = null,
                artworkUriString = null,
                ignored = null,
                nowPlaying = true,
                isFavorite = false,
            ),
            UiTrack(
                key = "key2",
                id = 0L,
                mediaId = 0L,
                codec = "Sample Codec",
                bitrate = 1000L,
                sampleRate = 10.0f,
                album = Album(
                    id = 0L,
                    artistId = 0L,
                    title = "Sample Album",
                    titleSort = "Sample Album",
                    artworkUriString = null,
                    hasAlbumArtist = false,
                    playbackCount = 0L,
                    totalDuration = 3000L,
                ),
                title = "Sample Track",
                titleSort = "Sample Track",
                artist = Artist(
                    id = 0L,
                    title = "Sample Artist",
                    titleSort = "Sample Artist",
                    playbackCount = 0L,
                    totalDuration = 10000L,
                    artworkUriString = null,
                ),
                albumArtist = null,
                composer = null,
                composerSort = null,
                thumbUriString = null,
                duration = 1000L,
                trackNum = 1,
                trackTotal = 3,
                discNum = 1,
                discTotal = 1,
                releaseYear = 2026,
                releaseMonth = 9,
                releaseDay = 6,
                genreName = null,
                sourcePath = "",
                dropboxPath = null,
                dropboxExpiredAt = null,
                artworkUriString = null,
                ignored = null,
                nowPlaying = false,
                isFavorite = false,
            ),
        ),
        currentIndex = 0,
        currentPlaybackPosition = 200L,
        currentBufferedPosition = 300L,
        currentPlaybackInfo = true to 0,
        currentRepeatMode = Player.REPEAT_MODE_OFF,
        isLoading = false to {},
        routeInfo = null,
        showLyric = false,
        forceScrollToCurrent = 0L,
        onTogglePlayPause = {},
        onPrev = {},
        onNext = {},
        onRewind = {},
        onFastForward = {},
        onEnablePauseOnCurrentTrackEnd = {},
        resetPlaybackButton = {},
        onNewProgress = { _ -> },
        rotateRepeatMode = {},
        shuffleQueue = { _ -> },
        resetShuffleQueue = {},
        moveToCurrentIndex = {},
        clearQueue = {},
        onSelectTrack = {},
        onToggleShowLyrics = {},
        onQueueMove = { _, _ -> },
        onChangeIndexRequested = { _ -> },
        onRemoveTrackFromQueue = { _ -> },
        onToggleFavorite = { _ -> null },
    )
}