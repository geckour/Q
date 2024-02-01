package com.geckour.q.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalDrawer
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberDrawerState
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
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SingleScreen(
    navController: NavHostController,
    topBarTitle: String,
    optionMediaItem: MediaItem?,
    sourcePaths: ImmutableList<String>,
    queue: ImmutableList<DomainTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentBufferedPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
    routeInfo: QAudioDeviceInfo?,
    showLyric: Boolean,
    selectedNav: Nav?,
    selectedTrack: DomainTrack?,
    selectedAlbum: Album?,
    selectedArtist: Artist?,
    selectedGenre: Genre?,
    equalizerParams: EqualizerParams?,
    currentDropboxItemList: Pair<String, ImmutableList<FolderMetadata>>,
    downloadTargets: ImmutableList<String>,
    invalidateDownloadedTargets: ImmutableList<String>,
    snackBarMessage: String?,
    forceScrollToCurrent: Long,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    showOptionsDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    scrollToTop: Long,
    onSelectNav: (nav: Nav?) -> Unit,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onChangeTopBarTitle: (title: String) -> Unit,
    onSelectTrack: (track: DomainTrack?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
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
    closeOptionsDialog: () -> Unit,
    resetShuffleQueue: () -> Unit,
    moveToCurrentIndex: () -> Unit,
    clearQueue: () -> Unit,
    onToggleShowLyrics: () -> Unit,
    onNewQueue: (
        queue: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (target: DomainTrack) -> Unit,
    onRemoveTrackFromQueue: (target: DomainTrack) -> Unit,
    onShowDropboxDialog: () -> Unit,
    onRetrieveMedia: (onlyAdded: Boolean) -> Unit,
    onDownload: (targets: List<String>) -> Unit,
    onCancelDownload: () -> Unit,
    onStartDownloader: () -> Unit,
    onInvalidateDownloaded: (targets: List<String>) -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
    onDeleteTrack: (target: DomainTrack) -> Unit,
    onExportLyric: (domainTrack: DomainTrack) -> Unit,
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
    onShowOptions: () -> Unit,
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

    ModalDrawer(
        drawerState = drawerState,
        drawerElevation = 8.dp,
        drawerContent = {
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
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            topBar = {
                QTopBar(
                    title = topBarTitle,
                    optionMediaItem = optionMediaItem,
                    drawerState = drawerState,
                    isSearchActive = isSearchActive.value,
                    onTapBar = onTapBar,
                    onToggleTheme = onToggleTheme,
                    onToggleFavorite = onToggleFavorite,
                    onShowOptions = onShowOptions,
                    onSetOptionMediaItem = onSetOptionMediaItem,
                )
            },
            backgroundColor = QTheme.colors.colorBackground,
            sheetBackgroundColor = QTheme.colors.colorBackgroundBottomSheet,
            sheetPeekHeight = (144 + abs(sin(bottomSheetHeightAngle.value)) * 20).dp,
            sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            sheetElevation = 8.dp,
            sheetContent = {
                BackHandler(scaffoldState.bottomSheetState.isExpanded && drawerState.isClosed) {
                    coroutineScope.launch { scaffoldState.bottomSheetState.collapse() }
                }
                PlayerSheet(
                    maxHeight = libraryHeight,
                    queue = queue,
                    currentIndex = currentIndex,
                    currentPlaybackPosition = currentPlaybackPosition,
                    currentBufferedPosition = currentBufferedPosition,
                    currentPlaybackInfo = currentPlaybackInfo,
                    currentRepeatMode = currentRepeatMode,
                    isLoading = isLoading,
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
                    onSelectTrack = onSelectTrack,
                    onToggleShowLyrics = onToggleShowLyrics,
                    forceScrollToCurrent = forceScrollToCurrent,
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
                                onSelectTrack(item.data as DomainTrack)
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
                                onSelectTrack(item.data as DomainTrack)
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
                    navController = navController,
                    currentDropboxItemList = currentDropboxItemList,
                    downloadTargets = downloadTargets,
                    invalidateDownloadedTargets = invalidateDownloadedTargets,
                    optionMediaItem = optionMediaItem,
                    showDropboxDialog = showDropboxDialog,
                    showResetShuffleDialog = showResetShuffleDialog,
                    showOptionsDialog = showOptionsDialog,
                    hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
                    isFavoriteOnly = isFavoriteOnly,
                    onSelectTrack = onSelectTrack,
                    onSelectAlbum = onSelectAlbum,
                    onSelectArtist = onSelectArtist,
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
                    onCloseOptionsDialog = closeOptionsDialog,
                    onCancelDownload = onCancelDownload,
                    onStartDownloader = onStartDownloader,
                    onCancelInvalidateDownloaded = onCancelInvalidateDownloaded,
                    onStartInvalidateDownloaded = onStartInvalidateDownloaded
                )
            }
        }
    }
}