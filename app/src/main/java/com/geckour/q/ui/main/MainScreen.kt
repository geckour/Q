package com.geckour.q.ui.main

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.geckour.q.domain.model.LayoutType
import com.geckour.q.ui.compose.QTheme
import kotlinx.collections.immutable.toImmutableList

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    layoutType: LayoutType,
    actions: MainActions,
    onChangeNightMode: (isInNightMode: Boolean) -> Unit,
) {
    val isInNightMode by viewModel.isInNightMode.collectAsState(initial = isSystemInDarkTheme())
    val isLoading by viewModel.loading.collectAsState()
    val navController = rememberNavController()
    val topBarTitle by viewModel.topBarTitle.collectAsState()
    val queue by viewModel.currentQueueFlow.collectAsState(initial = emptyList())
    val sourcePaths by viewModel.currentSourcePathsFlow.collectAsState()
    val currentIndex by viewModel.currentIndexFlow.collectAsState()
    val currentPlaybackPosition by viewModel.currentPlaybackPositionFlow.collectAsState()
    val currentBufferedPosition by viewModel.currentBufferedPositionFlow.collectAsState()
    val currentPlaybackInfo by viewModel.currentPlaybackInfoFlow.collectAsState()
    val currentRepeatMode by viewModel.currentRepeatModeFlow.collectAsState()
    val forceScrollToCurrent by viewModel.forceScrollToCurrent.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val syncSizeAlert by viewModel.syncSizeAlert.collectAsState()
    val selectedNav by viewModel.selectedNav.collectAsState()
    val progress by viewModel.progressState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessageFlow.collectAsState()
    val equalizerParams by viewModel.equalizerParams.collectAsState(initial = null)
    val scrollToTop by viewModel.scrollToTop.collectAsState()
    val showLyric by viewModel.showLyric.collectAsState(initial = false)
    val isSpotifyUnlocked by viewModel.isSpotifyUnlocked.collectAsState(initial = false)
    val spotifyBrowse by viewModel.spotifyBrowse.collectAsState()
    val hasSpotifyCredential by viewModel.hasSpotifyCredential.collectAsState(initial = false)
    val appBarOptionMediaItem by viewModel.appBarOptionMediaItem.collectAsState()
    val routeInfo by viewModel.activeQAudioDeviceInfo.collectAsState(initial = null)
    val isSearchActive = rememberSaveable { mutableStateOf(false) }
    val searchQuery = rememberSaveable { mutableStateOf("") }
    val isFavoriteOnly = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isInNightMode) {
        onChangeNightMode(isInNightMode)
    }

    LaunchedEffect(isSearchActive.value) {
        if (isSearchActive.value) viewModel.syncSpotifyContentInBackground()
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    val uiState = MainUiState(
        player = PlayerUiState(
            queue = queue.toImmutableList(),
            sourcePaths = sourcePaths,
            currentIndex = currentIndex,
            currentPlaybackPosition = currentPlaybackPosition,
            currentBufferedPosition = currentBufferedPosition,
            currentPlaybackInfo = currentPlaybackInfo,
            currentRepeatMode = currentRepeatMode,
            isLoading = isLoading,
            showLyric = showLyric,
            forceScrollToCurrent = forceScrollToCurrent,
        ),
        library = LibraryUiState(
            topBarTitle = topBarTitle,
            appBarOptionMediaItem = appBarOptionMediaItem,
            selectedNav = selectedNav,
            equalizerParams = equalizerParams,
            snackbarMessage = progress.message ?: snackbarMessage,
            isSpotifyUnlocked = isSpotifyUnlocked,
            spotifyBrowse = spotifyBrowse,
            hasSpotifyCredential = hasSpotifyCredential,
            snackbarPaths = progress.paths,
            snackbarProgress = progress.fraction,
            onCancelProgress = if (progress.cancelable) viewModel::cancelProgress else null,
            scrollToTop = scrollToTop,
        ),
        routeInfo = routeInfo,
        dialogState = dialogState,
        syncSizeAlert = syncSizeAlert,
    )

    QTheme(darkTheme = isInNightMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (layoutType) {
                is LayoutType.Single -> {
                    SingleScreen(
                        navController = navController,
                        uiState = uiState,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        isFavoriteOnly = isFavoriteOnly,
                        actions = actions,
                    )
                }

                is LayoutType.Twin -> {
                    TwinScreen(
                        layoutType = layoutType,
                        navController = navController,
                        uiState = uiState,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        isFavoriteOnly = isFavoriteOnly,
                        actions = actions,
                    )
                }
            }
        }
    }
}
