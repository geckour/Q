package com.geckour.q.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.window.layout.FoldingFeature
import com.geckour.q.domain.model.LayoutType
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SyncSizeAlert
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.dialog.DialogState
import com.geckour.q.ui.main.dialog.Dialogs
import com.geckour.q.ui.main.library.Library
import com.geckour.q.ui.main.player.PlayerSheet
import com.geckour.q.util.isSpotifyConfigured
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
fun TwinScreen(
    layoutType: LayoutType.Twin,
    navController: NavHostController,
    uiState: MainUiState,
    isSearchActive: MutableState<Boolean>,
    searchQuery: MutableState<String>,
    isFavoriteOnly: MutableState<Boolean>,
    actions: MainActions,
) {
    val isSideBySide = layoutType.orientation == FoldingFeature.Orientation.VERTICAL
    val navigationBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            TwinStartPage(
                endItemMargin = if (isSideBySide) navigationBarHeight else 0.dp,
                navController = navController,
                library = uiState.library,
                queue = uiState.player.queue,
                routeInfo = uiState.routeInfo,
                dialogState = uiState.dialogState,
                syncSizeAlert = uiState.syncSizeAlert,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                isFavoriteOnly = isFavoriteOnly,
                actions = actions,
            )
            TwinEndPage(
                isSideBySide = isSideBySide,
                endItemMargin = navigationBarHeight,
                player = uiState.player,
                routeInfo = uiState.routeInfo,
                actions = actions,
            )
        },
    ) { measurables, constraints ->
        val (startMeasurable, endMeasurable) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val hinge = layoutType.hingePosition?.let {
            IntRect(
                left = it.left.coerceIn(0, width),
                top = it.top.coerceIn(0, height),
                right = it.right.coerceIn(0, width),
                bottom = it.bottom.coerceIn(0, height),
            )
        } ?: IntRect(width / 2, height / 2, width / 2, height / 2)
        val (startBounds, endBounds) = if (isSideBySide) {
            val left = IntRect(0, 0, hinge.left, height)
            val right = IntRect(hinge.right, 0, width, height)
            if (layoutDirection == LayoutDirection.Rtl) right to left else left to right
        } else {
            IntRect(0, 0, width, hinge.top) to IntRect(0, hinge.bottom, width, height)
        }
        val startPlaceable =
            startMeasurable.measure(Constraints.fixed(startBounds.width, startBounds.height))
        val endPlaceable =
            endMeasurable.measure(Constraints.fixed(endBounds.width, endBounds.height))
        layout(width, height) {
            startPlaceable.place(startBounds.topLeft)
            endPlaceable.place(endBounds.topLeft)
        }
    }
}

@Composable
fun TwinStartPage(
    modifier: Modifier = Modifier,
    endItemMargin: Dp,
    navController: NavHostController,
    library: LibraryUiState,
    queue: ImmutableList<UiTrack>,
    routeInfo: QAudioDeviceInfo?,
    dialogState: DialogState?,
    syncSizeAlert: SyncSizeAlert?,
    isSearchActive: MutableState<Boolean>,
    searchQuery: MutableState<String>,
    isFavoriteOnly: MutableState<Boolean>,
    actions: MainActions,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(windowInsets = WindowInsets()) {
                Drawer(
                    drawerState = drawerState,
                    navController = navController,
                    selectedNav = library.selectedNav,
                    equalizerParams = library.equalizerParams,
                    onSelectNav = actions::onSelectNav,
                    onShowDropboxDialog = actions::onShowDropboxDialog,
                    onConfirmSpotifySignOut = actions::onConfirmSpotifySignOut,
                    isSpotifyUnlocked = library.isSpotifyUnlocked,
                    onRetrieveMedia = actions::onRetrieveMedia
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                QTopBar(
                    title = library.topBarTitle,
                    appBarOptionMediaItem = library.appBarOptionMediaItem,
                    drawerState = drawerState,
                    isSearchActive = isSearchActive.value,
                    onTapBar = actions::onTapBar,
                    onTapTitle = actions::onTapTopBarTitle,
                    onToggleTheme = actions::onToggleTheme,
                    onToggleFavorite = actions::onToggleFavorite,
                    onSetOptionMediaItem = actions::onSetOptionMediaItem,
                    onSelectAllArtists = actions::onSelectAllArtists,
                    onSelectSpotifyContainer = actions::onSelectSpotifyContainer,
                    onSelectArtist = actions::onSelectArtist,
                    onSelectAlbum = actions::onSelectAlbum,
                    onSelectTrack = actions::onSelectTrack,
                )
            },
        ) { paddingValues ->
            val coroutineScope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        end = paddingValues.calculateEndPadding(layoutDirection = LocalLayoutDirection.current),
                        start = paddingValues.calculateStartPadding(layoutDirection = LocalLayoutDirection.current),
                    )
                    .background(color = QTheme.colors.colorBackground)
                    .fillMaxSize()
            ) {
                Library(
                    endItemMargin = endItemMargin,
                    navController = navController,
                    scrollToTop = library.scrollToTop,
                    snackbarMessage = library.snackbarMessage,
                    snackbarPaths = library.snackbarPaths,
                    snackbarProgress = library.snackbarProgress,
                    isSearchActive = isSearchActive,
                    query = searchQuery,
                    selectedSavedQueueForModify =
                        (dialogState as? DialogState.SavedQueueModify)?.savedQueue,
                    isFavoriteOnly = isFavoriteOnly,
                    routeInfo = routeInfo,
                    onBackHandle = if (drawerState.currentValue == DrawerValue.Open) {
                        { coroutineScope.launch { drawerState.close() } }
                    } else null,
                    onCancelProgress = library.onCancelProgress,
                    onSelectNav = actions::onSelectNav,
                    onChangeTopBarTitle = actions::onChangeTopBarTitle,
                    onSelectArtist = actions::onSelectArtist,
                    onSelectAlbum = actions::onSelectAlbum,
                    onSelectTrack = actions::onSelectTrack,
                    onSelectGenre = actions::onSelectGenre,
                    onDownload = actions::onDownload,
                    onInvalidateDownloaded = actions::onInvalidateDownloaded,
                    onInvalidateDownloadedArtist = actions::onInvalidateDownloadedArtist,
                    onInvalidateDownloadedAlbum = actions::onInvalidateDownloadedAlbum,
                    onStartBilling = actions::onStartBilling,
                    onSetOptionMediaItem = actions::onSetOptionMediaItem,
                    onSetOptionArtist = actions::onSetOptionArtist,
                    onSetOptionAlbum = actions::onSetOptionAlbum,
                    onToggleFavorite = actions::onToggleFavorite,
                    onSearchItemClicked = { actions.onSearchItemClicked(it, navController) },
                    onSearchItemLongClicked = actions::onSearchItemLongClicked,
                    searchSpotify = actions::onSearchSpotify,
                    onSelectSavedQueueForOption = actions::onSelectSavedQueueForOption,
                    onSelectSavedQueueForModify = actions::onSelectSavedQueueForModify,
                    onDeleteSavedQueue = actions::onDeleteSavedQueue,
                    spotifyBrowse = library.spotifyBrowse,
                    isSpotifyConfigured = isSpotifyConfigured,
                    hasSpotifyCredential = library.hasSpotifyCredential,
                    onDialogEvent = actions::onDialogEvent,
                )
                Dialogs(
                    dialogState = dialogState,
                    syncSizeAlert = syncSizeAlert,
                    currentQueue = queue,
                    navController = navController,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    onDialogEvent = actions::onDialogEvent,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwinEndPage(
    modifier: Modifier = Modifier,
    isSideBySide: Boolean,
    endItemMargin: Dp,
    player: PlayerUiState,
    routeInfo: QAudioDeviceInfo?,
    actions: MainActions,
) {
    var isPortrait by remember { mutableStateOf(true) }
    Box(
        modifier = modifier
            .background(color = QTheme.colors.colorBackground)
            .fillMaxSize()
            .padding(
                if (isSideBySide) {
                    PaddingValues(
                        start = 8.dp,
                        top = with(LocalDensity.current) {
                            (WindowInsets.statusBars.getTop(this)).toDp()
                        },
                    )
                } else {
                    PaddingValues(top = 8.dp)
                }
            )
            .onSizeChanged { isPortrait = it.height > it.width }
    ) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackgroundBottomSheet),
            shape = if (isSideBySide) {
                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
            } else {
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            }
        ) {
            PlayerSheet(
                isPortrait = isPortrait,
                endItemMargin = endItemMargin,
                queue = player.queue,
                currentIndex = player.currentIndex,
                currentPlaybackPosition = player.currentPlaybackPosition,
                currentBufferedPosition = player.currentBufferedPosition,
                currentPlaybackInfo = player.currentPlaybackInfo,
                currentRepeatMode = player.currentRepeatMode,
                isLoading = player.isLoading,
                routeInfo = routeInfo,
                showLyric = player.showLyric,
                forceScrollToCurrent = player.forceScrollToCurrent,
                onTogglePlayPause = actions::onTogglePlayPause,
                onPrev = actions::onPrev,
                onNext = actions::onNext,
                onRewind = actions::onRewind,
                onFastForward = actions::onFastForward,
                onEnablePauseOnCurrentTrackEnd = actions::onEnablePauseOnCurrentTrackEnd,
                onShowSaveQueueDialog = actions::onShowSaveQueueDialog,
                resetPlaybackButton = actions::resetPlaybackButton,
                onNewProgress = actions::onNewProgress,
                rotateRepeatMode = actions::rotateRepeatMode,
                shuffleQueue = actions::shuffleQueue,
                resetShuffleQueue = actions::resetShuffleQueue,
                moveToCurrentIndex = actions::moveToCurrentIndex,
                clearQueue = actions::clearQueue,
                onSelectTrack = actions::onSelectTrack,
                onToggleShowLyrics = actions::onToggleShowLyrics,
                onQueueMove = actions::onQueueMove,
                onChangeIndexRequested = actions::onChangeIndexRequested,
                onRemoveTrackFromQueue = actions::onRemoveTrackFromQueue,
                onToggleFavorite = actions::onToggleFavorite,
            )
        }
    }
}
