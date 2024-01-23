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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
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
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.LayoutType
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.compose.ColorBackground
import com.geckour.q.ui.compose.ColorBackgroundInverse
import com.geckour.q.ui.compose.ColorPrimaryDark
import com.geckour.q.ui.compose.ColorPrimaryDarkInverse
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.dbxRequestConfig
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getExtension
import com.geckour.q.util.getHasAlreadyShownDropboxSyncAlert
import com.geckour.q.util.getIsNightMode
import com.geckour.q.util.getNumberWithUnitPrefix
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isFavoriteToggled
import com.geckour.q.util.parseLrc
import com.geckour.q.util.setIsNightMode
import com.geckour.q.util.toLrcString
import com.geckour.q.worker.DROPBOX_DOWNLOAD_WORKER_NAME
import com.geckour.q.worker.DropboxDownloadWorker
import com.geckour.q.worker.DropboxMediaRetrieveWorker
import com.geckour.q.worker.KEY_PROGRESS_FINISHED
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_DENOMINATOR
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_NUMERATOR
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_PATH
import com.geckour.q.worker.KEY_PROGRESS_PROGRESS_TOTAL_FILES
import com.geckour.q.worker.KEY_PROGRESS_REMAINING
import com.geckour.q.worker.KEY_PROGRESS_REMAINING_FILES_SIZE
import com.geckour.q.worker.KEY_PROGRESS_TITLE
import com.geckour.q.worker.LocalMediaRetrieveWorker
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
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

class MainActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }

    private val viewModel by viewModel<MainViewModel>()
    private var onAuthDropboxCompleted: (() -> Unit)? = null
    private var onCancelProgress: (() -> Unit)? = null

    private var onLrcFileLoaded: ((lyricLines: List<LyricLine>) -> Unit)? = null
    private var lrcString: String? = null

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStoragePermissionRequestResult?.invoke(it) }

    private var onStoragePermissionRequestResult: ((isGranted: Boolean) -> Unit)? = null

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
                        onLrcFileLoaded?.invoke(
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
                onLrcFileLoaded?.invoke(lyricLines)
                file.delete()
            } catch (t: Throwable) {
                Timber.e(t)
                lifecycleScope.launch {
                    viewModel.emitSnackBarMessage(
                        getString(R.string.message_attach_lyric_failure)
                    )
                    delay(2000)
                    viewModel.emitSnackBarMessage(null)
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
                    viewModel.emitSnackBarMessage(
                        getString(R.string.message_export_lyric_success)
                    )
                    delay(2000)
                    viewModel.emitSnackBarMessage(null)
                }
            } catch (t: Throwable) {
                Timber.e(t)
                lifecycleScope.launch {
                    viewModel.emitSnackBarMessage(
                        getString(R.string.message_export_lyric_failure)
                    )
                    delay(2000)
                    viewModel.emitSnackBarMessage(null)
                }
            }
        }

    private val generalContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                super.onChange(selfChange, uri, flags)

                Timber.d("qgeck change occurred uri: $uri, flags: $flags")

                retrieveMedia(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutTypeFlow = WindowInfoTracker.getOrCreate(this)
            .windowLayoutInfo(this)
            .flowWithLifecycle(this.lifecycle)
            .map {
                val (windowHeight, windowWidth) = if (Build.VERSION.SDK_INT < 30) {
                    val metrics = DisplayMetrics().apply {
                        windowManager.defaultDisplay.getMetrics(this)
                    }
                    metrics.heightPixels.toFloat() to metrics.widthPixels.toFloat()
                } else {
                    val bounds = windowManager.currentWindowMetrics.bounds
                    bounds.height().toFloat() to bounds.width().toFloat()
                }
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
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            val isNightMode by context.getIsNightMode()
                .collectAsState(initial = isSystemInDarkTheme())
            val isLoading by viewModel.loading.collectAsState()
            val navController = rememberNavController()
            var topBarTitle by remember { mutableStateOf("") }
            val queue by viewModel.currentQueueFlow.collectAsState()
            val sourcePaths by viewModel.currentSourcePathsFlow.collectAsState()
            val currentIndex by viewModel.currentIndexFlow.collectAsState()
            val currentPlaybackPosition by viewModel.currentPlaybackPositionFlow.collectAsState()
            val currentBufferedPosition by viewModel.currentBufferedPositionFlow.collectAsState()
            val currentPlaybackInfo by viewModel.currentPlaybackInfoFlow.collectAsState()
            val currentRepeatMode by viewModel.currentRepeatModeFlow.collectAsState()
            var forceScrollToCurrent by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var showDropboxDialog by remember { mutableStateOf(false) }
            var showResetShuffleDialog by remember { mutableStateOf(false) }
            var showOptionsDialog by remember { mutableStateOf(false) }
            var selectedTrack by remember { mutableStateOf<DomainTrack?>(null) }
            var selectedAlbum by remember { mutableStateOf<Album?>(null) }
            var selectedArtist by remember { mutableStateOf<Artist?>(null) }
            var selectedGenre by remember { mutableStateOf<Genre?>(null) }
            var selectedNav by remember { mutableStateOf<Nav?>(null) }
            val workInfoList by viewModel.workInfoListFlow
                .collectAsState(initial = emptyList())
            var progressMessage by remember { mutableStateOf<String?>(null) }
            var finishedWorkIdSet by remember { mutableStateOf(emptySet<UUID>()) }
            val hasAlreadyShownDropboxSyncAlert by context.getHasAlreadyShownDropboxSyncAlert()
                .collectAsState(initial = false)
            var downloadTargets by remember { mutableStateOf(emptyList<String>()) }
            var invalidateDownloadedTargets by remember { mutableStateOf(emptyList<Long>()) }
            var attachLyricTargetTrackId by remember { mutableLongStateOf(-1) }
            val snackBarMessage by viewModel.snackBarMessageFlow.collectAsState()
            val equalizerParams by context.getEqualizerParams().collectAsState(initial = null)
            var scrollToTop by remember { mutableLongStateOf(0L) }
            var showLyric by remember { mutableStateOf(false) }
            val currentDropboxItemList by viewModel.dropboxItemList.collectAsState(
                initial = "" to emptyList()
            )
            val layoutType by layoutTypeFlow.collectAsState()
            var optionMediaItem by remember { mutableStateOf<MediaItem?>(null) }
            val onToggleFavorite: (mediaItem: MediaItem?) -> MediaItem? = { mediaItem ->
                val newMediaItem = mediaItem.isFavoriteToggled()
                coroutineScope.launch {
                    when (newMediaItem) {
                        is DomainTrack -> {
                            val trackDao = DB.getInstance(context).trackDao()
                            val newTrack = trackDao.get(newMediaItem.id)
                                ?.track
                                ?.copy(isFavorite = newMediaItem.isFavorite)
                                ?: return@launch
                            trackDao.insert(newTrack)
                        }

                        is Album -> {
                            DB.getInstance(context).albumDao().insert(newMediaItem)
                        }

                        is Artist -> {
                            DB.getInstance(context).artistDao().insert(newMediaItem)
                        }
                    }
                }

                newMediaItem
            }

            onLrcFileLoaded = {
                if (attachLyricTargetTrackId > 0) {
                    coroutineScope.launch {
                        val db = DB.getInstance(context)
                        val id = db.lyricDao().getLyricIdByTrackId(attachLyricTargetTrackId) ?: 0
                        db.lyricDao()
                            .upsertLyric(
                                Lyric(id = id, trackId = attachLyricTargetTrackId, lines = it)
                            )
                        viewModel.emitSnackBarMessage(
                            getString(R.string.message_attach_lyric_success)
                        )
                        delay(2000)
                        viewModel.emitSnackBarMessage(null)
                        attachLyricTargetTrackId = -1
                    }
                } else {
                    coroutineScope.launch {
                        viewModel.emitSnackBarMessage(
                            getString(R.string.message_attach_lyric_failure)
                        )
                        delay(2000)
                        viewModel.emitSnackBarMessage(null)
                    }
                }
            }

            LaunchedEffect(isNightMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = ColorPrimaryDark.value.toInt(),
                        darkScrim = ColorPrimaryDarkInverse.value.toInt(),
                        detectDarkMode = { isNightMode }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = ColorBackground.value.toInt(),
                        darkScrim = ColorBackgroundInverse.value.toInt(),
                        detectDarkMode = { isNightMode }
                    )
                )
            }

            LaunchedEffect(
                workInfoList.map { it.progress },
                workInfoList.map { it.state }
            ) {
                if (workInfoList.none { it.state == WorkInfo.State.RUNNING }) {
                    viewModel.forceLoad.value = Unit
                    progressMessage =
                        if (workInfoList.any {
                                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                            }) {
                            getString(R.string.starting)
                        } else null
                    return@LaunchedEffect
                }

                workInfoList.forEach { workInfo ->
                    workInfo.progress.also { progress ->
                        progress.getInt(KEY_PROGRESS_PROGRESS_NUMERATOR, -1).let { numerator ->
                            if (numerator < 0) return@let

                            val title = progress.getString(KEY_PROGRESS_TITLE)
                                ?: getString(R.string.progress_title_retrieve_media)

                            val denominator = progress.getInt(KEY_PROGRESS_PROGRESS_DENOMINATOR, -1)
                            val totalFilesCount =
                                progress.getInt(KEY_PROGRESS_PROGRESS_TOTAL_FILES, -1)
                            val path = progress.getString(KEY_PROGRESS_PROGRESS_PATH).orEmpty()
                            val remaining = progress.getLong(KEY_PROGRESS_REMAINING, -1)
                            val remainingFilesSize =
                                progress.getLong(KEY_PROGRESS_REMAINING_FILES_SIZE, -1)

                            val progressText =
                                if (denominator < 0 || totalFilesCount < 0) ""
                                else getString(
                                    R.string.progress_sync,
                                    numerator,
                                    denominator,
                                    totalFilesCount
                                )
                            val remainingText =
                                if (remainingFilesSize < 0) ""
                                else getString(
                                    R.string.remaining,
                                    if (remaining < 0) "" else remaining.getTimeString(),
                                    remainingFilesSize.getNumberWithUnitPrefix { value, prefix ->
                                        "$value $prefix"
                                    }
                                )

                            if (progressText.isNotEmpty() || remainingText.isNotEmpty() || path.isNotEmpty()) {
                                progressMessage =
                                    "$title\n$progressText\n$remainingText\n$path"
                                onCancelProgress = {
                                    val workManager = WorkManager.getInstance(context)
                                    workInfo.tags.forEach {
                                        workManager.cancelAllWorkByTag(it)
                                    }
                                }
                            }
                        }
                    }
                    if (finishedWorkIdSet.contains(workInfo.id).not()
                        && workInfo.outputData.getBoolean(KEY_PROGRESS_FINISHED, false)
                    ) {
                        finishedWorkIdSet += workInfo.id
                        viewModel.forceLoad.value = Unit
                        progressMessage = null
                    }
                }
            }

            SideEffect {
                onAuthDropboxCompleted = { showDropboxDialog = true }
            }

            QTheme(darkTheme = isNightMode) {
                Box(
                    modifier = Modifier
                        .background(color = QTheme.colors.colorBackground)
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    when (layoutType) {
                        is LayoutType.Single -> {
                            SingleScreen(
                                navController = navController,
                                topBarTitle = topBarTitle,
                                optionMediaItem = optionMediaItem,
                                sourcePaths = sourcePaths,
                                queue = queue,
                                currentIndex = currentIndex,
                                currentPlaybackPosition = currentPlaybackPosition,
                                currentBufferedPosition = currentBufferedPosition,
                                currentPlaybackInfo = currentPlaybackInfo,
                                currentRepeatMode = currentRepeatMode,
                                isLoading = isLoading,
                                showLyric = showLyric,
                                selectedNav = selectedNav,
                                selectedTrack = selectedTrack,
                                selectedAlbum = selectedAlbum,
                                selectedArtist = selectedArtist,
                                selectedGenre = selectedGenre,
                                equalizerParams = equalizerParams,
                                currentDropboxItemList = currentDropboxItemList,
                                downloadTargets = downloadTargets,
                                invalidateDownloadedTargets = invalidateDownloadedTargets,
                                snackBarMessage = progressMessage ?: snackBarMessage,
                                forceScrollToCurrent = forceScrollToCurrent,
                                showDropboxDialog = showDropboxDialog,
                                showResetShuffleDialog = showResetShuffleDialog,
                                showOptionsDialog = showOptionsDialog,
                                hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
                                scrollToTop = scrollToTop,
                                onSelectNav = { selectedNav = it },
                                onTapBar = { scrollToTop = System.currentTimeMillis() },
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        context.setIsNightMode(isNightMode.not())
                                    }
                                },
                                onChangeTopBarTitle = { topBarTitle = it },
                                onSelectTrack = { selectedTrack = it },
                                onSelectAlbum = { selectedAlbum = it },
                                onSelectArtist = { selectedArtist = it },
                                onSelectGenre = { selectedGenre = it },
                                onTogglePlayPause = {
                                    viewModel.onPlayOrPause(
                                        currentPlaybackInfo.first &&
                                                currentPlaybackInfo.second == Player.STATE_READY
                                    )
                                },
                                onPrev = viewModel::onPrev,
                                onNext = viewModel::onNext,
                                onRewind = viewModel::onRewind,
                                onFastForward = viewModel::onFF,
                                resetPlaybackButton = { viewModel.onNewPlaybackButton(PlaybackButton.UNDEFINED) },
                                onNewProgress = viewModel::onNewSeekBarProgress,
                                rotateRepeatMode = viewModel::onClickRepeatButton,
                                shuffleQueue = viewModel::onShuffle,
                                resetShuffleQueue = viewModel::onResetShuffle,
                                closeOptionsDialog = { showOptionsDialog = false },
                                moveToCurrentIndex = {
                                    forceScrollToCurrent = System.currentTimeMillis()
                                },
                                clearQueue = viewModel::onClickClearQueueButton,
                                onToggleShowLyrics = { showLyric = showLyric.not() },
                                onNewQueue = viewModel::onNewQueue,
                                onQueueMove = viewModel::onQueueMove,
                                onChangeRequestedTrackInQueue = viewModel::onChangeRequestedTrackInQueue,
                                onRemoveTrackFromQueue = viewModel::onRemoveTrackFromQueue,
                                onShowDropboxDialog = { showDropboxDialog = true },
                                onRetrieveMedia = ::retrieveMedia,
                                onDownload = { downloadTargets = it },
                                onCancelDownload = { downloadTargets = emptyList() },
                                onStartDownloader = {
                                    enqueueDropboxDownloadWorker(downloadTargets)
                                    downloadTargets = emptyList()
                                },
                                onInvalidateDownloaded = { invalidateDownloadedTargets = it },
                                onCancelInvalidateDownloaded = {
                                    invalidateDownloadedTargets = emptyList()
                                },
                                onStartInvalidateDownloaded = {
                                    viewModel.invalidateDownloaded(
                                        invalidateDownloadedTargets
                                    )
                                    invalidateDownloadedTargets = emptyList()
                                },
                                onDeleteTrack = viewModel::deleteTrack,
                                onExportLyric = {
                                    coroutineScope.launch {
                                        lrcString = DB.getInstance(context)
                                            .lyricDao()
                                            .getLyricByTrackId(it.id)
                                            ?.toLrcString() ?: return@launch
                                        putContent.launch("${it.title}.lrc")
                                    }
                                },
                                onAttachLyric = {
                                    attachLyricTargetTrackId = it
                                    getContent.launch("*/*")
                                },
                                onDetachLyric = {
                                    coroutineScope.launch {
                                        DB.getInstance(context)
                                            .lyricDao()
                                            .deleteLyricByTrackId(it)
                                        viewModel.emitSnackBarMessage(
                                            getString(R.string.message_delete_lyric_complete)
                                        )
                                        delay(2000)
                                        viewModel.emitSnackBarMessage(null)
                                    }
                                },
                                onStartAuthDropbox = {
                                    viewModel.isDropboxAuthOngoing = true
                                    Auth.startOAuth2PKCE(
                                        context,
                                        BuildConfig.DROPBOX_APP_KEY,
                                        dbxRequestConfig,
                                        DbxHost.DEFAULT
                                    )
                                    showDropboxDialog = false
                                },
                                onShowDropboxFolderChooser = viewModel::showDropboxFolderChooser,
                                hideDropboxDialog = {
                                    viewModel.clearDropboxItemList()
                                    showDropboxDialog = false
                                },
                                startDropboxSync = { rootFolderPath, needDownloaded ->
                                    retrieveDropboxMedia(
                                        rootFolderPath ?: MainViewModel.DROPBOX_PATH_ROOT,
                                        needDownloaded
                                    )
                                },
                                hideResetShuffleDialog = { showResetShuffleDialog = false },
                                onStartBilling = { viewModel.startBilling(this@MainActivity) },
                                onCancelProgress = onCancelProgress,
                                onSetOptionMediaItem = { mediaItem -> optionMediaItem = mediaItem },
                                onToggleFavorite = onToggleFavorite,
                                onShowOptions = { showOptionsDialog = true },
                            )
                        }

                        is LayoutType.Twin -> {
                            TwinScreen(
                                navController = navController,
                                topBarTitle = topBarTitle,
                                optionMediaItem = optionMediaItem,
                                queue = queue,
                                currentIndex = currentIndex,
                                currentPlaybackPosition = currentPlaybackPosition,
                                currentBufferedPosition = currentBufferedPosition,
                                currentPlaybackInfo = currentPlaybackInfo,
                                currentRepeatMode = currentRepeatMode,
                                isLoading = isLoading,
                                showLyric = showLyric,
                                selectedNav = selectedNav,
                                selectedTrack = selectedTrack,
                                selectedAlbum = selectedAlbum,
                                selectedArtist = selectedArtist,
                                selectedGenre = selectedGenre,
                                equalizerParams = equalizerParams,
                                currentDropboxItemList = currentDropboxItemList,
                                downloadTargets = downloadTargets,
                                invalidateDownloadedTargets = invalidateDownloadedTargets,
                                snackBarMessage = progressMessage ?: snackBarMessage,
                                forceScrollToCurrent = forceScrollToCurrent,
                                showDropboxDialog = showDropboxDialog,
                                showResetShuffleDialog = showResetShuffleDialog,
                                showOptionsDialog = showOptionsDialog,
                                hasAlreadyShownDropboxSyncAlert = hasAlreadyShownDropboxSyncAlert,
                                scrollToTop = scrollToTop,
                                onSelectNav = { selectedNav = it },
                                onTapBar = { scrollToTop = System.currentTimeMillis() },
                                onToggleTheme = {
                                    coroutineScope.launch {
                                        context.setIsNightMode(isNightMode.not())
                                    }
                                },
                                onChangeTopBarTitle = { topBarTitle = it },
                                onSelectTrack = { selectedTrack = it },
                                onSelectAlbum = { selectedAlbum = it },
                                onSelectArtist = { selectedArtist = it },
                                onSelectGenre = { selectedGenre = it },
                                onTogglePlayPause = {
                                    viewModel.onPlayOrPause(
                                        currentPlaybackInfo.first &&
                                                currentPlaybackInfo.second == Player.STATE_READY
                                    )
                                },
                                onPrev = viewModel::onPrev,
                                onNext = viewModel::onNext,
                                onRewind = viewModel::onRewind,
                                onFastForward = viewModel::onFF,
                                resetPlaybackButton = { viewModel.onNewPlaybackButton(PlaybackButton.UNDEFINED) },
                                onNewProgress = viewModel::onNewSeekBarProgress,
                                rotateRepeatMode = viewModel::onClickRepeatButton,
                                shuffleQueue = viewModel::onShuffle,
                                resetShuffleQueue = viewModel::onResetShuffle,
                                closeOptionsDialog = { showOptionsDialog = false },
                                moveToCurrentIndex = {
                                    forceScrollToCurrent = System.currentTimeMillis()
                                },
                                clearQueue = viewModel::onClickClearQueueButton,
                                onToggleShowLyrics = { showLyric = showLyric.not() },
                                onNewQueue = viewModel::onNewQueue,
                                onQueueMove = viewModel::onQueueMove,
                                onChangeRequestedTrackInQueue = viewModel::onChangeRequestedTrackInQueue,
                                onRemoveTrackFromQueue = viewModel::onRemoveTrackFromQueue,
                                onShowDropboxDialog = { showDropboxDialog = true },
                                onRetrieveMedia = ::retrieveMedia,
                                onDownload = { downloadTargets = it },
                                onCancelDownload = { downloadTargets = emptyList() },
                                onStartDownloader = {
                                    enqueueDropboxDownloadWorker(downloadTargets)
                                    downloadTargets = emptyList()
                                },
                                onInvalidateDownloaded = { invalidateDownloadedTargets = it },
                                onCancelInvalidateDownloaded = {
                                    invalidateDownloadedTargets = emptyList()
                                },
                                onStartInvalidateDownloaded = {
                                    viewModel.invalidateDownloaded(
                                        invalidateDownloadedTargets
                                    )
                                    invalidateDownloadedTargets = emptyList()
                                },
                                onDeleteTrack = viewModel::deleteTrack,
                                onExportLyric = {
                                    coroutineScope.launch {
                                        lrcString = DB.getInstance(context)
                                            .lyricDao()
                                            .getLyricByTrackId(it.id)
                                            ?.toLrcString() ?: return@launch
                                        putContent.launch("${it.title}.lrc")
                                    }
                                },
                                onAttachLyric = {
                                    attachLyricTargetTrackId = it
                                    getContent.launch("*/*")
                                },
                                onDetachLyric = {
                                    coroutineScope.launch {
                                        DB.getInstance(context)
                                            .lyricDao()
                                            .deleteLyricByTrackId(it)
                                        viewModel.emitSnackBarMessage(
                                            getString(R.string.message_delete_lyric_complete)
                                        )
                                        delay(2000)
                                        viewModel.emitSnackBarMessage(null)
                                    }
                                },
                                onStartAuthDropbox = {
                                    viewModel.isDropboxAuthOngoing = true
                                    Auth.startOAuth2PKCE(
                                        context,
                                        BuildConfig.DROPBOX_APP_KEY,
                                        dbxRequestConfig,
                                        DbxHost.DEFAULT
                                    )
                                    showDropboxDialog = false
                                },
                                onShowDropboxFolderChooser = viewModel::showDropboxFolderChooser,
                                hideDropboxDialog = {
                                    viewModel.clearDropboxItemList()
                                    showDropboxDialog = false
                                },
                                startDropboxSync = { rootFolderPath, needDownloaded ->
                                    retrieveDropboxMedia(
                                        rootFolderPath ?: MainViewModel.DROPBOX_PATH_ROOT,
                                        needDownloaded
                                    )
                                },
                                hideResetShuffleDialog = { showResetShuffleDialog = false },
                                onStartBilling = { viewModel.startBilling(this@MainActivity) },
                                onCancelProgress = onCancelProgress,
                                onSetOptionMediaItem = { mediaItem -> optionMediaItem = mediaItem },
                                onToggleFavorite = onToggleFavorite,
                                onShowOptions = { showOptionsDialog = true },
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
                if (Build.VERSION.SDK_INT < 29) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true,
                generalContentObserver
            )
        }
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT > 25) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                contentResolver.refresh(
                    if (Build.VERSION.SDK_INT < 29) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    null,
                    null
                )
            }
        }

        if (viewModel.isDropboxAuthOngoing) {
            viewModel.isDropboxAuthOngoing = false
            lifecycleScope.launch {
                viewModel.storeDropboxApiToken()
                onAuthDropboxCompleted?.invoke()
            }
        }

        if (cacheDir.getDirSize() == 0L) {
            lifecycleScope.launch {
                viewModel.invalidateDownloaded(
                    DB.getInstance(this@MainActivity)
                        .trackDao()
                        .getAllDownloadedIds()
                )
            }
        }

        viewModel.requestBillingInfoUpdate()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(generalContentObserver)

        super.onDestroy()
    }

    private fun retrieveMedia(onlyAdded: Boolean) {
        WorkManager.getInstance(this)
            .cancelAllWorkByTag(LocalMediaRetrieveWorker.TAG)
        if (Build.VERSION.SDK_INT < 33) {
            when (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_GRANTED -> {
                    enqueueLocalRetrieveWorker(onlyAdded)
                }

                else -> {
                    onStoragePermissionRequestResult = {
                        if (it) {
                            enqueueLocalRetrieveWorker(onlyAdded)
                        } else {
                            onReadExternalStorageDenied()
                        }
                    }
                    requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
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
    }

    private fun retrieveDropboxMedia(rootPath: String, needDownloaded: Boolean) {
        WorkManager.getInstance(this)
            .cancelAllWorkByTag(DropboxMediaRetrieveWorker.TAG)
        if (Build.VERSION.SDK_INT < 33) {
            when {
                checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    enqueueDropboxRetrieveWorker(rootPath, needDownloaded)
                }

                else -> {
                    onStoragePermissionRequestResult = {
                        if (it) {
                            enqueueDropboxRetrieveWorker(rootPath, needDownloaded)
                        } else {
                            onReadExternalStorageDenied()
                        }
                    }
                    requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            enqueueDropboxRetrieveWorker(rootPath, needDownloaded)
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

    private fun enqueueDropboxRetrieveWorker(rootPath: String, needDownloaded: Boolean) {
        viewModel.workManager.beginUniqueWork(
            MEDIA_RETRIEVE_WORKER_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DropboxMediaRetrieveWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(DropboxMediaRetrieveWorker.KEY_ROOT_PATH, rootPath)
                        .putBoolean(DropboxMediaRetrieveWorker.KEY_NEED_DOWNLOADED, needDownloaded)
                        .build()
                )
                .addTag(DropboxMediaRetrieveWorker.TAG)
                .build()
        ).enqueue()
    }

    private fun enqueueDropboxDownloadWorker(targetPaths: List<String>) {
        viewModel.workManager.beginUniqueWork(
            DROPBOX_DOWNLOAD_WORKER_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DropboxDownloadWorker>()
                .setInputData(
                    Data.Builder()
                        .putStringArray(
                            DropboxDownloadWorker.KEY_TARGET_PATHS,
                            targetPaths.toTypedArray()
                        )
                        .build()
                )
                .addTag(DropboxDownloadWorker.TAG)
                .build()
        ).enqueue()
    }

    private fun onReadExternalStorageDenied() = Unit

    private fun onReadMediaDenied() = Unit

    private fun File.getDirSize(initialSize: Long = 0): Long =
        if (isFile) initialSize + length()
        else listFiles()?.sumOf { it.getDirSize(initialSize) } ?: initialSize
}

private val Resources.isNightMode
    get() =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
