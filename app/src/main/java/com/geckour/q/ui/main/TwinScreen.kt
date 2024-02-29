package com.geckour.q.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
fun TwinScreen(
    navController: NavHostController,
    topBarTitle: String,
    appBarOptionMediaItem: MediaItem?,
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
    snackBarProgress: Float?,
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
    onChangeIndexRequested: (index: Int) -> Unit,
    onRemoveTrackFromQueue: (index: Int) -> Unit,
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
    Row {
        TwinStartPage(
            modifier = Modifier.weight(1f),
            navController = navController,
            topBarTitle = topBarTitle,
            appBarOptionMediaItem = appBarOptionMediaItem,
            scrollToTop = scrollToTop,
            selectedNav = selectedNav,
            selectedAllArtists = selectedAllArtists,
            selectedArtist = selectedArtist,
            selectedAlbum = selectedAlbum,
            selectedTrack = selectedTrack,
            selectedGenre = selectedGenre,
            equalizerParams = equalizerParams,
            currentDropboxItemList = currentDropboxItemList,
            downloadTargets = downloadTargets,
            invalidateDownloadedTargets = invalidateDownloadedTargets,
            snackBarMessage = snackBarMessage,
            snackBarProgress = snackBarProgress,
            routeInfo = routeInfo,
            onCancelProgress = onCancelProgress,
            showDropboxDialog = showDropboxDialog,
            showResetShuffleDialog = showResetShuffleDialog,
            hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
            isSearchActive = isSearchActive,
            isFavoriteOnly = isFavoriteOnly,
            onSelectNav = onSelectNav,
            onChangeTopBarTitle = onChangeTopBarTitle,
            onTapBar = onTapBar,
            onToggleTheme = onToggleTheme,
            onSelectAllArtists = onSelectAllArtists,
            onSelectArtist = onSelectArtist,
            onSelectAlbum = onSelectAlbum,
            onSelectTrack = onSelectTrack,
            onSelectGenre = onSelectGenre,
            onNewQueue = onNewQueue,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onStartDownloader = onStartDownloader,
            onInvalidateDownloaded = onInvalidateDownloaded,
            onCancelInvalidateDownloaded = onCancelInvalidateDownloaded,
            onStartInvalidateDownloaded = onStartInvalidateDownloaded,
            onDeleteTrack = onDeleteTrack,
            onExportLyric = onExportLyric,
            onAttachLyric = onAttachLyric,
            onDetachLyric = onDetachLyric,
            onStartAuthDropbox = onStartAuthDropbox,
            onShowDropboxFolderChooser = onShowDropboxFolderChooser,
            hideDropboxDialog = hideDropboxDialog,
            startDropboxSync = startDropboxSync,
            hideResetShuffleDialog = hideResetShuffleDialog,
            onShuffle = shuffleQueue,
            onResetShuffle = resetShuffleQueue,
            onShowDropboxDialog = onShowDropboxDialog,
            onRetrieveMedia = onRetrieveMedia,
            onStartBilling = onStartBilling,
            onSetOptionMediaItem = onSetOptionMediaItem,
            onToggleFavorite = onToggleFavorite,
        )
        TwinEndPage(
            modifier = Modifier.weight(1f),
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
            onChangeIndexRequested = onChangeIndexRequested,
            onRemoveTrackFromQueue = onRemoveTrackFromQueue,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
fun RowScope.TwinStartPage(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    topBarTitle: String,
    appBarOptionMediaItem: MediaItem?,
    scrollToTop: Long,
    selectedNav: Nav?,
    selectedAllArtists: AllArtists?,
    selectedArtist: Artist?,
    selectedAlbum: Album?,
    selectedTrack: UiTrack?,
    selectedGenre: Genre?,
    equalizerParams: EqualizerParams?,
    currentDropboxItemList: Pair<String, ImmutableList<FolderMetadata>>,
    downloadTargets: ImmutableList<String>,
    invalidateDownloadedTargets: ImmutableList<String>,
    snackBarMessage: String?,
    snackBarProgress: Float?,
    routeInfo: QAudioDeviceInfo?,
    onCancelProgress: (() -> Unit)?,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    onSelectNav: (nav: Nav?) -> Unit,
    onChangeTopBarTitle: (newTitle: String) -> Unit,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectAllArtists: (allArtists: AllArtists?) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
    onNewQueue: (
        queue: List<UiTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onDownload: (targetTrackPaths: List<String>) -> Unit,
    onCancelDownload: () -> Unit,
    onStartDownloader: () -> Unit,
    onInvalidateDownloaded: (targetTrackIds: List<String>) -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
    onDeleteTrack: (track: UiTrack) -> Unit,
    onExportLyric: (uiTrack: UiTrack) -> Unit,
    onAttachLyric: (targetTrackId: Long) -> Unit,
    onDetachLyric: (targetTrackId: Long) -> Unit,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (selectedFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (rootFolderPath: String?, needDownloaded: Boolean) -> Unit,
    hideResetShuffleDialog: () -> Unit,
    onShuffle: (actionType: ShuffleActionType?) -> Unit,
    onResetShuffle: () -> Unit,
    onShowDropboxDialog: () -> Unit,
    onRetrieveMedia: (onlyAdded: Boolean) -> Unit,
    onStartBilling: () -> Unit,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        modifier = modifier,
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
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
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
        ) { paddingValues ->
            val coroutineScope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(color = QTheme.colors.colorBackground)
                    .fillMaxSize()
            ) {
                Library(
                    navController = navController,
                    scrollToTop = scrollToTop,
                    snackBarMessage = snackBarMessage,
                    snackBarProgress = snackBarProgress,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    routeInfo = routeInfo,
                    onBackHandle = if (drawerState.currentValue == DrawerValue.Open) {
                        { coroutineScope.launch { drawerState.close() } }
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
                    selectedAllArtists = selectedAllArtists,
                    selectedGenre = selectedGenre,
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
                    onShuffle = onShuffle,
                    onResetShuffle = onResetShuffle,
                    onCancelDownload = onCancelDownload,
                    onStartDownloader = onStartDownloader,
                    onCancelInvalidateDownloaded = onCancelInvalidateDownloaded,
                    onStartInvalidateDownloaded = onStartInvalidateDownloaded
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.TwinEndPage(
    modifier: Modifier = Modifier,
    queue: ImmutableList<UiTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentBufferedPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
    routeInfo: QAudioDeviceInfo?,
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
    onSelectTrack: (track: UiTrack) -> Unit,
    onToggleShowLyrics: () -> Unit,
    forceScrollToCurrent: Long,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeIndexRequested: (index: Int) -> Unit,
    onRemoveTrackFromQueue: (index: Int) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    var isPortrait by remember { mutableStateOf(true) }
    Box(
        modifier = modifier
            .background(color = QTheme.colors.colorBackground)
            .weight(1f)
            .fillMaxSize()
            .padding(start = 8.dp)
            .onSizeChanged { isPortrait = it.height > it.width }
    ) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackgroundBottomSheet),
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
        ) {
            PlayerSheet(
                isPortrait = isPortrait,
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
                onChangeIndexRequested = onChangeIndexRequested,
                onRemoveTrackFromQueue = onRemoveTrackFromQueue,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}