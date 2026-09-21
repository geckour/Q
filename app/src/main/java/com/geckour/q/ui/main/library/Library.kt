package com.geckour.q.ui.main.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.util.encodeUrlSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.geckour.q.R
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.ui.license.Licenses
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.component.QSnackbar
import com.geckour.q.ui.main.extra.Equalizer
import com.geckour.q.ui.main.extra.Pay
import com.geckour.q.ui.main.extra.Qzi
import com.geckour.q.ui.main.dialog.DialogEvent
import com.geckour.q.util.decodeUrlSafe
import com.geckour.q.util.toUiTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun Library(
    navController: NavHostController,
    scrollToTop: Long,
    snackbarMessage: String?,
    snackbarPaths: ImmutableList<String>,
    snackbarProgress: Float?,
    endItemMargin: Dp = 0.dp,
    isSearchActive: MutableState<Boolean>,
    query: MutableState<String>,
    selectedSavedQueueForModify: UiSavedQueue?,
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
    onInvalidateDownloadedArtist: (artistId: Long) -> Unit,
    onInvalidateDownloadedAlbum: (albumId: Long) -> Unit,
    onStartBilling: () -> Unit,
    onSetOptionMediaItem: (mediaItem: MediaItem?) -> Unit,
    onSetOptionArtist: (artistId: Long) -> Unit,
    onSetOptionAlbum: (albumId: Long) -> Unit,
    onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
    searchSpotify: suspend (query: String) -> List<SearchItem>,
    onSelectSavedQueueForOption: (uiSavedQueue: UiSavedQueue?) -> Unit,
    onSelectSavedQueueForModify: (uiSavedQueue: UiSavedQueue?) -> Unit,
    onDeleteSavedQueue: (savedQueueId: Long) -> Unit,
    spotifyBrowse: SpotifyBrowseState,
    isSpotifyFlattened: Boolean,
    loadSpotifySource: (source: SpotifyBrowseSource, reset: Boolean) -> Unit,
    loadSpotifyContainer: (container: SpotifyContainer, reset: Boolean) -> Unit,
    resolveSpotifyContainer: suspend (uri: String, kind: SpotifyContainer.Kind) -> SpotifyContainer?,
    isSpotifyConfigured: Boolean,
    hasSpotifyCredential: Boolean,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
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
                    onInvalidateDownloaded = onInvalidateDownloadedArtist,
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                    searchSpotify = searchSpotify,
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
                    onSetOptionArtist(artistId)
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
                    onInvalidateDownloaded = onInvalidateDownloadedAlbum,
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    onScrollPositionUpdated = scrollPosition::update,
                    onToggleFavorite = onToggleFavorite,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                    searchSpotify = searchSpotify,
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
                    onSetOptionAlbum(albumId)
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
                    searchSpotify = searchSpotify,
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
                    searchSpotify = searchSpotify,
                )
            }
            composable("saved_queue") { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_saved_queue)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.SAVED_QUEUE)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                val scrollPosition = rememberScrollPosition(backStackEntry)
                SavedQueues(
                    endItemMargin = endItemMargin,
                    initialScrollPosition = scrollPosition.initialPosition,
                    scrollToTop = scrollToTop,
                    selectedSavedQueueForModify = selectedSavedQueueForModify,
                    onSelectSavedQueueForOption = onSelectSavedQueueForOption,
                    onSelectSavedQueueForModify = onSelectSavedQueueForModify,
                    onScrollPositionUpdated = scrollPosition::update,
                    onDeleteSavedQueue = onDeleteSavedQueue,
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
            composable("spotify") { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = spotifyTopBarTitle(section = null)
                LaunchedEffect(navController.currentDestination, topBarTitle) {
                    onSelectNav(Nav.SPOTIFY)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                LaunchedEffect(isSpotifyConfigured, hasSpotifyCredential) {
                    when {
                        isSpotifyConfigured.not() -> {
                            onDialogEvent(DialogEvent.NotifySpotifyNotConfigured)
                        }

                        hasSpotifyCredential.not() -> {
                            onDialogEvent(DialogEvent.RequestSpotifyAuth)
                        }
                    }
                }
                SpotifyScreen(
                    isSearchActive = isSearchActive,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                    searchSpotify = searchSpotify,
                ) {
                    if (isSpotifyConfigured.not() || hasSpotifyCredential.not()) return@SpotifyScreen

                    SpotifySourceMenu(endItemMargin = endItemMargin) { source ->
                        navController.navigate("spotify/source?type=${source.name}")
                    }
                }
            }
            composable(
                "spotify/source?type={type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val source = SpotifyBrowseSource.valueOf(
                    backStackEntry.arguments?.getString("type").orEmpty()
                )
                val level = spotifyBrowse.level(source.levelKey)
                val topBarTitle = spotifyTopBarTitle(spotifySourceLabel(source))
                val optionMediaItem =
                    spotifySourceOptionTarget(source, isSpotifyFlattened)
                LaunchedEffect(navController.currentDestination, topBarTitle, optionMediaItem) {
                    onSelectNav(Nav.SPOTIFY)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(optionMediaItem)
                }
                LaunchedEffect(source) {
                    if (level.items.isEmpty()) loadSpotifySource(source, true)
                }
                SpotifyScreen(
                    isSearchActive = isSearchActive,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                    searchSpotify = searchSpotify,
                ) {
                    SpotifyLevelList(
                        level = level,
                        backStackEntry = backStackEntry,
                        endItemMargin = endItemMargin,
                        onOpenContainer = { navController.navigateToSpotifyContainer(it) },
                        onLoadMore = { loadSpotifySource(source, false) },
                        onDialogEvent = onDialogEvent,
                    )
                }
            }
            composable(
                "spotify/container?uri={uri}&kind={kind}",
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("kind") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val uri = backStackEntry.arguments?.getString("uri").orEmpty().decodeUrlSafe()
                val kind = SpotifyContainer.Kind.valueOf(
                    backStackEntry.arguments?.getString("kind").orEmpty()
                )
                var container by remember { mutableStateOf<SpotifyContainer?>(null) }
                LaunchedEffect(uri, kind) {
                    container = resolveSpotifyContainer(uri, kind)
                    if (container == null) navController.popBackStack()
                }

                val resolved = container
                val level = spotifyBrowse.level(uri)
                val topBarTitle = spotifyTopBarTitle(resolved?.name)
                val optionMediaItem = resolved?.let { spotifyContainerOptionTarget(it) }
                LaunchedEffect(navController.currentDestination, topBarTitle, optionMediaItem) {
                    onSelectNav(Nav.SPOTIFY)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(optionMediaItem)
                }
                LaunchedEffect(resolved) {
                    val target = resolved ?: return@LaunchedEffect
                    if (level.items.isEmpty()) loadSpotifyContainer(target, true)
                }
                SpotifyScreen(
                    isSearchActive = isSearchActive,
                    query = query,
                    result = result,
                    keyboardController = keyboardController,
                    onSearchItemClicked = onSearchItemClicked,
                    onSearchItemLongClicked = onSearchItemLongClicked,
                    searchSpotify = searchSpotify,
                ) {
                    SpotifyLevelList(
                        level = level,
                        backStackEntry = backStackEntry,
                        endItemMargin = endItemMargin,
                        onOpenContainer = { navController.navigateToSpotifyContainer(it) },
                        onLoadMore = { resolved?.let { loadSpotifyContainer(it, false) } },
                        onDialogEvent = onDialogEvent,
                    )
                }
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
            composable("license") {
                BackHandler(enabled = onBackHandle != null) {
                    onBackHandle?.invoke()
                }
                val topBarTitle = stringResource(id = R.string.nav_license)
                LaunchedEffect(navController.currentDestination) {
                    onSelectNav(Nav.LICENSE)
                    onChangeTopBarTitle(topBarTitle)
                    onSetOptionMediaItem(null)
                }
                Licenses(endItemMargin = endItemMargin)
            }
        }
        QSnackbar(
            message = snackbarMessage,
            paths = snackbarPaths,
            progress = snackbarProgress,
            onCancelProgress = onCancelProgress
        )
    }
}

@Composable
private fun SpotifyScreen(
    isSearchActive: MutableState<Boolean>,
    query: MutableState<String>,
    result: MutableState<ImmutableList<SearchItem>>,
    keyboardController: SoftwareKeyboardController?,
    onSearchItemClicked: (item: SearchItem) -> Unit,
    onSearchItemLongClicked: (item: SearchItem) -> Unit,
    searchSpotify: suspend (query: String) -> List<SearchItem>,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QSearchBar(
            isSearchActive = isSearchActive,
            query = query,
            result = result,
            keyboardController = keyboardController,
            onSearchItemClicked = onSearchItemClicked,
            onSearchItemLongClicked = onSearchItemLongClicked,
            searchSpotify = searchSpotify,
        )
        content()
    }
}

@Composable
private fun SpotifyLevelList(
    level: SpotifyLevel,
    backStackEntry: NavBackStackEntry,
    endItemMargin: Dp,
    onOpenContainer: (container: SpotifyContainer) -> Unit,
    onLoadMore: () -> Unit,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollPosition = rememberScrollPosition(backStackEntry)
    ScrollPositionEffect(
        listState = listState,
        initialScrollPosition = scrollPosition.initialPosition,
        headerItemCount = 0,
        isItemLoaded = { it < level.items.size },
        onScrollPositionUpdated = scrollPosition::update,
    )
    SpotifyLevel(
        level = level,
        listState = listState,
        endItemMargin = endItemMargin,
        onOpenContainer = onOpenContainer,
        onLoadMore = onLoadMore,
        onDialogEvent = onDialogEvent,
    )
}

private fun NavHostController.navigateToSpotifyContainer(container: SpotifyContainer) {
    navigate(
        "spotify/container?uri=${container.uri.encodeUrlSafe()}&kind=${container.kind.name}"
    )
}
