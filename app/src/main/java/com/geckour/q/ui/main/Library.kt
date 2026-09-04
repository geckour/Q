package com.geckour.q.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.util.decodeUrlSafe
import com.geckour.q.util.toUiTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun Library(
    navController: NavHostController,
    scrollToTop: Long,
    snackbarMessage: String?,
    snackbarProgress: Float?,
    endItemMargin: Dp = 0.dp,
    isSearchActive: MutableState<Boolean>,
    query: MutableState<String>,
    isFavoriteOnly: MutableState<Boolean>,
    routeInfo: QAudioDeviceInfo?,
    onBackHandle: (() -> Unit)?,
    onCancelProgress: (() -> Unit)?,
    onSelectNav: (nav: Nav?) -> Unit,
    onChangeTopBarTitle: (newTitle: String) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectTrack: (track: UiTrack?) -> Unit,
    onSelectGenre: (genre: Genre?) -> Unit,
    onDownload: (targetTrackPaths: List<String>) -> Unit,
    onInvalidateDownloaded: (targetTrackIds: List<String>) -> Unit,
    onStartBilling: () -> Unit,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Derived from [query]: QSearchBar re-runs the search whenever the query changes, so it is
    // repopulated on its own once the restored query is applied. Keeping it out of the saved
    // instance state also avoids serializing every MediaItem of a result set into a Bundle.
    val result = remember { mutableStateOf<ImmutableList<SearchItem>>(persistentListOf()) }

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
            composable("artists") { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_artist)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.ARTIST)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(AllArtists)
                }
                val scrollPosition = rememberScrollPosition(backStackEntry)

                Artists(
                    endItemMargin = endItemMargin,
                    navController = navController,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
                    onSelectArtist = {
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
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
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
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val artistId = backStackEntry.arguments?.getLong("artistId")
                    ?: -1
                val scrollPosition = rememberScrollPosition(backStackEntry)
                LaunchedEffect(artistId) {
                    onSelectNav(Nav.ALBUM)
                    launch {
                        onSetOptionMediaItem(
                            DB.getInstance(context).artistDao().get(artistId) ?: return@launch
                        )
                    }
                }

                Albums(
                    endItemMargin = endItemMargin,
                    navController = navController,
                    artistId = backStackEntry.arguments?.getLong("artistId")
                        ?: -1,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
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
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
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
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: -1
                val genreName = backStackEntry.arguments?.getString("genreName")?.decodeUrlSafe()
                val scrollPosition = rememberScrollPosition(backStackEntry)
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
                    endItemMargin = endItemMargin,
                    albumId = albumId,
                    genreName = genreName,
                    isSearchActive = isSearchActive,
                    isFavoriteOnly = isFavoriteOnly,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
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
                        onInvalidateDownloaded(listOf(it.sourcePath))
                    },
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                )
            }
            composable("genres") { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_genre)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.GENRE)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                val scrollPosition = rememberScrollPosition(backStackEntry)
                Genres(
                    endItemMargin = endItemMargin,
                    navController = navController,
                    isSearchActive = isSearchActive,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
                    onSelectGenre = { onSelectGenre(it) },
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                )
            }
            composable("history") { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_history)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.HISTORY)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                val scrollPosition = rememberScrollPosition(backStackEntry)
                TrackHistories(
                    endItemMargin = endItemMargin,
                    onSelectHistory = { uiTrackHistory ->
                        onSelectTrack(uiTrackHistory.uiTrack)
                    },
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                )
            }
            composable("qzi") {
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_fortune)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(null)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                Qzi(
                    onClick = { onSelectTrack(it.toUiTrack()) }
                )
            }
            composable("pay") {
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_pay)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.PAY)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                Pay(onStartBilling = onStartBilling)
            }
            composable("equalizer") {
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_equalizer)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.EQUALIZER)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                Equalizer(routeInfo = routeInfo)
            }
        }
        QSnackbar(
            message = snackbarMessage,
            progress = snackbarProgress,
            onCancelProgress = onCancelProgress
        )
    }
}