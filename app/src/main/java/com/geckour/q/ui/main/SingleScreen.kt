package com.geckour.q.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.dialog.DialogState
import com.geckour.q.ui.main.dialog.Dialogs
import com.geckour.q.ui.main.library.Library
import com.geckour.q.ui.main.player.PlayerSheet
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleScreen(
    navController: NavHostController,
    uiState: MainUiState,
    isSearchActive: MutableState<Boolean>,
    searchQuery: MutableState<String>,
    isFavoriteOnly: MutableState<Boolean>,
    actions: MainActions,
) {
    val player = uiState.player
    val library = uiState.library
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val bottomSheetHeightAngle = remember { Animatable(0f) }
    var libraryHeight by remember { mutableIntStateOf(0) }
    val navigationBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    LaunchedEffect(player.sourcePaths) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden &&
            player.sourcePaths.isNotEmpty()
        ) {
            bottomSheetHeightAngle.animateTo(
                bottomSheetHeightAngle.value + Math.PI.toFloat(),
                animationSpec = tween(400),
            )
        }
    }

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            actions.moveToCurrentIndex()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Drawer(
                    drawerState = drawerState,
                    navController = navController,
                    selectedNav = library.selectedNav,
                    equalizerParams = library.equalizerParams,
                    onSelectNav = actions::onSelectNav,
                    onShowDropboxDialog = actions::onShowDropboxDialog,
                    onRetrieveMedia = actions::onRetrieveMedia
                )
            }
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            topBar = {
                QTopBar(
                    title = library.topBarTitle,
                    appBarOptionMediaItem = library.appBarOptionMediaItem,
                    drawerState = drawerState,
                    isSearchActive = isSearchActive.value,
                    onTapBar = actions::onTapBar,
                    onToggleTheme = actions::onToggleTheme,
                    onToggleFavorite = actions::onToggleFavorite,
                    onSetOptionMediaItem = actions::onSetOptionMediaItem,
                    onSelectAllArtists = actions::onSelectAllArtists,
                    onSelectArtist = actions::onSelectArtist,
                    onSelectAlbum = actions::onSelectAlbum,
                    onSelectTrack = actions::onSelectTrack,
                )
            },
            containerColor = QTheme.colors.colorBackground,
            sheetContainerColor = QTheme.colors.colorBackgroundBottomSheet,
            sheetPeekHeight = (144 + abs(sin(bottomSheetHeightAngle.value)) * 20).dp + navigationBarHeight,
            sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            sheetDragHandle = null,
            sheetShadowElevation = 8.dp,
            sheetContent = {
                PlayerSheet(
                    needToAnimateController = true,
                    libraryHeight = libraryHeight,
                    endItemMargin = with(LocalDensity.current) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    },
                    queue = player.queue,
                    currentIndex = player.currentIndex,
                    currentPlaybackPosition = player.currentPlaybackPosition,
                    currentBufferedPosition = player.currentBufferedPosition,
                    currentPlaybackInfo = player.currentPlaybackInfo,
                    currentRepeatMode = player.currentRepeatMode,
                    isLoading = player.isLoading,
                    routeInfo = uiState.routeInfo,
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
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(color = QTheme.colors.colorBackground)
                    .fillMaxSize()
                    .onSizeChanged {
                        libraryHeight = it.height
                    }
            ) {
                Library(
                    navController = navController,
                    scrollToTop = library.scrollToTop,
                    snackbarMessage = library.snackbarMessage,
                    snackbarPaths = library.snackbarPaths,
                    snackbarProgress = library.snackbarProgress,
                    isSearchActive = isSearchActive,
                    query = searchQuery,
                    selectedSavedQueueForModify =
                        (uiState.dialogState as? DialogState.SavedQueueModify)?.savedQueue,
                    isFavoriteOnly = isFavoriteOnly,
                    routeInfo = uiState.routeInfo,
                    onBackHandle = if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                        { coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() } }
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
                    onSelectSavedQueueForOption = actions::onSelectSavedQueueForOption,
                    onSelectSavedQueueForModify = actions::onSelectSavedQueueForModify,
                    onDeleteSavedQueue = actions::onDeleteSavedQueue,
                )
                Dialogs(
                    dialogState = uiState.dialogState,
                    syncSizeAlert = uiState.syncSizeAlert,
                    currentQueue = player.queue,
                    navController = navController,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    onDialogEvent = actions::onDialogEvent,
                )
            }
        }
    }
}
