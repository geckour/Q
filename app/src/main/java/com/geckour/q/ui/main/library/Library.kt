package com.geckour.q.ui.main.library

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.geckour.q.R
import com.geckour.q.core.model.MediaItem
import com.geckour.q.core.util.decodeUrlSafe
import com.geckour.q.core.util.encodeUrlSafe
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.domain.model.AllArtists
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.spotify.library.isInLibrary
import com.geckour.q.spotify.model.SpotifyContainer
import com.geckour.q.ui.component.QSnackbar
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.license.Licenses
import com.geckour.q.ui.main.dialog.DialogEvent
import com.geckour.q.ui.main.extra.Equalizer
import com.geckour.q.ui.main.extra.Pay
import com.geckour.q.ui.main.extra.Qzi
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
    val screenMetas = remember { mutableStateMapOf<String, ScreenMeta>() }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentScreenMeta = currentBackStackEntry?.id?.let { screenMetas[it] }
    LaunchedEffect(currentScreenMeta) {
        val screenMeta = currentScreenMeta ?: return@LaunchedEffect
        onSelectNav(screenMeta.nav)
        onChangeTopBarTitle(screenMeta.title)
        when (val target = screenMeta.optionTarget) {
            is OptionTarget.Item -> onSetOptionMediaItem(target.mediaItem)
            is OptionTarget.ArtistId -> onSetOptionArtist(target.artistId)
            is OptionTarget.AlbumId -> onSetOptionAlbum(target.albumId)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SharedTransitionLayout(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = "artists",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(NAV_TRANSITION_MILLIS)) },
                    exitTransition = { fadeOut(tween(NAV_TRANSITION_MILLIS)) },
                    popEnterTransition = { fadeIn(tween(NAV_TRANSITION_MILLIS)) },
                    popExitTransition = { fadeOut(tween(NAV_TRANSITION_MILLIS)) },
                    predictivePopEnterTransition = { fadeIn(tween(NAV_TRANSITION_MILLIS)) },
                    predictivePopExitTransition = { fadeOut(tween(NAV_TRANSITION_MILLIS)) },
                ) {
                    screen("artists") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.ARTIST, stringResource(id = R.string.nav_artist), OptionTarget.Item(AllArtists)),
                        )
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
                    screen(
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
                        val scrollPosition = rememberScrollPosition(backStackEntry)
                        var topBarTitle by remember { mutableStateOf<String?>(null) }
                        topBarTitle?.let {
                            RegisterScreenMeta(
                                screenMetas,
                                backStackEntry,
                                ScreenMeta(Nav.ALBUM, it, OptionTarget.ArtistId(artistId)),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .containerTransform(artistId.takeIf { it > 0 }?.let(::artistContainerKey))
                                .background(QTheme.colors.colorBackground)
                        ) {
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
                                changeTopBarTitle = { topBarTitle = it },
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
                    }
                    screen(
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
                        val genreName = backStackEntry.arguments?.getString("genreName")?.decodeUrlSafe()
                        val scrollPosition = rememberScrollPosition(backStackEntry)
                        var topBarTitle by remember { mutableStateOf<String?>(null) }
                        topBarTitle?.let {
                            RegisterScreenMeta(
                                screenMetas,
                                backStackEntry,
                                ScreenMeta(Nav.TRACK, it, OptionTarget.AlbumId(albumId)),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .containerTransform(
                                    albumId.takeIf { it > 0 }?.let(::albumContainerKey)
                                        ?: genreName?.let(::genreContainerKey)
                                )
                                .background(QTheme.colors.colorBackground)
                        ) {
                            Tracks(
                                endItemMargin = endItemMargin,
                                albumId = albumId,
                                genreName = genreName,
                                isSearchActive = isSearchActive,
                                isFavoriteOnly = isFavoriteOnly,
                                query = query,
                                result = result,
                                keyboardController = keyboardController,
                                changeTopBarTitle = { topBarTitle = it },
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
                    }
                    screen("genres") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.GENRE, stringResource(id = R.string.nav_genre), OptionTarget.Item(null)),
                        )
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
                    screen("saved_queue") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.SAVED_QUEUE, stringResource(id = R.string.nav_saved_queue), OptionTarget.Item(null)),
                        )
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
                    screen("history") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.HISTORY, stringResource(id = R.string.nav_history), OptionTarget.Item(null)),
                        )
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
                    screen("spotify") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.SPOTIFY, spotifyTopBarTitle(section = null), OptionTarget.Item(null)),
                        )
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

                            SpotifySourceMenu(
                                endItemMargin = endItemMargin,
                                onSelectSource = { source ->
                                    navController.navigate("spotify/source?type=${source.name}")
                                },
                                onDialogEvent = onDialogEvent,
                            )
                        }
                    }
                    screen(
                        "spotify/source?type={type}",
                        arguments = listOf(navArgument("type") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val source = SpotifyBrowseSource.valueOf(
                            backStackEntry.arguments?.getString("type").orEmpty()
                        )
                        val level = spotifyBrowse.level(source.levelKey)
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(
                                Nav.SPOTIFY,
                                spotifyTopBarTitle(spotifySourceTitle(source)),
                                OptionTarget.Item(spotifySourceOptionTarget(source, isSpotifyFlattened)),
                            ),
                        )
                        LaunchedEffect(source) {
                            if (level.items.isEmpty() || source == SpotifyBrowseSource.LIBRARY) {
                                loadSpotifySource(source, true)
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
                            SpotifyLevelList(
                                level = level,
                                backStackEntry = backStackEntry,
                                endItemMargin = endItemMargin,
                                showsRemoveFromLibrary = source == SpotifyBrowseSource.LIBRARY,
                                onOpenContainer = { navController.navigateToSpotifyContainer(it) },
                                onLoadMore = { loadSpotifySource(source, false) },
                                onDialogEvent = onDialogEvent,
                            )
                        }
                    }
                    screen(
                        "spotify/container?uri={uri}&kind={kind}",
                        arguments = listOf(
                            navArgument("uri") { type = NavType.StringType },
                            navArgument("kind") { type = NavType.StringType },
                        ),
                    ) { backStackEntry ->
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
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(
                                Nav.SPOTIFY,
                                spotifyTopBarTitle(resolved?.name),
                                OptionTarget.Item(resolved?.let { spotifyContainerOptionTarget(it) }),
                            ),
                        )
                        LaunchedEffect(resolved) {
                            val target = resolved ?: return@LaunchedEffect
                            if (level.items.isEmpty() || target.kind.isInLibrary) {
                                loadSpotifyContainer(target, true)
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
                            SpotifyLevelList(
                                level = level,
                                backStackEntry = backStackEntry,
                                endItemMargin = endItemMargin,
                                showsRemoveFromLibrary = kind.isInLibrary,
                                onOpenContainer = { navController.navigateToSpotifyContainer(it) },
                                onLoadMore = { resolved?.let { loadSpotifyContainer(it, false) } },
                                onDialogEvent = onDialogEvent,
                            )
                        }
                    }
                    screen("qzi") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(null, stringResource(id = R.string.nav_fortune), OptionTarget.Item(null)),
                        )
                        Qzi(
                            onClick = { onSelectTrack(it.toUiTrack()) }
                        )
                    }
                    screen("pay") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.PAY, stringResource(id = R.string.nav_pay), OptionTarget.Item(null)),
                        )
                        Pay(onStartBilling = onStartBilling)
                    }
                    screen("equalizer") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.EQUALIZER, stringResource(id = R.string.nav_equalizer), OptionTarget.Item(null)),
                        )
                        Equalizer(routeInfo = routeInfo)
                    }
                    screen("license") { backStackEntry ->
                        RegisterScreenMeta(
                            screenMetas,
                            backStackEntry,
                            ScreenMeta(Nav.LICENSE, stringResource(id = R.string.nav_license), OptionTarget.Item(null)),
                        )
                        Licenses(endItemMargin = endItemMargin)
                    }
                }
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
    showsRemoveFromLibrary: Boolean,
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
        showsRemoveFromLibrary = showsRemoveFromLibrary,
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

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments) { backStackEntry ->
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            content(backStackEntry)
        }
    }
}
