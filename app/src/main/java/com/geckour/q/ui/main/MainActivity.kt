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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dropbox.core.DbxHost
import com.dropbox.core.android.Auth
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.domain.model.LayoutType
import com.geckour.q.domain.model.MediaItem
import com.geckour.q.domain.model.Nav
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.service.DropboxMediaSyncJobService
import com.geckour.q.ui.compose.ColorBackground
import com.geckour.q.ui.compose.ColorBackgroundInverse
import com.geckour.q.ui.compose.ColorPrimaryDark
import com.geckour.q.ui.compose.ColorPrimaryDarkInverse
import com.geckour.q.ui.main.dialog.DialogEvent
import com.geckour.q.ui.main.dialog.DialogState
import com.geckour.q.ui.widget.player.PlayerSheetWidgetProvider
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.authorizeSpotifyAppRemote
import com.geckour.q.util.createSpotifyAuthorizationRequest
import com.geckour.q.util.dbxRequestConfig
import com.geckour.q.util.isSpotifyInstalled
import com.geckour.q.util.spotifyWebUrl
import com.geckour.q.util.getExtension
import com.geckour.q.util.parseLrc
import com.geckour.q.worker.LocalMediaRetrieveWorker
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class MainActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }

    private val viewModel by viewModel<MainViewModel>()

    private var attachLyricTargetTrackId = -1L
    private var lrcString: String? = null

    private val mainActions = object : MainActions {

        override fun onSelectNav(nav: Nav?) {
            viewModel.selectedNav.value = nav
        }

        override fun onTapBar() = viewModel.requestScrollToTop()

        override fun onTapTopBarTitle() = viewModel.onTapTopBarTitle()

        override suspend fun onSearchSpotify(query: String): List<SearchItem> =
            viewModel.searchSpotifyItems(query)

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

        override fun onSetOptionArtist(artistId: Long) = viewModel.setOptionArtist(artistId)

        override fun onSetOptionAlbum(albumId: Long) = viewModel.setOptionAlbum(albumId)

        override fun onToggleFavorite(mediaItem: MediaItem?): MediaItem? =
            viewModel.toggleFavorite(mediaItem)

        override fun onShowDialog(dialogState: DialogState?) = viewModel.showDialog(dialogState)

        override fun onDialogEvent(event: DialogEvent) = handleDialogEvent(event)

        override fun onInvalidateDownloadedArtist(artistId: Long) =
            viewModel.showInvalidateDownloadedDialogForArtist(artistId)

        override fun onInvalidateDownloadedAlbum(albumId: Long) =
            viewModel.showInvalidateDownloadedDialogForAlbum(albumId)

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

    private val spotifyAuth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val response = AuthorizationClient.getResponse(result.resultCode, result.data)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> onSpotifyAuthorized(response)

                AuthorizationResponse.Type.ERROR -> {
                    lifecycleScope.launch {
                        viewModel.emitSnackbarMessage(
                            getString(
                                R.string.spotify_message_auth_failure,
                                response.error,
                            )
                        )
                        delay(2000.milliseconds)
                        viewModel.emitSnackbarMessage(null)
                    }
                }

                else -> Unit
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
            .map { windowLayoutInfo ->
                val windowBounds = windowManager.currentWindowMetrics.bounds
                windowLayoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?.let { getFoldAlignedLayoutType(it, windowBounds) }
                    ?: getSizeBasedLayoutType(windowBounds)
            }
            .stateIn(scope = lifecycleScope, started = SharingStarted.Eagerly, LayoutType.Single)

        setContent {
            val layoutType by layoutTypeFlow.collectAsState()
            MainScreen(
                viewModel = viewModel,
                layoutType = layoutType,
                actions = mainActions,
                onChangeNightMode = ::applySystemBarStyle,
            )
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
                val stored = viewModel.storeDropboxApiToken {
                    lifecycleScope.launch {
                        onDropboxSyncFailure(it)
                    }
                }
                if (stored) viewModel.showDialog(DialogState.Dropbox())
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

    private fun getFoldAlignedLayoutType(
        foldingFeature: FoldingFeature,
        windowBounds: Rect,
    ): LayoutType.Twin? {
        if (foldingFeature.state != FoldingFeature.State.HALF_OPENED) return null

        val hingeBounds = foldingFeature.bounds
        val isVertical = foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL
        val startSize = if (isVertical) hingeBounds.left else hingeBounds.top
        val endSize =
            if (isVertical) windowBounds.width() - hingeBounds.right
            else windowBounds.height() - hingeBounds.bottom
        if (startSize <= 0 || endSize <= 0) return null

        return LayoutType.Twin(
            hingePosition = hingeBounds,
            orientation = foldingFeature.orientation,
        )
    }

    private fun getSizeBasedLayoutType(windowBounds: Rect): LayoutType {
        val windowWidth = windowBounds.width().toFloat()
        val windowHeight = windowBounds.height().toFloat()
        val isSquareIshScreen = (windowHeight / windowWidth) in 0.75..1.33
        val isHorizontal =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val existSpaceToSplit = windowWidth > 1500

        return if (existSpaceToSplit && (isSquareIshScreen || isHorizontal)) {
            LayoutType.Twin()
        } else {
            LayoutType.Single
        }
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

    private fun applySystemBarStyle(isInNightMode: Boolean) {
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

            is DialogEvent.NewQueueFromSource -> {
                viewModel.onNewQueueFromSource(
                    source = event.source,
                    favoriteOnly = event.favoriteOnly,
                    actionType = event.actionType,
                    classType = event.classType,
                )
            }

            is DialogEvent.GenerateQueue -> {
                viewModel.onGenerateQueue(event.track, event.actionType, event.classType)
            }

            is DialogEvent.DeleteTrack -> viewModel.deleteTrack(event.track)

            is DialogEvent.DeleteTracksFromSource -> {
                viewModel.deleteTracksFromSource(event.source, event.favoriteOnly)
            }

            is DialogEvent.ExportLyric -> {
                lifecycleScope.launch {
                    lrcString = viewModel.getLrcString(event.track.id) ?: return@launch
                    putContent.launch("${event.track.title}.lrc")
                }
            }

            is DialogEvent.AttachLyric -> {
                attachLyricTargetTrackId = event.trackId
                getContent.launch("*/*")
            }

            is DialogEvent.DetachLyric -> viewModel.detachLyric(event.trackId)

            DialogEvent.AcknowledgeDropboxSyncAlert -> viewModel.acknowledgeDropboxSyncAlert()

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

            DialogEvent.StartSpotifyAuth -> {
                spotifyAuth.launch(
                    AuthorizationClient.createLoginActivityIntent(
                        this,
                        createSpotifyAuthorizationRequest(),
                    )
                )
                viewModel.dismissDialog()
            }

            DialogEvent.SignOutSpotify -> viewModel.signOutSpotify()

            is DialogEvent.ChangeSpotifySource -> viewModel.changeSpotifySource(event.source)

            DialogEvent.LoadMoreSpotifyItems -> viewModel.loadMoreSpotifyItems()

            is DialogEvent.OpenSpotifyContainer -> {
                viewModel.openSpotifyContainer(event.container)
            }

            DialogEvent.CloseSpotifyContainer -> viewModel.closeSpotifyContainer()


            is DialogEvent.ShowSpotifyTrackOption -> {
                viewModel.showDialog(DialogState.SpotifyTrackOption(event.track))
            }

            is DialogEvent.ShowSpotifyContainerOption -> {
                viewModel.showDialog(DialogState.SpotifyContainerOption(event.container))
            }

            is DialogEvent.OpenInSpotify -> openInSpotify(event.uri)

            is DialogEvent.AddSpotifyContainer -> {
                viewModel.addSpotifyContainer(event.container, event.actionType, event.classType)
            }

            is DialogEvent.AddSpotifyTrack -> {
                viewModel.addSpotifyTrack(event.track, event.actionType)
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
                viewModel.respondSyncSizeConfirmation(event.approved)
            }

            DialogEvent.DismissSyncSizeExceeded -> viewModel.dismissSyncSizeExceeded()
        }
    }

    private fun onLrcFileLoaded(lyricLines: List<LyricLine>) {
        if (attachLyricTargetTrackId > 0) {
            viewModel.attachLyric(attachLyricTargetTrackId, lyricLines)
            attachLyricTargetTrackId = -1
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

    private fun openInSpotify(uri: String) {
        if (isSpotifyInstalled(this).not()) {
            AuthorizationClient.openDownloadSpotifyActivity(this)
            return
        }

        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
            .onFailure {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri.spotifyWebUrl.toUri()))
                }.onFailure { throwable -> Timber.e(throwable) }
            }
    }

    private fun onSpotifyAuthorized(response: AuthorizationResponse) {
        val accessToken = response.accessToken ?: return

        lifecycleScope.launch {
            viewModel.storeSpotifyCredential(
                accessToken = accessToken,
                refreshToken = response.refreshToken,
                expiresInSeconds = response.expiresIn,
            )
            try {
                authorizeSpotifyAppRemote(this@MainActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Timber.e(t)
                viewModel.showSpotifyError(t)
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
