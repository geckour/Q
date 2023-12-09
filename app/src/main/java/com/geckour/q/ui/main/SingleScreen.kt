package com.geckour.q.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.EqualizerParams
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SingleScreen(
    navController: NavHostController,
    topBarTitle: String,
    sourcePaths: List<String>,
    queue: List<DomainTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
    showLyric: Boolean,
    selectedNav: Nav?,
    selectedTrack: DomainTrack?,
    selectedAlbum: Album?,
    selectedArtist: Artist?,
    selectedGenre: Genre?,
    equalizerParams: EqualizerParams?,
    currentDropboxItemList: Pair<String, List<FolderMetadata>>,
    downloadTargets: List<String>,
    invalidateDownloadedTargets: List<Long>,
    snackBarMessage: String?,
    forceScrollToCurrent: Long,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
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
    onInvalidateDownloaded: (targets: List<Long>) -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
    onDeleteTrack: (target: DomainTrack) -> Unit,
    onAttachLyric: (targetTrackId: Long) -> Unit,
    onDetachLyric: (targetTrackId: Long) -> Unit,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (rootFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (rootFolderPath: String?, needDownloaded: Boolean) -> Unit,
    hideResetShuffleDialog: () -> Unit,
    onStartBilling: () -> Unit,
    onCancelProgress: (() -> Unit)?,
) {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val bottomSheetHeightAngle = remember { Animatable(0f) }
    LaunchedEffect(sourcePaths) {
        if (sourcePaths.isNotEmpty()) {
            bottomSheetHeightAngle.animateTo(
                bottomSheetHeightAngle.value + Math.PI.toFloat(),
                animationSpec = tween(400),
            )
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        drawerContent = {
            Drawer(
                drawerState = scaffoldState.drawerState,
                navController = navController,
                selectedNav = selectedNav,
                equalizerParams = equalizerParams,
                onSelectNav = onSelectNav,
                onShowDropboxDialog = onShowDropboxDialog,
                onRetrieveMedia = onRetrieveMedia
            )
        },
        drawerElevation = 8.dp,
        topBar = {
            QTopBar(
                title = topBarTitle,
                drawerState = scaffoldState.drawerState,
                onTapBar = onTapBar,
                onToggleTheme = onToggleTheme,
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
                }
            )
        },
        backgroundColor = QTheme.colors.colorBackground,
        sheetBackgroundColor = QTheme.colors.colorBackgroundBottomSheet,
        sheetPeekHeight = (144 + abs(sin(bottomSheetHeightAngle.value)) * 20).dp,
        sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        sheetElevation = 8.dp,
        sheetContent = {
            BackHandler(scaffoldState.bottomSheetState.isExpanded && scaffoldState.drawerState.isClosed) {
                coroutineScope.launch { scaffoldState.bottomSheetState.collapse() }
            }
            PlayerSheet(
                queue = queue,
                currentIndex = currentIndex,
                currentPlaybackPosition = currentPlaybackPosition,
                currentPlaybackInfo = currentPlaybackInfo,
                currentRepeatMode = currentRepeatMode,
                isLoading = isLoading,
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
                onRemoveTrackFromQueue = onRemoveTrackFromQueue
            )
        }
    ) { paddingValues ->
        Library(
            paddingValues = paddingValues,
            navController = navController,
            scrollToTop = scrollToTop,
            selectedArtist = selectedArtist,
            selectedAlbum = selectedAlbum,
            selectedTrack = selectedTrack,
            selectedGenre = selectedGenre,
            currentDropboxItemList = currentDropboxItemList,
            downloadTargets = downloadTargets,
            invalidateDownloadedTargets = invalidateDownloadedTargets,
            snackBarMessage = snackBarMessage,
            onCancelProgress = onCancelProgress,
            showDropboxDialog = showDropboxDialog,
            showResetShuffleDialog = showResetShuffleDialog,
            hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
            onSelectNav = onSelectNav,
            onChangeTopBarTitle = onChangeTopBarTitle,
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
            onAttachLyric = onAttachLyric,
            onDetachLyric = onDetachLyric,
            onStartAuthDropbox = onStartAuthDropbox,
            onShowDropboxFolderChooser = onShowDropboxFolderChooser,
            hideDropboxDialog = hideDropboxDialog,
            startDropboxSync = startDropboxSync,
            hideResetShuffleDialog = hideResetShuffleDialog,
            onShuffle = shuffleQueue,
            onResetShuffle = resetShuffleQueue,
            onStartBilling = onStartBilling
        )
    }
}