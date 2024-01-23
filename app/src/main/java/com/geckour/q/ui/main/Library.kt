package com.geckour.q.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.toDomainTrack
import kotlinx.coroutines.launch

@Composable
fun Library(
    paddingValues: PaddingValues,
    navController: NavHostController,
    scrollToTop: Long,
    selectedArtist: Artist?,
    selectedAlbum: Album?,
    selectedTrack: DomainTrack?,
    selectedGenre: Genre?,
    currentDropboxItemList: Pair<String, List<FolderMetadata>>,
    downloadTargets: List<String>,
    invalidateDownloadedTargets: List<Long>,
    optionMediaItem: MediaItem?,
    snackBarMessage: String?,
    onCancelProgress: (() -> Unit)?,
    showDropboxDialog: Boolean,
    showResetShuffleDialog: Boolean,
    showOptionsDialog: Boolean,
    hasAlreadyShownDropboxSyncAlert: Boolean,
    onSelectNav: (nav: Nav?) -> Unit,
    onChangeTopBarTitle: (newTitle: String) -> Unit,
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
    onExportLyric: (domainTrack: DomainTrack) -> Unit,
    onAttachLyric: (targetTrackId: Long) -> Unit,
    onDetachLyric: (targetTrackId: Long) -> Unit,
    onStartAuthDropbox: () -> Unit,
    onShowDropboxFolderChooser: (selectedFolder: FolderMetadata?) -> Unit,
    hideDropboxDialog: () -> Unit,
    startDropboxSync: (rootFolderPath: String?, needDownloaded: Boolean) -> Unit,
    hideResetShuffleDialog: () -> Unit,
    onShuffle: (actionType: ShuffleActionType?) -> Unit,
    onResetShuffle: () -> Unit,
    onCloseOptionsDialog: () -> Unit,
    onStartBilling: () -> Unit,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .padding(paddingValues)
            .background(color = QTheme.colors.colorBackground)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = "artists",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                composable("artists") {
                    val topBarTitle = stringResource(id = R.string.nav_artist)
                    LaunchedEffect(navController.currentDestination) {
                        onSelectNav(Nav.ARTIST)
                        onChangeTopBarTitle(topBarTitle)
                        onSetOptionMediaItem(AllArtists)
                    }
                    Artists(
                        navController = navController, onSelectArtist = {
                            onSelectArtist(it)
                        },
                        onDownload = onDownload,
                        onInvalidateDownloaded = {
                            coroutineScope.launch {
                                val targets =
                                    DB.getInstance(context)
                                        .artistDao()
                                        .getContainTrackIds(it)
                                onInvalidateDownloaded(targets)
                            }
                        },
                        scrollToTop = scrollToTop,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                composable(
                    "albums?artistId={artistId}",
                    arguments = listOf(
                        navArgument("artistId") {
                            type = NavType.LongType
                            defaultValue = -1
                        }
                    )
                ) { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getLong("artistId")
                        ?: -1
                    LaunchedEffect(artistId) {
                        onSelectNav(Nav.ALBUM)
                        launch {
                            onSetOptionMediaItem(
                                DB.getInstance(context).artistDao().get(artistId) ?: return@launch
                            )
                        }
                    }
                    Albums(
                        navController = navController,
                        artistId = backStackEntry.arguments?.getLong("artistId")
                            ?: -1,
                        changeTopBarTitle = {
                            onChangeTopBarTitle(it)
                        },
                        onSelectAlbum = {
                            onSelectAlbum(it.album)
                        },
                        onDownload = {
                            onDownload(it)
                        },
                        onInvalidateDownloaded = {
                            coroutineScope.launch {
                                val targets =
                                    DB.getInstance(context)
                                        .albumDao()
                                        .getContainTrackIds(it)
                                onInvalidateDownloaded(targets)
                            }
                        },
                        scrollToTop = scrollToTop,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                composable(
                    "tracks?albumId={albumId}&genreName={genreName}",
                    arguments = listOf(
                        navArgument("albumId") {
                            type = NavType.LongType
                            defaultValue = -1
                        },
                        navArgument("genreName") {
                            type = NavType.StringType
                            nullable = true
                        }
                    )
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: -1
                    val genreName = backStackEntry.arguments?.getString("genreName")
                    LaunchedEffect(albumId) {
                        onSelectNav(Nav.TRACK)
                        launch {
                            onSetOptionMediaItem(
                                DB.getInstance(context).albumDao().get(albumId)?.album
                                    ?: return@launch
                            )
                        }
                    }
                    Tracks(
                        albumId = albumId,
                        genreName = genreName,
                        changeTopBarTitle = {
                            onChangeTopBarTitle(it)
                        },
                        onTrackSelected = {
                            onSelectTrack(it)
                        },
                        onDownload = {
                            onDownload(listOfNotNull(it.dropboxPath))
                        },
                        onInvalidateDownloaded = {
                            onInvalidateDownloaded(listOf(it.id))
                        },
                        scrollToTop = scrollToTop,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                composable("genres") {
                    val topBarTitle = stringResource(id = R.string.nav_genre)
                    LaunchedEffect(navController.currentDestination) {
                        onSelectNav(Nav.GENRE)
                        onChangeTopBarTitle(topBarTitle)
                        onSetOptionMediaItem(null)
                    }
                    Genres(
                        navController = navController,
                        onSelectGenre = { onSelectGenre(it) },
                        scrollToTop = scrollToTop
                    )
                }
                composable("qzi") {
                    val topBarTitle = stringResource(id = R.string.nav_fortune)
                    LaunchedEffect(navController.currentDestination) {
                        onSelectNav(null)
                        onChangeTopBarTitle(topBarTitle)
                        onSetOptionMediaItem(null)
                    }
                    Qzi(
                        onClick = { onSelectTrack(it.toDomainTrack()) }
                    )
                }
                composable("pay") {
                    val topBarTitle = stringResource(id = R.string.nav_pay)
                    LaunchedEffect(navController.currentDestination) {
                        onSelectNav(Nav.PAY)
                        onChangeTopBarTitle(topBarTitle)
                        onSetOptionMediaItem(null)
                    }
                    Pay(onStartBilling = onStartBilling)
                }
                composable("equalizer") {
                    val topBarTitle = stringResource(id = R.string.nav_equalizer)
                    LaunchedEffect(navController.currentDestination) {
                        onSelectNav(Nav.EQUALIZER)
                        onChangeTopBarTitle(topBarTitle)
                        onSetOptionMediaItem(null)
                    }
                    Equalizer()
                }
            }
            QSnackBar(
                message = snackBarMessage,
                onCancelProgress = onCancelProgress
            )
        }
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
            onShuffle = onShuffle,
            onResetShuffle = onResetShuffle,
            onCloseOptionsDialog = onCloseOptionsDialog,
            onCancelDownload = onCancelDownload,
            onStartDownloader = onStartDownloader,
            onCancelInvalidateDownloaded = onCancelInvalidateDownloaded,
            onStartInvalidateDownloaded = onStartInvalidateDownloaded
        )
    }
}