package com.geckour.q.ui.main

import com.geckour.q.domain.model.EqualizerParams
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SyncSizeAlert
import com.geckour.q.domain.model.UiTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class MainUiState(
    val player: PlayerUiState,
    val library: LibraryUiState,
    val routeInfo: QAudioDeviceInfo?,
    val dialogState: DialogState?,
    val syncSizeAlert: SyncSizeAlert?,
)

data class PlayerUiState(
    val queue: ImmutableList<UiTrack>,
    val sourcePaths: ImmutableList<String>,
    val currentIndex: Int,
    val currentPlaybackPosition: Long,
    val currentBufferedPosition: Long,
    val currentPlaybackInfo: Pair<Boolean, Int>,
    val currentRepeatMode: Int,
    val isLoading: Pair<Boolean, (() -> Unit)?>,
    val showLyric: Boolean,
    val forceScrollToCurrent: Long,
)

data class LibraryUiState(
    val topBarTitle: String,
    val appBarOptionMediaItem: MediaItem?,
    val selectedNav: Nav?,
    val equalizerParams: EqualizerParams?,
    val snackbarMessage: String?,
    val snackbarPaths: ImmutableList<String>,
    val snackbarProgress: Float?,
    val onCancelProgress: (() -> Unit)?,
    val scrollToTop: Long,
)

data class ProgressUiState(
    val message: String? = null,
    val paths: ImmutableList<String> = persistentListOf(),
    val fraction: Float? = null,
    val cancelable: Boolean = false,
)
