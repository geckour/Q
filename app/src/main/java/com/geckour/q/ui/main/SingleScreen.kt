package com.geckour.q.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.EqualizerParams
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleScreen(
    navController: NavHostController,
    topBarTitle: String,
    appBarOptionMediaItem: MediaItem?,
    sourcePaths: ImmutableList<String>,
    queue: ImmutableList<UiTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentBufferedPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
    routeInfo: QAudioDeviceInfo?,
    showLyric: Boolean,
    selectedNav: Nav?,
    selectedTrack: UiTrack?,
    selectedAlbum: Album?,
    selectedArtist: Artist?,
    selectedAllArtists: AllArtists?,
    selectedGenre: Genre?,
    equalizerParams: EqualizerParams?,
    currentDropboxItemList: Pair<String, ImmutableList<FolderMetadata>>,
    downloadTargets: ImmutableList<String>,
    invalidateDownloadedTargets: ImmutableList<String>,
    snackBarMessage: String?,
    forceScrollToCurrent: Long,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    scrollToTop: Long,
    onSelectNav: (nav: Nav?) -> Unit,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onChangeTopBarTitle: (title: String) -> Unit,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAllArtists: (allArtists: AllArtists?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
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
    onToggleShowLyrics: () -> Unit,
    onNewQueue: (
        queue: List<UiTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (target: UiTrack) -> Unit,
    onRemoveTrackFromQueue: (target: UiTrack) -> Unit,
    onShowDropboxDialog: () -> Unit,
    onRetrieveMedia: (onlyAdded: Boolean) -> Unit,
    onDownload: (targets: List<String>) -> Unit,
    onCancelDownload: () -> Unit,
    onStartDownloader: () -> Unit,
    onInvalidateDownloaded: (targets: List<String>) -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
    onDeleteTrack: (target: UiTrack) -> Unit,
    onExportLyric: (uiTrack: UiTrack) -> Unit,
    onAttachLyric: (targetTrackId: Long) -> Unit,
    onDetachLyric: (targetTrackId: Long) -> Unit,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (rootFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (rootFolderPath: String?, needDownloaded: Boolean) -> Unit,
    hideResetShuffleDialog: () -> Unit,
    onStartBilling: () -> Unit,
    onCancelProgress: (() -> Unit)?,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val bottomSheetHeightAngle = remember { Animatable(0f) }
    var libraryHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(sourcePaths) {
        if (sourcePaths.isNotEmpty()) {
            bottomSheetHeightAngle.animateTo(
                bottomSheetHeightAngle.value + Math.PI.toFloat(),
                animationSpec = tween(400),
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Drawer(
                    drawerState = drawerState,
                    navController = navController,
                    selectedNav = selectedNav,
                    equalizerParams = equalizerParams,
                    onSelectNav = onSelectNav,
                    onShowDropboxDialog = onShowDropboxDialog,
                    onRetrieveMedia = onRetrieveMedia
                )
            }
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            topBar = {
                QTopBar(
                    title = topBarTitle,
                    appBarOptionMediaItem = appBarOptionMediaItem,
                    drawerState = drawerState,
                    isSearchActive = isSearchActive.value,
                    onTapBar = onTapBar,
                    onToggleTheme = onToggleTheme,
                    onToggleFavorite = onToggleFavorite,
                    onSetOptionMediaItem = onSetOptionMediaItem,
                    onSelectAllArtists = onSelectAllArtists,
                    onSelectArtist = onSelectArtist,
                    onSelectAlbum = onSelectAlbum,
                    onSelectTrack = onSelectTrack,
                )
            },
            containerColor = QTheme.colors.colorBackground,
            sheetContainerColor = QTheme.colors.colorBackgroundBottomSheet,
            sheetPeekHeight = (144 + abs(sin(bottomSheetHeightAngle.value)) * 20).dp,
            sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            sheetDragHandle = null,
            sheetShadowElevation = 8.dp,
            sheetContent = {
                PlayerSheet(
                    animateController = true,
                    libraryHeight = libraryHeight,
                    bottomSheetValue = scaffoldState.bottomSheetState.currentValue,
                    queue = queue,
                    currentIndex = currentIndex,
                    currentPlaybackPosition = currentPlaybackPosition,
                    currentBufferedPosition = currentBufferedPosition,
                    currentPlaybackInfo = currentPlaybackInfo,
                    currentRepeatMode = currentRepeatMode,
                    isLoading = isLoading,
                    routeInfo = routeInfo,
                    showLyric = showLyric,
                    forceScrollToCurrent = forceScrollToCurrent,
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
                    onSelectTrack = onSelectTrack,
                    onToggleShowLyrics = onToggleShowLyrics,
                    onQueueMove = onQueueMove,
                    onChangeRequestedTrackInQueue = onChangeRequestedTrackInQueue,
                    onRemoveTrackFromQueue = onRemoveTrackFromQueue,
                    onToggleFavorite = onToggleFavorite,
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
                    scrollToTop = scrollToTop,
                    snackBarMessage = snackBarMessage,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    routeInfo = routeInfo,
                    onBackHandle = if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                        { coroutineScope.launch { scaffoldState.bottomSheetState.hide() } }
                    } else null,
                    onCancelProgress = onCancelProgress,
                    onSelectNav = onSelectNav,
                    onChangeTopBarTitle = onChangeTopBarTitle,
                    onSelectArtist = onSelectArtist,
                    onSelectAlbum = onSelectAlbum,
                    onSelectTrack = onSelectTrack,
                    onSelectGenre = onSelectGenre,
                    onDownload = onDownload,
                    onInvalidateDownloaded = onInvalidateDownloaded,
                    onStartBilling = onStartBilling,
                    onSetOptionMediaItem = onSetOptionMediaItem,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = { item ->
                        when (item.type) {
                            SearchItem.SearchItemType.TRACK -> {
                                onSelectTrack(item.data as UiTrack)
                            }

                            SearchItem.SearchItemType.ALBUM -> {
                                navController.navigate("tracks?albumId=${(item.data as Album).id}")
                            }

                            SearchItem.SearchItemType.ARTIST -> {
                                navController.navigate("albums?artistId=${(item.data as Artist).id}")
                            }

                            SearchItem.SearchItemType.GENRE -> {
                                navController.navigate("tracks?genreName=${(item.data as Genre).name}")
                            }

                            else -> Unit
                        }
                    },
                    onSearchItemLongClicked = { item ->
                        when (item.type) {
                            SearchItem.SearchItemType.TRACK -> {
                                onSelectTrack(item.data as UiTrack)
                            }

                            SearchItem.SearchItemType.ALBUM -> {
                                onSelectAlbum(item.data as Album)
                            }

                            SearchItem.SearchItemType.ARTIST -> {
                                onSelectArtist(item.data as Artist)
                            }

                            SearchItem.SearchItemType.GENRE -> {
                                onSelectGenre(item.data as Genre)
                            }

                            else -> Unit
                        }
                    },
                )
                Dialogs(
                    selectedTrack = selectedTrack,
                    selectedAlbum = selectedAlbum,
                    selectedArtist = selectedArtist,
                    selectedGenre = selectedGenre,
                    selectedAllArtists = selectedAllArtists,
                    navController = navController,
                    isSearchActive = isSearchActive,
                    currentDropboxItemList = currentDropboxItemList,
                    downloadTargets = downloadTargets,
                    invalidateDownloadedTargets = invalidateDownloadedTargets,
                    showDropboxDialog = showDropboxDialog,
                    showResetShuffleDialog = showResetShuffleDialog,
                    hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
                    isFavoriteOnly = isFavoriteOnly,
                    onSelectTrack = onSelectTrack,
                    onSelectAlbum = onSelectAlbum,
                    onSelectArtist = onSelectArtist,
                    onSelectAllArtists = onSelectAllArtists,
                    onSelectGenre = onSelectGenre,
                    onDeleteTrack = onDeleteTrack,
                    onExportLyric = onExportLyric,
                    onAttachLyric = onAttachLyric,
                    onDetachLyric = onDetachLyric,
                    onNewQueue = onNewQueue,
                    onStartAuthDropbox = onStartAuthDropbox,
                    onShowDropboxFolderChooser = onShowDropboxFolderChooser,
                    hideDropboxDialog = hideDropboxDialog,
                    startDropboxSync = startDropboxSync,
                    hideResetShuffleDialog = hideResetShuffleDialog,
                    onShuffle = shuffleQueue,
                    onResetShuffle = resetShuffleQueue,
                    onCancelDownload = onCancelDownload,
                    onStartDownloader = onStartDownloader,
                    onCancelInvalidateDownloaded = onCancelInvalidateDownloaded,
                    onStartInvalidateDownloaded = onStartInvalidateDownloaded
                )
            }
        }
    }
}