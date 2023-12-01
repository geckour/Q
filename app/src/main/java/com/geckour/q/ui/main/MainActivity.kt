package com.geckour.q.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dropbox.core.DbxHost
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.MainViewModel.Companion.DROPBOX_PATH_ROOT
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.dbxRequestConfig
import com.geckour.q.util.dropboxCredential
import com.geckour.q.util.getIsNightMode
import com.geckour.q.util.getTimeString
import com.geckour.q.util.setIsNightMode
import com.geckour.q.util.toDomainTrack
import com.geckour.q.worker.DropboxMediaRetrieveWorker
import com.geckour.q.worker.KEY_SYNCING_FINISHED
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_DENOMINATOR
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_NUMERATOR
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_PATH
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_TOTAL_FILES
import com.geckour.q.worker.KEY_SYNCING_REMAINING
import com.geckour.q.worker.LocalMediaRetrieveWorker
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import permissions.dispatcher.ktx.constructPermissionsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    companion object {

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }

    private val viewModel by viewModel<MainViewModel>()
    private val sharedPreferences by inject<SharedPreferences>()

//    private val onNavigationItemSelected: (MenuItem) -> Boolean = {
//        when (it.itemId) {
//            R.id.nav_sync -> {
//                FirebaseAnalytics.getInstance(this)
//                    .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
//                        putString(
//                            FirebaseAnalytics.Param.ITEM_NAME, "Invoked force sync"
//                        )
//                    })
//
//                retrieveMedia(false)
//                null
//            }
//        }
//        true
//    }

    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            val isNightMode by context.getIsNightMode()
                .collectAsState(initial = isSystemInDarkTheme())
            val isLoading by viewModel.loading.collectAsState()
            val navController = rememberNavController()
            val scaffoldState = rememberBottomSheetScaffoldState()
            var topBarTitle by remember { mutableStateOf("") }
            val queue by viewModel.currentQueueFlow.collectAsState()
            val currentIndex by viewModel.currentIndexFlow.collectAsState()
            val currentPlaybackPosition by viewModel.currentPlaybackPositionFlow.collectAsState()
            val currentPlaybackInfo by viewModel.currentPlaybackInfoFlow.collectAsState()
            val currentRepeatMode by viewModel.currentRepeatModeFlow.collectAsState()
            var forceScrollToCurrent by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var showTrackDialog by remember { mutableStateOf(false) }
            var showAlbumDialog by remember { mutableStateOf(false) }
            var showArtistDialog by remember { mutableStateOf(false) }
            var showDropboxDialog by remember { mutableStateOf(false) }
            var selectedTrack by remember { mutableStateOf<DomainTrack?>(null) }
            var selectedAlbum by remember { mutableStateOf<JoinedAlbum?>(null) }
            var selectedArtist by remember { mutableStateOf<Artist?>(null) }
            var selectedDrawerItemId by remember { mutableIntStateOf(R.id.nav_artist) }
            val mediaRetrieveWorkInfoList by viewModel.mediaRetrieveWorkInfoListFlow
                .collectAsState(initial = emptyList())
            var progressMessage by remember { mutableStateOf<String?>(null) }
            var finishedWorkIdSet by remember { mutableStateOf(emptySet<UUID>()) }

            val bottomSheetHeightAngle = remember { Animatable(0f) }
            LaunchedEffect(queue) {
                if (queue.isNotEmpty()) {
                    bottomSheetHeightAngle.animateTo(
                        bottomSheetHeightAngle.value + Math.PI.toFloat(),
                        animationSpec = tween(400),
                    )
                }
            }

            LaunchedEffect(
                mediaRetrieveWorkInfoList.map { it.progress },
                mediaRetrieveWorkInfoList.map { it.state }) {
                if (mediaRetrieveWorkInfoList.none { it.state == WorkInfo.State.RUNNING }) {
                    viewModel.forceLoad.value = Unit
                    progressMessage = null
                    return@LaunchedEffect
                }

                mediaRetrieveWorkInfoList.forEach { workInfo ->
                    Timber.d("qgeck work id: ${workInfo.id}, state: ${workInfo.state}")
                    workInfo.progress.also { progress ->
                        progress.getInt(KEY_SYNCING_PROGRESS_NUMERATOR, -1).let { numerator ->
                            if (numerator < 0) return@let

                            val denominator = progress.getInt(KEY_SYNCING_PROGRESS_DENOMINATOR, -1)
                            val totalFilesCount =
                                progress.getInt(KEY_SYNCING_PROGRESS_TOTAL_FILES, -1)
                            val path = progress.getString(KEY_SYNCING_PROGRESS_PATH).orEmpty()
                            val remaining = progress.getLong(KEY_SYNCING_REMAINING, -1)

                            val progressText =
                                if (denominator < 0 || totalFilesCount < 0) ""
                                else getString(
                                    R.string.progress_sync,
                                    numerator,
                                    denominator,
                                    totalFilesCount
                                )
                            val remainingText =
                                if (remaining > -1) getString(
                                    R.string.remaining,
                                    remaining.getTimeString()
                                )
                                else ""

                            if (progressText.isNotEmpty() || remainingText.isNotEmpty() || path.isNotEmpty()) {
                                progressMessage =
                                    "${getString(R.string.syncing)}\n$progressText $remainingText\n$path"
                            }
                        }
                    }
                    if (finishedWorkIdSet.contains(workInfo.id)
                            .not() && workInfo.outputData.getBoolean(KEY_SYNCING_FINISHED, false)
                    ) {
                        finishedWorkIdSet += workInfo.id
                        viewModel.forceLoad.value = Unit
                        progressMessage = null
                    }
                }
            }

            QTheme(darkTheme = isNightMode) {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    drawerContent = {
                        Column(
                            modifier = Modifier
                                .background(color = QTheme.colors.colorBackground)
                                .fillMaxSize()
                        ) {
                            BackHandler(scaffoldState.drawerState.isOpen) {
                                coroutineScope.launch { scaffoldState.drawerState.close() }
                            }

                            DrawerHeader()
                            DrawerSectionHeader(title = stringResource(id = R.string.nav_category_library))
                            DrawerItem(
                                iconResId = R.drawable.ic_artist,
                                title = stringResource(id = R.string.nav_artist),
                                isSelected = selectedDrawerItemId == R.id.nav_artist,
                                onClick = {
                                    navController.navigate("artists")
                                    selectedDrawerItemId = R.id.nav_artist
                                    coroutineScope.launch { scaffoldState.drawerState.close() }
                                }
                            )
                            DrawerItem(
                                iconResId = R.drawable.ic_album,
                                title = stringResource(id = R.string.nav_album),
                                isSelected = selectedDrawerItemId == R.id.nav_album,
                                onClick = {
                                    navController.navigate("albums")
                                    selectedDrawerItemId = R.id.nav_album
                                    coroutineScope.launch { scaffoldState.drawerState.close() }
                                }
                            )
                            DrawerItem(
                                iconResId = R.drawable.ic_track,
                                title = stringResource(id = R.string.nav_track),
                                isSelected = selectedDrawerItemId == R.id.nav_track,
                                onClick = {
                                    navController.navigate("tracks")
                                    selectedDrawerItemId = R.id.nav_track
                                    coroutineScope.launch { scaffoldState.drawerState.close() }
                                }
                            )
                            DrawerItem(
                                iconResId = R.drawable.ic_genre,
                                title = stringResource(id = R.string.nav_genre),
                                isSelected = selectedDrawerItemId == R.id.nav_genre,
                                onClick = {
                                    navController.navigate("genres")
                                    selectedDrawerItemId = R.id.nav_genre
                                    coroutineScope.launch { scaffoldState.drawerState.close() }
                                }
                            )
                            Divider(color = QTheme.colors.colorPrimaryDark)
                            DrawerSectionHeader(title = stringResource(id = R.string.nav_category_others))
                            DrawerItem(
                                iconResId = R.drawable.ic_dropbox,
                                title = stringResource(id = R.string.nav_dropbox_sync),
                                isSelected = selectedDrawerItemId == R.id.nav_dropbox_sync,
                                onClick = {
                                    val credential = sharedPreferences.dropboxCredential
                                    if (credential.isNullOrBlank()) {
                                        viewModel.isDropboxAuthOngoing = true
                                        Auth.startOAuth2PKCE(
                                            context,
                                            BuildConfig.DROPBOX_APP_KEY,
                                            dbxRequestConfig,
                                            DbxHost.DEFAULT
                                        )
                                    } else {
                                        viewModel.showDropboxFolderChooser()
                                        showDropboxDialog = true
                                    }
                                    selectedDrawerItemId = R.id.nav_dropbox_sync
                                    coroutineScope.launch { scaffoldState.drawerState.close() }
                                }
                            )
                        }
                    },
                    drawerElevation = 8.dp,
                    topBar = {
                        QTopBar(
                            title = topBarTitle,
                            scaffoldState = scaffoldState,
                            onToggleTheme = {
                                coroutineScope.launch {
                                    context.setIsNightMode(isNightMode.not())
                                }
                            }
                        )
                    },
                    backgroundColor = QTheme.colors.colorBackground,
                    sheetBackgroundColor = QTheme.colors.colorBackgroundBottomSheet,
                    sheetPeekHeight = (144 + sin(bottomSheetHeightAngle.value) * 20).dp,
                    sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    sheetContent = {
                        BackHandler(scaffoldState.bottomSheetState.isExpanded && scaffoldState.drawerState.isClosed) {
                            coroutineScope.launch { scaffoldState.bottomSheetState.collapse() }
                        }
                        Controller(
                            currentTrack = queue.getOrNull(currentIndex),
                            progress = currentPlaybackPosition,
                            queueTotalDuration = queue.sumOf { it.duration },
                            queueRemainingDuration = queue.drop(currentIndex + 1)
                                .sumOf { it.duration }
                                    + (queue.getOrNull(currentIndex)?.duration ?: 0)
                                    - currentPlaybackPosition,
                            playbackInfo = currentPlaybackInfo,
                            repeatMode = currentRepeatMode,
                            isLoading = isLoading.first,
                            onTogglePlayPause = {
                                viewModel.onPlayOrPause(
                                    currentPlaybackInfo.first && currentPlaybackInfo.second == Player.STATE_READY
                                )
                            },
                            onPrev = viewModel::onPrev,
                            onNext = viewModel::onNext,
                            onRewind = viewModel::onRewind,
                            onFastForward = viewModel::onFF,
                            resetPlaybackButton = {
                                viewModel.onNewPlaybackButton(PlaybackButton.UNDEFINED)
                            },
                            onNewProgress = viewModel::onNewSeekBarProgress,
                            rotateRepeatMode = viewModel::onClickRepeatButton,
                            shuffleQueue = viewModel::onClickShuffleButton,
                            moveToCurrentIndex = {
                                forceScrollToCurrent = System.currentTimeMillis()
                            },
                            clearQueue = viewModel::onClickClearQueueButton
                        )
                        Queue(
                            domainTracks = queue,
                            currentIndex = currentIndex,
                            scrollTo = currentIndex.coerceAtLeast(0),
                            forceScrollToCurrent = forceScrollToCurrent,
                            isQueueVisible = scaffoldState.bottomSheetState.isExpanded,
                            onQueueMove = viewModel::onQueueMove,
                            onChangeRequestedTrackInQueue = viewModel::onChangeRequestedTrackInQueue,
                            onRemoveTrackFromQueue = viewModel::onRemoveTrackFromQueue,
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues)
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
                                    selectedDrawerItemId = R.id.nav_artist
                                    topBarTitle = stringResource(id = R.string.nav_artist)
                                    Artists(
                                        navController = navController, onSelectArtist = {
                                            selectedArtist = it
                                            showArtistDialog = true
                                        }
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
                                    selectedDrawerItemId = R.id.nav_album
                                    Albums(
                                        navController = navController,
                                        artistId = backStackEntry.arguments?.getLong("artistId")
                                            ?: -1,
                                        changeTopBarTitle = {
                                            topBarTitle = it
                                        },
                                        onSelectAlbum = {
                                            selectedAlbum = it
                                            showAlbumDialog = true
                                        }
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
                                    selectedDrawerItemId = R.id.nav_track
                                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: -1
                                    val genreName = backStackEntry.arguments?.getString("genreName")
                                    Tracks(
                                        albumId = albumId,
                                        genreName = genreName,
                                        changeTopBarTitle = {
                                            topBarTitle = it
                                        },
                                        onTrackSelected = {
                                            selectedTrack = it
                                            showTrackDialog = true
                                        }
                                    )
                                }
                                composable("genres") {
                                    topBarTitle = stringResource(id = R.string.nav_genre)
                                    selectedDrawerItemId = R.id.nav_genre
                                    Genres(navController = navController)
                                }
                            }
                            if (showTrackDialog) {
                                selectedTrack?.let { domainTrack ->
                                    Dialog(onDismissRequest = { showTrackDialog = false }) {
                                        Card(backgroundColor = QTheme.colors.colorBackground) {
                                            Column {
                                                DialogListItem(
                                                    onClick = {
                                                        viewModel.onNewQueue(
                                                            listOf(domainTrack),
                                                            actionType = InsertActionType.NEXT,
                                                            classType = OrientedClassType.TRACK
                                                        )
                                                        showTrackDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_next),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        viewModel.onNewQueue(
                                                            listOf(domainTrack),
                                                            actionType = InsertActionType.LAST,
                                                            classType = OrientedClassType.TRACK
                                                        )
                                                        showTrackDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_last),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        viewModel.onNewQueue(
                                                            listOf(domainTrack),
                                                            actionType = InsertActionType.OVERRIDE,
                                                            classType = OrientedClassType.TRACK
                                                        )
                                                        showTrackDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_override_all),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        navController.navigate("albums?artistId=${domainTrack.artist.id}")
                                                        showTrackDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_transition_to_artist),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        navController.navigate("tracks?albumId=${domainTrack.album.id}")
                                                        showTrackDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_transition_to_album),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        viewModel.deleteTrack(domainTrack)
                                                        showTrackDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_delete_from_device),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (showAlbumDialog) {
                                selectedAlbum?.let { joinedAlbum ->
                                    Dialog(onDismissRequest = { showAlbumDialog = false }) {
                                        Card(backgroundColor = QTheme.colors.colorBackground) {
                                            Column {
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByAlbum(joinedAlbum.album.id)
                                                                    .map { it.toDomainTrack() }
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.NEXT,
                                                                classType = OrientedClassType.ALBUM
                                                            )
                                                            showAlbumDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_next),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByAlbum(joinedAlbum.album.id)
                                                                    .map { it.toDomainTrack() }
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.LAST,
                                                                classType = OrientedClassType.ALBUM
                                                            )
                                                            showAlbumDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_last),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByAlbum(joinedAlbum.album.id)
                                                                    .map { it.toDomainTrack() }
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.OVERRIDE,
                                                                classType = OrientedClassType.ALBUM
                                                            )
                                                            showAlbumDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_override_all),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByAlbum(joinedAlbum.album.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                                                classType = OrientedClassType.ALBUM
                                                            )
                                                            showAlbumDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_next),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByAlbum(joinedAlbum.album.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_SIMPLE_LAST,
                                                                classType = OrientedClassType.ALBUM
                                                            )
                                                            showAlbumDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_last),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByAlbum(joinedAlbum.album.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                                                classType = OrientedClassType.ALBUM
                                                            )
                                                            showAlbumDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_override_all_simple_shuffle),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            DB.getInstance(context).trackDao()
                                                                .getAllByAlbum(joinedAlbum.album.id)
                                                                .forEach {
                                                                    viewModel.deleteTrack(it.toDomainTrack())
                                                                    showAlbumDialog = false
                                                                }
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_delete_from_device),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (showArtistDialog) {
                                selectedArtist?.let { artist ->
                                    Dialog(onDismissRequest = { showArtistDialog = false }) {
                                        Card(backgroundColor = QTheme.colors.colorBackground) {
                                            Column {
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.NEXT,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_next),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.LAST,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_last),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.OVERRIDE,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_override_all),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_NEXT,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_albums_insert_all_shuffle_next),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_LAST,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_albums_insert_all_shuffle_last),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_OVERRIDE,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_albums_override_all_shuffle),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_SIMPLE_NEXT,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_next),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_SIMPLE_LAST,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_insert_all_simple_shuffle_last),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val tracks =
                                                                DB.getInstance(context).trackDao()
                                                                    .getAllByArtist(artist.id)
                                                                    .map { it.toDomainTrack() }
                                                                    .shuffled()
                                                            viewModel.onNewQueue(
                                                                tracks,
                                                                actionType = InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
                                                                classType = OrientedClassType.ARTIST
                                                            )
                                                            showArtistDialog = false
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_override_all_simple_shuffle),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                DialogListItem(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            DB.getInstance(context).trackDao()
                                                                .getAllByArtist(artist.id)
                                                                .forEach {
                                                                    viewModel.deleteTrack(it.toDomainTrack())
                                                                    showArtistDialog = false
                                                                }
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(id = R.string.menu_delete_from_device),
                                                        fontSize = 14.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (showDropboxDialog) {
                                val currentDropboxItemList by viewModel.dropboxItemList.collectAsState(
                                    initial = "" to emptyList()
                                )
                                var selectedHistory by remember { mutableStateOf(emptyList<FolderMetadata>()) }
                                Dialog(onDismissRequest = { showDropboxDialog = false }) {
                                    Card(
                                        backgroundColor = QTheme.colors.colorBackground,
                                        modifier = Modifier.heightIn(max = 800.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 8.dp
                                            )
                                        ) {
                                            Text(
                                                text = currentDropboxItemList.first,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = QTheme.colors.colorAccent
                                            )
                                            LazyColumn(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            ) {
                                                items(currentDropboxItemList.second) {
                                                    Text(
                                                        text = it.name,
                                                        fontSize = 20.sp,
                                                        color = QTheme.colors.colorTextPrimary,
                                                        modifier = Modifier
                                                            .clickable {
                                                                selectedHistory += it
                                                                viewModel.showDropboxFolderChooser(
                                                                    it
                                                                )
                                                            }
                                                            .padding(
                                                                horizontal = 8.dp,
                                                                vertical = 12.dp
                                                            )
                                                            .fillMaxWidth()
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (selectedHistory.isNotEmpty()) {
                                                    TextButton(
                                                        onClick = {
                                                            selectedHistory =
                                                                selectedHistory.dropLast(1)
                                                            viewModel.showDropboxFolderChooser(
                                                                selectedHistory.lastOrNull()
                                                            )
                                                        }
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.dialog_prev),
                                                            fontSize = 16.sp,
                                                            color = QTheme.colors.colorTextPrimary
                                                        )
                                                    }
                                                }
                                                Spacer(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                )
                                                TextButton(
                                                    onClick = {
                                                        viewModel.clearDropboxItemList()
                                                        showDropboxDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.dialog_ng),
                                                        fontSize = 16.sp,
                                                        color = QTheme.colors.colorTextPrimary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                TextButton(
                                                    onClick = {
                                                        retrieveDropboxMedia(
                                                            selectedHistory.lastOrNull()?.pathLower
                                                                ?: DROPBOX_PATH_ROOT
                                                        )
                                                        showDropboxDialog = false
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.dialog_ok),
                                                        fontSize = 16.sp,
                                                        color = QTheme.colors.colorAccent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(visible = progressMessage != null) {
                                Text(
                                    text = progressMessage.orEmpty(),
                                    modifier = Modifier
                                        .background(color = QTheme.colors.colorBackgroundProgress)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 16.sp,
                                    color = QTheme.colors.colorTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            viewModel.checkDBIsEmpty { retrieveMedia(false) }
            retrieveMedia(true)
        }
    }

    override fun onResume() {
        super.onResume()

        if (viewModel.isDropboxAuthOngoing) {
            viewModel.isDropboxAuthOngoing = false
            onAuthDropboxCompleted()
        }
    }

    private fun retrieveMedia(onlyAdded: Boolean) {
        WorkManager.getInstance(this)
            .cancelAllWorkByTag(LocalMediaRetrieveWorker.TAG)
        if (Build.VERSION.SDK_INT < 33) {
            constructPermissionsRequest(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                onPermissionDenied = ::onReadExternalStorageDenied
            ) {
                enqueueLocalRetrieveWorker(onlyAdded)
            }.launch()
        } else {
            enqueueLocalRetrieveWorker(onlyAdded)
        }
    }

    private fun retrieveDropboxMedia(rootPath: String) {
        WorkManager.getInstance(this)
            .cancelAllWorkByTag(DropboxMediaRetrieveWorker.TAG)
        if (Build.VERSION.SDK_INT < 33) {
            constructPermissionsRequest(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                onPermissionDenied = ::onReadExternalStorageDenied
            ) {
                enqueueDropboxRetrieveWorker(rootPath)
            }.launch()
        } else {
            enqueueDropboxRetrieveWorker(rootPath)
        }
    }

    private fun enqueueLocalRetrieveWorker(onlyAdded: Boolean) {
        viewModel.workManager.beginUniqueWork(
            MEDIA_RETRIEVE_WORKER_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LocalMediaRetrieveWorker>()
                .setInputData(
                    Data.Builder()
                        .putBoolean(LocalMediaRetrieveWorker.KEY_ONLY_ADDED, onlyAdded)
                        .build()
                )
                .addTag(LocalMediaRetrieveWorker.TAG)
                .build()
        ).enqueue()
    }

    private fun enqueueDropboxRetrieveWorker(rootPath: String) {
        viewModel.workManager.beginUniqueWork(
            MEDIA_RETRIEVE_WORKER_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DropboxMediaRetrieveWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(DropboxMediaRetrieveWorker.KEY_ROOT_PATH, rootPath)
                        .build()
                )
                .addTag(DropboxMediaRetrieveWorker.TAG)
                .build()
        ).enqueue()
    }

    private fun onReadExternalStorageDenied() {
        retrieveMedia(false)
    }

//    private fun observeEvents() {
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.dropboxItemList.collect {
//                    (dropboxChooserDialog ?: run {
//                        DropboxChooserDialog(this@MainActivity, onClickItem = { metadata ->
//                            viewModel.showDropboxFolderChooser(metadata)
//                        }, onPrev = { metadata ->
//                            viewModel.showDropboxFolderChooser(metadata)
//                        }, onChoose = { path ->
//                            retrieveDropboxMedia(path)
//                        }).apply { dropboxChooserDialog = this }
//                    }).show(it.first, it.second)
//                }
//            }
//        }
//
//        viewModel.mediaRetrieveWorkInfoList.observe(this) { workInfoList ->
//            if (workInfoList.none { it.state == WorkInfo.State.RUNNING }) {
//                onCancelSync()
//                return@observe
//            }
//
//            workInfoList.forEach { workInfo ->
//                workInfo.progress.also {
//                    it.getInt(KEY_SYNCING_PROGRESS_NUMERATOR, -1).let { numerator ->
//                        if (numerator < 0) return@let
//
//                        val denominator = it.getInt(KEY_SYNCING_PROGRESS_DENOMINATOR, -1)
//                        val totalFilesCount = it.getInt(KEY_SYNCING_PROGRESS_TOTAL_FILES, -1)
//                        val path = it.getString(KEY_SYNCING_PROGRESS_PATH)
//                        val remaining = it.getLong(KEY_SYNCING_REMAINING, -1)
//                        setLockingIndicator(true, null)
//                        binding.indicatorLocking.progressSync.text =
//                            if (denominator < 0 || totalFilesCount < 0) null
//                            else getString(
//                                R.string.progress_sync, numerator, denominator, totalFilesCount
//                            )
//                        binding.indicatorLocking.progressPath.text = path
//                        binding.indicatorLocking.remaining.text = if (remaining > -1) getString(
//                            R.string.remaining, remaining.getTimeString()
//                        )
//                        else ""
//                    }
//                }
//                if (workInfo.outputData.getBoolean(KEY_SYNCING_FINISHED, false)) {
//                    onCancelSync()
//                }
//            }
//        }
//    }

    private fun onAuthDropboxCompleted() {
        viewModel.storeDropboxApiToken()
    }
}
