package com.geckour.q.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dropbox.core.DbxHost
import com.dropbox.core.android.Auth
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.domain.model.LayoutType
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.service.DropboxMediaSyncJobService
import com.geckour.q.ui.compose.ColorBackground
import com.geckour.q.ui.compose.ColorBackgroundInverse
import com.geckour.q.ui.compose.ColorPrimaryDark
import com.geckour.q.ui.compose.ColorPrimaryDarkInverse
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.widget.player.PlayerSheetWidgetProvider
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.SyncProgressState
import com.geckour.q.util.SyncSizeAlertState
import com.geckour.q.util.dbxRequestConfig
import com.geckour.q.util.getActiveQAudioDeviceInfo
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getExtension
import com.geckour.q.util.getIsInNightMode
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.getShowLyric
import com.geckour.q.util.getTimeString
import com.geckour.q.util.parseLrc
import com.geckour.q.util.toLrcString
import com.geckour.q.worker.KEY_PROGRESS_FINISHED
import com.geckour.q.worker.KEY_PROGRESS_PROCESSED_FILES_SIZE
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_FRACTION
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_PATHS
import com.geckour.q.worker.KEY_PROGRESS_REMAINING_DURATION
import com.geckour.q.worker.KEY_PROGRESS_REMAINING_FILES
import com.geckour.q.worker.KEY_PROGRESS_SKIPPED_FILES
import com.geckour.q.worker.KEY_PROGRESS_TITLE
import com.geckour.q.worker.KEY_PROGRESS_TOTAL_FILES_SIZE
import com.geckour.q.worker.LocalMediaRetrieveWorker
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class MainActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }

    private val viewModel by viewModel<MainViewModel>()
    private var onCancelProgress: (() -> Unit)? = null

    private var attachLyricTargetTrackId = -1L
    private var lrcString: String? = null

    private val mainActions = object : MainActions {

        override fun onSelectNav(nav: Nav?) {
            viewModel.selectedNav.value = nav
        }

        override fun onTapBar() = viewModel.requestScrollToTop()

        override fun onToggleTheme() {
            viewModel.toggleNightMode()
            PlayerSheetWidgetProvider.requestUpdate(this@MainActivity)
        }

        override fun onChangeTopBarTitle(title: String) {
            viewModel.topBarTitle.value = title
        }

        override fun onSetOptionMediaItem(mediaItem: MediaItem?) {
            viewModel.appBarOptionMediaItem.value = mediaItem
        }

        override fun onToggleFavorite(mediaItem: MediaItem?): MediaItem? =
            viewModel.toggleFavorite(mediaItem)

        override fun onShowDialog(dialogState: DialogState?) = viewModel.showDialog(dialogState)

        override fun onDialogEvent(event: DialogEvent) = handleDialogEvent(event)

        override fun onRetrieveMedia(onlyAdded: Boolean) = retrieveMedia(onlyAdded)

        override fun onStartBilling() = viewModel.startBilling(this@MainActivity)

        override fun onDeleteSavedQueue(savedQueueId: Long) =
            viewModel.deleteSavedQueue(savedQueueId)

        override fun onTogglePlayPause() = viewModel.onTogglePlayPause()

        override fun onPrev() = viewModel.onPrev()

        override fun onNext() = viewModel.onNext()

        override fun onRewind() {
            viewModel.onRewind()
        }

        override fun onFastForward() {
            viewModel.onFF()
        }

        override fun resetPlaybackButton() =
            viewModel.onNewPlaybackButton(PlaybackButton.UNDEFINED)

        override fun onNewProgress(newProgress: Long) =
            viewModel.onNewSeekBarProgress(newProgress)

        override fun rotateRepeatMode() = viewModel.onClickRepeatButton()

        override fun shuffleQueue(actionType: ShuffleActionType?) = viewModel.onShuffle(actionType)

        override fun resetShuffleQueue() = viewModel.onResetShuffle()

        override fun moveToCurrentIndex() = viewModel.requestScrollToCurrent()

        override fun clearQueue() = viewModel.onClickClearQueueButton()

        override fun onToggleShowLyrics() = viewModel.toggleShowLyric()

        override fun onQueueMove(from: Int, to: Int) = viewModel.onQueueMove(from, to)

        override fun onChangeIndexRequested(index: Int) = viewModel.onChangeIndexRequested(index)

        override fun onRemoveTrackFromQueue(index: Int) = viewModel.onRemoveTrackFromQueue(index)
    }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            onStoragePermissionRequestResult?.invoke(it)
        }

    private var onStoragePermissionRequestResult: ((isGranted: Boolean) -> Unit)? = null

    private val requestPermissionWithoutResultHandling =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult

            try {
                contentResolver.query(uri, null, null, null, null, null).use { cursor ->
                    cursor ?: return@use
                    cursor.moveToFirst()
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index < 0) return@use
                    val fileName = cursor.getString(index)
                    val extensionName = fileName.getExtension()
                    if (extensionName == "txt") {
                        val lyricText = contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charset.forName("UTF-8"))
                        } ?: throw IllegalStateException("Cannot open lyric file.")
                        onLrcFileLoaded(
                            lyricText.split('\n').map { LyricLine(0, it) }
                        )
                        return@registerForActivityResult
                    } else if (extensionName != "lrc") {
                        throw IllegalStateException("The file type .$extensionName is not supported.")
                    }
                }

                val dir = File(cacheDir, "lrc")
                val file = File(dir, "sample.lrc")

                if (file.exists()) file.delete()
                if (dir.exists().not()) dir.mkdirs()

                contentResolver.openInputStream(uri)?.use {
                    file.writeBytes(it.readBytes())
                }
                val lyricLines = file.parseLrc()
                if (lyricLines.isEmpty()) throw IllegalStateException("The lyric is empty.")
                onLrcFileLoaded(lyricLines)
                file.delete()
            } catch (t: Throwable) {
                Timber.e(t)
                lifecycleScope.launch {
                    viewModel.emitSnackbarMessage(
                        getString(R.string.message_attach_lyric_failure)
                    )
                    delay(2000.milliseconds)
                    viewModel.emitSnackbarMessage(null)
                }
            }
        }

    private val putContent =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri ?: return@registerForActivityResult
            val lrcString = this.lrcString ?: return@registerForActivityResult

            try {
                contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                    FileOutputStream(parcelFileDescriptor.fileDescriptor).use {
                        it.write(lrcString.toByteArray())
                    }
                }
                this.lrcString = null
                lifecycleScope.launch {
                    viewModel.emitSnackbarMessage(
                        getString(R.string.message_export_lyric_success)
                    )
                    delay(2000.milliseconds)
                    viewModel.emitSnackbarMessage(null)
                }
            } catch (t: Throwable) {
                Timber.e(t)
                lifecycleScope.launch {
                    viewModel.emitSnackbarMessage(
                        getString(R.string.message_export_lyric_failure)
                    )
                    delay(2000.milliseconds)
                    viewModel.emitSnackbarMessage(null)
                }
            }
        }

    private val generalContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                super.onChange(selfChange, uri, flags)

                retrieveMedia(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionWithoutResultHandling.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val layoutTypeFlow = WindowInfoTracker.getOrCreate(this)
            .windowLayoutInfo(this)
            .flowWithLifecycle(this.lifecycle)
            .map {
                val bounds = windowManager.currentWindowMetrics.bounds
                val (windowHeight, windowWidth) = bounds.height().toFloat() to bounds.width()
                    .toFloat()
                val isSquareIshScreen = (windowHeight / windowWidth) in 0.75..1.33
                val isHorizontal =
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val existSpaceToSplit = windowWidth > 1500

                val foldingFeature = it.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()

                when {
                    foldingFeature == null -> {
                        if (existSpaceToSplit && (isSquareIshScreen || isHorizontal)) {
                            LayoutType.Twin(
                                hingePosition = Rect(0, 0, 0, 0),
                                orientation = FoldingFeature.Orientation.VERTICAL
                            )
                        } else {
                            LayoutType.Single
                        }
                    }

                    // Book style
                    (foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) ||
                            // Separated and portraits style
                            (foldingFeature.state == FoldingFeature.State.FLAT &&
                                    (foldingFeature.isSeparating &&
                                            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL)) ||
                            // Not separated and square-ish screen
                            (foldingFeature.state == FoldingFeature.State.FLAT && isSquareIshScreen) -> {
                        LayoutType.Twin(foldingFeature.bounds, foldingFeature.orientation)
                    }

                    else -> LayoutType.Single
                }
            }
            .stateIn(scope = lifecycleScope, started = SharingStarted.Eagerly, LayoutType.Single)

        setContent {
            val context = LocalContext.current
            val isInNightMode by context.getIsInNightMode()
                .collectAsState(initial = isSystemInDarkTheme())
            val isLoading by viewModel.loading.collectAsState()
            val navController = rememberNavController()
            val topBarTitle by viewModel.topBarTitle.collectAsState()
            val queue by viewModel.currentQueueFlow.collectAsState(initial = emptyList())
            val sourcePaths by viewModel.currentSourcePathsFlow.collectAsState()
            val currentIndex by viewModel.currentIndexFlow.collectAsState()
            val currentPlaybackPosition by viewModel.currentPlaybackPositionFlow.collectAsState()
            val currentBufferedPosition by viewModel.currentBufferedPositionFlow.collectAsState()
            val currentPlaybackInfo by viewModel.currentPlaybackInfoFlow.collectAsState()
            val currentRepeatMode by viewModel.currentRepeatModeFlow.collectAsState()
            val forceScrollToCurrent by viewModel.forceScrollToCurrent.collectAsState()
            val dialogState by viewModel.dialogState.collectAsState()
            val selectedNav by viewModel.selectedNav.collectAsState()
            val workInfoList by viewModel.workInfoListFlow
                .collectAsState(initial = emptyList())
            var progressMessage by remember { mutableStateOf<String?>(null) }
            var progressFraction by remember { mutableStateOf<Float?>(null) }
            var progressPaths by remember {
                mutableStateOf<ImmutableList<String>>(persistentListOf())
            }
            var finishedWorkIdSet by remember { mutableStateOf(emptySet<UUID>()) }
            val snackbarMessage by viewModel.snackbarMessageFlow.collectAsState()
            val equalizerParams by context.getEqualizerParams().collectAsState(initial = null)
            val scrollToTop by viewModel.scrollToTop.collectAsState()
            val showLyric by context.getShowLyric().collectAsState(initial = false)
            val layoutType by layoutTypeFlow.collectAsState()
            val appBarOptionMediaItem by viewModel.appBarOptionMediaItem.collectAsState()
            val isSearchActive = rememberSaveable { mutableStateOf(false) }
            val searchQuery = rememberSaveable { mutableStateOf("") }
            val isFavoriteOnly = rememberSaveable { mutableStateOf(false) }
            val activeQAudioDeviceInfo by getActiveQAudioDeviceInfo().collectAsState(initial = null)

            LaunchedEffect(isInNightMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = ColorPrimaryDark.toArgb(),
                        darkScrim = ColorPrimaryDarkInverse.toArgb(),
                        detectDarkMode = { isInNightMode }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = ColorBackground.toArgb(),
                        darkScrim = ColorBackgroundInverse.toArgb(),
                        detectDarkMode = { isInNightMode }
                    )
                )
            }

            val syncProgress by SyncProgressState.progress.collectAsState()
            val syncSizeAlert by SyncSizeAlertState.alert.collectAsState()

            LaunchedEffect(syncProgress) {
                val progress = syncProgress
                if (progress == null) {
                    if (workInfoList.none { it.state == WorkInfo.State.RUNNING }) {
                        progressMessage = null
                        progressPaths = persistentListOf()
                        progressFraction = null
                        onCancelProgress = null
                    }
                    return@LaunchedEffect
                }

                val remainingText = getString(
                    R.string.remaining,
                    progress.remainingFiles,
                    "${progress.processedFilesSize.toFloat().getReadableStringWithUnit()}B",
                    "${progress.totalFilesSize.toFloat().getReadableStringWithUnit()}B",
                    progress.skippedFiles,
                )
                val remainingDurationText =
                    if (progress.remainingDuration < 0) ""
                    else getString(
                        R.string.remaining_duration,
                        progress.remainingDuration.getTimeString(),
                    )

                progressMessage = listOf(progress.title, remainingText, remainingDurationText)
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                progressPaths = progress.paths.toImmutableList()
                progressFraction = progress.progressFraction
                onCancelProgress = { DropboxMediaSyncJobService.cancel(context) }
            }

            LaunchedEffect(
                workInfoList.map { it.progress },
                workInfoList.map { it.state }
            ) {
                launch(Dispatchers.IO) {
                    if (SyncProgressState.progress.value != null) return@launch

                    if (workInfoList.none { it.state == WorkInfo.State.RUNNING }) {
                        workInfoList.firstOrNull {
                            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                        }?.let {
                            progressMessage = getString(R.string.starting)
                            onCancelProgress = {
                                val workManager = WorkManager.getInstance(context)
                                it.tags.forEach {
                                    workManager.cancelAllWorkByTag(it)
                                }
                            }
                        } ?: run {
                            progressMessage = null
                            progressPaths = persistentListOf()
                            progressFraction = null
                            onCancelProgress = null
                        }
                        return@launch
                    }

                    workInfoList.forEach { workInfo ->
                        workInfo.progress.also { progress ->
                            val fraction = progress.getFloat(KEY_PROGRESS_PROGRESS_FRACTION, -1f)
                            if (fraction < 0) return@forEach

                            val title = progress.getString(KEY_PROGRESS_TITLE).orEmpty()

                            val paths = progress.getStringArray(KEY_PROGRESS_PROGRESS_PATHS)
                                ?.toList()
                                .orEmpty()
                                .toImmutableList()
                            val remainingFilesCount = progress.getInt(
                                KEY_PROGRESS_REMAINING_FILES,
                                -1
                            )
                            val totalFilesSize = progress.getLong(
                                KEY_PROGRESS_TOTAL_FILES_SIZE,
                                1
                            )
                            val processedFilesSize = progress.getLong(
                                KEY_PROGRESS_PROCESSED_FILES_SIZE,
                                0
                            )
                            val skippedFilesCount = progress.getInt(
                                KEY_PROGRESS_SKIPPED_FILES,
                                0
                            )
                            val remainingText =
                                if (remainingFilesCount < 0) ""
                                else getString(
                                    R.string.remaining,
                                    remainingFilesCount,
                                    "${processedFilesSize.toFloat().getReadableStringWithUnit()}B",
                                    "${totalFilesSize.toFloat().getReadableStringWithUnit()}B",
                                    skippedFilesCount,
                                )
                            val remainingDuration = progress.getLong(
                                KEY_PROGRESS_REMAINING_DURATION,
                                -1
                            )
                            val remainingDurationText =
                                if (remainingDuration < 0) ""
                                else getString(
                                    R.string.remaining_duration,
                                    remainingDuration.getTimeString(),
                                )

                            listOf(
                                title,
                                remainingText,
                                remainingDurationText
                            )
                                .filter { it.isNotEmpty() }
                                .joinToString("\n")
                                .let { message ->
                                    if (message.isEmpty()) return@let

                                    progressMessage = message
                                    progressPaths = paths
                                    progressFraction = fraction
                                    onCancelProgress = {
                                        val workManager = WorkManager.getInstance(context)
                                        workInfo.tags.forEach {
                                            workManager.cancelAllWorkByTag(it)
                                        }
                                    }
                                }
                        }
                        if (finishedWorkIdSet.contains(workInfo.id).not()
                            && (workInfo.outputData.getBoolean(KEY_PROGRESS_FINISHED, false) ||
                                    workInfo.state in listOf(
                                WorkInfo.State.SUCCEEDED,
                                WorkInfo.State.CANCELLED,
                                WorkInfo.State.FAILED
                            ))
                        ) {
                            finishedWorkIdSet += workInfo.id
                            if (workInfoList.all { it.state.isFinished }) {
                                progressMessage = null
                                progressPaths = persistentListOf()
                            }
                        }
                    }
                }
            }

            val uiState = MainUiState(
                player = PlayerUiState(
                    queue = queue.toImmutableList(),
                    sourcePaths = sourcePaths,
                    currentIndex = currentIndex,
                    currentPlaybackPosition = currentPlaybackPosition,
                    currentBufferedPosition = currentBufferedPosition,
                    currentPlaybackInfo = currentPlaybackInfo,
                    currentRepeatMode = currentRepeatMode,
                    isLoading = isLoading,
                    showLyric = showLyric,
                    forceScrollToCurrent = forceScrollToCurrent,
                ),
                library = LibraryUiState(
                    topBarTitle = topBarTitle,
                    appBarOptionMediaItem = appBarOptionMediaItem,
                    selectedNav = selectedNav,
                    equalizerParams = equalizerParams,
                    snackbarMessage = progressMessage ?: snackbarMessage,
                    snackbarPaths = progressPaths,
                    snackbarProgress = progressFraction,
                    onCancelProgress = onCancelProgress,
                    scrollToTop = scrollToTop,
                ),
                routeInfo = activeQAudioDeviceInfo,
                dialogState = dialogState,
                syncSizeAlert = syncSizeAlert,
            )

            QTheme(darkTheme = isInNightMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (layoutType) {
                        is LayoutType.Single -> {
                            SingleScreen(
                                navController = navController,
                                uiState = uiState,
                                isSearchActive = isSearchActive,
                                searchQuery = searchQuery,
                                isFavoriteOnly = isFavoriteOnly,
                                actions = mainActions,
                            )
                        }

                        is LayoutType.Twin -> {
                            TwinScreen(
                                navController = navController,
                                uiState = uiState,
                                isSearchActive = isSearchActive,
                                searchQuery = searchQuery,
                                isFavoriteOnly = isFavoriteOnly,
                                actions = mainActions,
                            )
                        }
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            viewModel.checkDBIsEmpty { retrieveMedia(false) }
        }

        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true,
                generalContentObserver
            )
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.initializeMediaController(this)
        retrievePendingDropboxMedia()
    }

    override fun onResume() {
        super.onResume()

        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            contentResolver.refresh(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                null,
                null
            )
        }

        if (viewModel.isDropboxAuthOngoing) {
            viewModel.isDropboxAuthOngoing = false
            lifecycleScope.launch {
                viewModel.storeDropboxApiToken {
                    lifecycleScope.launch {
                        onDropboxSyncFailure(it)
                    }
                }
                viewModel.showDialog(DialogState.Dropbox())
            }
        }

        viewModel.requestBillingInfoUpdate()

        viewModel.requestScrollToCurrent()
    }

    override fun onStop() {
        viewModel.releaseMediaController()

        super.onStop()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(generalContentObserver)

        super.onDestroy()
    }

    private fun retrieveMedia(onlyAdded: Boolean) {
        WorkManager.getInstance(this)
            .cancelAllWorkByTag(LocalMediaRetrieveWorker.TAG)
        when (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                enqueueLocalRetrieveWorker(onlyAdded)
            }

            else -> {
                onStoragePermissionRequestResult = {
                    if (it) {
                        enqueueLocalRetrieveWorker(onlyAdded)
                    } else {
                        onReadMediaDenied()
                    }
                }
                requestStoragePermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
    }

    private fun retrievePendingDropboxMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            DropboxMediaSyncJobService.resume(this@MainActivity)
        }
    }

    private fun retrieveDropboxMedia(rootPath: String, needDownloaded: Boolean) {
        DropboxMediaSyncJobService.schedule(this, rootPath, needDownloaded)
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

    private fun onReadMediaDenied() = Unit

    private fun handleDialogEvent(event: DialogEvent) {
        when (event) {
            DialogEvent.Dismiss -> viewModel.dismissDialog()

            is DialogEvent.NewQueue -> {
                viewModel.onNewQueue(
                    sourcePaths = event.sourcePaths,
                    actionType = event.actionType,
                    classType = event.classType,
                    needSorted = event.needSorted,
                )
            }

            is DialogEvent.GenerateQueue -> {
                viewModel.onGenerateQueue(event.track, event.actionType, event.classType)
            }

            is DialogEvent.DeleteTrack -> viewModel.deleteTrack(event.track)

            is DialogEvent.ExportLyric -> {
                lifecycleScope.launch {
                    lrcString = DB.getInstance(this@MainActivity)
                        .lyricDao()
                        .getLyricByTrackId(event.track.id)
                        ?.toLrcString() ?: return@launch
                    putContent.launch("${event.track.title}.lrc")
                }
            }

            is DialogEvent.AttachLyric -> {
                attachLyricTargetTrackId = event.trackId
                getContent.launch("*/*")
            }

            is DialogEvent.DetachLyric -> {
                lifecycleScope.launch {
                    DB.getInstance(this@MainActivity)
                        .lyricDao()
                        .deleteLyricByTrackId(event.trackId)
                    viewModel.emitSnackbarMessage(
                        getString(R.string.message_delete_lyric_complete)
                    )
                    delay(2000.milliseconds)
                    viewModel.emitSnackbarMessage(null)
                }
            }

            DialogEvent.StartDropboxAuth -> {
                viewModel.isDropboxAuthOngoing = true
                Auth.startOAuth2PKCE(
                    this,
                    BuildConfig.DROPBOX_APP_KEY,
                    dbxRequestConfig,
                    DbxHost.DEFAULT
                )
                viewModel.dismissDialog()
            }

            is DialogEvent.ShowDropboxFolder -> {
                viewModel.showDropboxFolderChooser(event.folder) {
                    lifecycleScope.launch {
                        onDropboxSyncFailure(it)
                    }
                }
            }

            is DialogEvent.StartDropboxSync -> {
                retrieveDropboxMedia(
                    event.rootFolderPath ?: MainViewModel.DROPBOX_PATH_ROOT,
                    event.needDownloaded
                )
            }

            is DialogEvent.StartDownload -> viewModel.downloadDropboxMedia(event.targets)

            is DialogEvent.StartInvalidateDownloaded -> viewModel.purgeDownloaded(event.targets)

            DialogEvent.EnablePauseOnCurrentTrackEnd -> viewModel.enablePauseOnCurrentTrackEnd()

            is DialogEvent.SaveQueue -> {
                viewModel.saveQueue(
                    title = event.title,
                    trackIds = event.trackIds,
                    onComplete = {
                        lifecycleScope.launch {
                            viewModel.emitSnackbarMessage(
                                getString(R.string.snackbar_message_save_queue_complete)
                            )
                            delay(2000.milliseconds)
                            viewModel.emitSnackbarMessage(null)
                        }
                    },
                )
            }

            is DialogEvent.ModifySavedQueue -> {
                viewModel.saveQueue(
                    savedQueueId = event.savedQueueId,
                    title = event.title,
                    trackIds = event.trackIds,
                )
            }

            is DialogEvent.DeleteSavedQueue -> viewModel.deleteSavedQueue(event.savedQueueId)

            is DialogEvent.RespondSyncSizeConfirmation -> {
                DropboxMediaSyncJobService.respondSizeConfirmation(event.approved)
            }

            DialogEvent.DismissSyncSizeExceeded -> SyncSizeAlertState.update(null)
        }
    }

    private fun onLrcFileLoaded(lyricLines: List<LyricLine>) {
        if (attachLyricTargetTrackId > 0) {
            lifecycleScope.launch {
                val db = DB.getInstance(this@MainActivity)
                val id = db.lyricDao().getLyricIdByTrackId(attachLyricTargetTrackId) ?: 0
                db.lyricDao()
                    .upsertLyric(
                        Lyric(id = id, trackId = attachLyricTargetTrackId, lines = lyricLines)
                    )
                viewModel.emitSnackbarMessage(
                    getString(R.string.message_attach_lyric_success)
                )
                delay(2000.milliseconds)
                viewModel.emitSnackbarMessage(null)
                attachLyricTargetTrackId = -1
            }
        } else {
            lifecycleScope.launch {
                viewModel.emitSnackbarMessage(
                    getString(R.string.message_attach_lyric_failure)
                )
                delay(2000.milliseconds)
                viewModel.emitSnackbarMessage(null)
            }
        }
    }

    private suspend fun onDropboxSyncFailure(throwable: Throwable) {
        viewModel.emitSnackbarMessage(
            getString(
                R.string.snackbar_message_dropbox_sync_failure,
                throwable.message,
            )
        )
        delay(2000.milliseconds)
        viewModel.emitSnackbarMessage(null)
    }
}

private val Resources.isNightMode
    get() =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
