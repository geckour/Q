package com.geckour.q.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation.NavHostController
import com.geckour.q.spotify.isSpotifyConfigured
import com.geckour.q.ui.component.PredictiveBackProgressHandler
import com.geckour.q.ui.component.predictiveBackSlide
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.dialog.DialogState
import com.geckour.q.ui.main.dialog.Dialogs
import com.geckour.q.ui.main.library.Library
import com.geckour.q.ui.main.player.PlayerSheet
import kotlin.math.abs
import kotlin.math.sin

private val SheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

private val SheetShadowElevation = 8.dp

private const val SHEET_SHADOW_FADE_SPEED = 4

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
    val scaffoldState = rememberBottomSheetScaffoldState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val bottomSheetHeightAngle = remember { Animatable(0f) }
    val sheetBackProgress = remember { Animatable(0f) }
    val drawerBackProgress = remember { Animatable(0f) }
    var scaffoldHeight by remember { mutableIntStateOf(0) }
    var libraryHeight by remember { mutableIntStateOf(0) }
    val navigationBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    val sheetPeekHeight =
        (144 + abs(sin(bottomSheetHeightAngle.value)) * 20).dp + navigationBarHeight

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
            PredictiveBackProgressHandler(
                enabled = drawerState.targetValue == DrawerValue.Open,
                progress = drawerBackProgress,
                easing = LinearEasing,
            ) {
                drawerState.close()
            }
            ModalDrawerSheet(
                modifier = Modifier.predictiveBackSlide(
                    progress = { drawerBackProgress.value },
                    remainingOffset = { drawerState.remainingCloseOffset(size.width) },
                ),
                windowInsets = WindowInsets(),
            ) {
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
        BottomSheetScaffold(
            modifier = Modifier.onSizeChanged { scaffoldHeight = it.height },
            scaffoldState = scaffoldState,
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
                    onSelectSpotifyRecommendedRoot = actions::onSelectSpotifyRecommendedRoot,
                    onSelectArtist = actions::onSelectArtist,
                    onSelectAlbum = actions::onSelectAlbum,
                    onSelectTrack = actions::onSelectTrack,
                )
            },
            containerColor = QTheme.colors.colorBackground,
            sheetContainerColor = Color.Transparent,
            sheetPeekHeight = sheetPeekHeight,
            sheetShape = SheetShape,
            sheetDragHandle = null,
            sheetShadowElevation = lerp(
                SheetShadowElevation,
                0.dp,
                (sheetBackProgress.value * SHEET_SHADOW_FADE_SPEED).coerceAtMost(1f),
            ),
            sheetContent = {
                Box(
                    modifier = Modifier
                        .predictiveBackSlide(
                            progress = { sheetBackProgress.value },
                            remainingOffset = {
                                val sheetOffset = runCatching {
                                    scaffoldState.bottomSheetState.requireOffset()
                                }.getOrNull() ?: return@predictiveBackSlide Offset.Zero
                                val peekOffset = scaffoldHeight - sheetPeekHeight.toPx()
                                Offset(0f, (peekOffset - sheetOffset).coerceAtLeast(0f))
                            },
                        )
                        .clip(SheetShape)
                        .background(QTheme.colors.colorBackgroundBottomSheet)
                ) {
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
                    onChangeShowLyric = actions::onChangeShowLyric,
                    onQueueMove = actions::onQueueMove,
                    onChangeIndexRequested = actions::onChangeIndexRequested,
                    onRemoveTrackFromQueue = actions::onRemoveTrackFromQueue,
                    onToggleFavorite = actions::onToggleFavorite,
                )
                }
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
                    isSpotifyFlattened = library.isSpotifyFlattened,
                    loadSpotifySource = actions::loadSpotifySource,
                    loadSpotifyContainer = actions::loadSpotifyContainer,
                    resolveSpotifyContainer = actions::resolveSpotifyContainer,
                    isSpotifyConfigured = isSpotifyConfigured,
                    hasSpotifyCredential = library.hasSpotifyCredential,
                    onDialogEvent = actions::onDialogEvent,
                )
                PredictiveBackProgressHandler(
                    enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded,
                    progress = sheetBackProgress,
                    easing = LinearEasing,
                ) {
                    scaffoldState.bottomSheetState.partialExpand()
                }
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

internal fun DrawerState.remainingCloseOffset(drawerWidth: Float): Offset {
    val offset = currentOffset
    if (offset.isNaN()) return Offset.Zero

    return Offset(-drawerWidth - offset, 0f)
}
