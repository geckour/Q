package com.geckour.q.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
fun TwinScreen(
    navController: NavHostController,
    topBarTitle: String,
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
    Row {
        TwinStartPage(
            navController = navController,
            topBarTitle = topBarTitle,
            scrollToTop = scrollToTop,
            selectedNav = selectedNav,
            selectedArtist = selectedArtist,
            selectedAlbum = selectedAlbum,
            selectedTrack = selectedTrack,
            selectedGenre = selectedGenre,
            equalizerParams = equalizerParams,
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
            onTapBar = onTapBar,
            onToggleTheme = onToggleTheme,
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
            onShowDropboxDialog = onShowDropboxDialog,
            onRetrieveMedia = onRetrieveMedia,
            onStartBilling = onStartBilling
        )
        TwinEndPage(
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
}

@Composable
fun RowScope.TwinStartPage(
    navController: NavHostController,
    topBarTitle: String,
    scrollToTop: Long,
    selectedNav: Nav?,
    selectedArtist: Artist?,
    selectedAlbum: Album?,
    selectedTrack: DomainTrack?,
    selectedGenre: Genre?,
    equalizerParams: EqualizerParams?,
    currentDropboxItemList: Pair<String, List<FolderMetadata>>,
    downloadTargets: List<String>,
    invalidateDownloadedTargets: List<Long>,
    snackBarMessage: String?,
    onCancelProgress: (() -> Unit)?,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    onSelectNav: (nav: Nav?) -> Unit,
    onChangeTopBarTitle: (newTitle: String) -> Unit,
    onTapBar: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectTrack: (track: DomainTrack?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
    onNewQueue: (
        queue: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) -> Unit,
    onDownload: (targetTrackPaths: List<String>) -> Unit,
    onCancelDownload: () -> Unit,
    onStartDownloader: () -> Unit,
    onInvalidateDownloaded: (targetTrackIds: List<Long>) -> Unit,
    onCancelInvalidateDownloaded: () -> Unit,
    onStartInvalidateDownloaded: () -> Unit,
    onDeleteTrack: (track: DomainTrack) -> Unit,
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
) {
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize(),
        scaffoldState = scaffoldState,
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
            onShuffle = onShuffle,
            onResetShuffle = onResetShuffle,
            onStartBilling = onStartBilling
        )
    }
}

@Composable
fun RowScope.TwinEndPage(
    queue: List<DomainTrack>,
    currentIndex: Int,
    currentPlaybackPosition: Long,
    currentPlaybackInfo: Pair<Boolean, Int>,
    currentRepeatMode: Int,
    isLoading: Pair<Boolean, (() -> Unit)?>,
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
    onSelectTrack: (track: DomainTrack) -> Unit,
    onToggleShowLyrics: () -> Unit,
    forceScrollToCurrent: Long,
    onQueueMove: (from: Int, to: Int) -> Unit,
    onChangeRequestedTrackInQueue: (track: DomainTrack) -> Unit,
    onRemoveTrackFromQueue: (track: DomainTrack) -> Unit
) {
    Box(
        modifier = Modifier
            .background(color = QTheme.colors.colorBackground)
            .weight(1f)
            .fillMaxSize()
            .padding(start = 8.dp)
    ) {
        Card(
            backgroundColor = QTheme.colors.colorBackgroundBottomSheet,
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
        ) {
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
    }
}