package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.util.toDomainTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Library(
    navController: NavHostController,
    scrollToTop: Long,
    snackBarMessage: String?,
    isSearchActive: MutableState<Boolean>,
    isFavoriteOnly: MutableState<Boolean>,
    onCancelProgress: (() -> Unit)?,
    onSelectNav: (nav: Nav?) -> Unit,
    onChangeTopBarTitle: (newTitle: String) -> Unit,
    onSelectArtist: (artist: Artist?) -> Unit,
    onSelectAlbum: (album: Album?) -> Unit,
    onSelectTrack: (track: DomainTrack?) -> Unit,
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
    val query = remember { mutableStateOf("") }
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
            composable("artists") {
                val topBarTitle = stringResource(id = R.string.nav_artist)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.ARTIST)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(AllArtists)
                }
                Artists(
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
                    scrollToTop = scrollToTop,
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
                    scrollToTop = scrollToTop,
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
                    scrollToTop = scrollToTop,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
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
                    isSearchActive = isSearchActive,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
                    onSelectGenre = { onSelectGenre(it) },
                    scrollToTop = scrollToTop,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
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
}