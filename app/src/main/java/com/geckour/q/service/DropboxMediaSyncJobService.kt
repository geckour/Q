package com.geckour.q.service

import android.app.Notification
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.net.Uri
import android.os.PersistableBundle
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RateLimitException
import com.dropbox.core.ServerException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.domain.model.PendingMediaRetrieve
import com.geckour.q.domain.model.SyncProgress
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.DROPBOX_EXPIRES_IN
import com.geckour.q.util.DownloadFailedException
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.SyncProgressState
import com.geckour.q.util.getExtension
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isDownloaded
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveAudioFileFromUrl
import com.geckour.q.util.saveTempAudioFileFromUrl
import com.geckour.q.util.setPendingMediaRetrieve
import com.geckour.q.worker.storeMediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class DropboxMediaSyncJobService : JobService() {

    companion object {

        const val JOB_ID = 300

        private const val KEY_ROOT_PATH = "key_root_path"
        private const val KEY_TARGET_PATHS_FILE_PATH = "key_target_paths_file_path"
        private const val KEY_NEED_DOWNLOADED = "key_need_downloaded"
        private const val KEY_RETRY_GENERATION = "key_retry_generation"

        private const val PROGRESS_UPDATE_THRESHOLD_MILLIS = 200

        private const val MAX_RETRY_COUNT = 5
        private const val MAX_RATE_LIMIT_RETRY_COUNT = 2
        private const val MAX_RETRY_GENERATION = 3

        private const val LINK_FETCH_CONCURRENCY = 8
        private const val STORE_CONCURRENCY = 4

        private const val ESTIMATED_BYTES_PER_TARGET = 10L * 1024 * 1024

        private const val NOTIFICATION_ID_RETRIEVE = 300

        private const val NOTIFICATION_LARGE_ICON_SIZE = 256

        private const val NOTIFICATION_UPDATE_THRESHOLD_MILLIS = 1000

        private const val TARGET_PATHS_DIR_NAME = "download"

        private const val STALE_TARGET_PATHS_MILLIS = 24 * 60 * 60 * 1000L

        private val targetPathsJson = Json { ignoreUnknownKeys = true }

        fun schedule(
            context: Context,
            rootPath: String,
            needDownloaded: Boolean,
            retryGeneration: Int = 0,
        ): Boolean = schedule(
            context,
            PersistableBundle().apply {
                putString(KEY_ROOT_PATH, rootPath)
                putBoolean(KEY_NEED_DOWNLOADED, needDownloaded)
                putInt(KEY_RETRY_GENERATION, retryGeneration)
            }
        )

        fun schedule(context: Context, targetPaths: List<String>): Boolean {
            if (targetPaths.isEmpty()) return false

            val targetPathsFile =
                writeTargetPaths(context, UUID.randomUUID().toString(), targetPaths)

            return schedule(
                context,
                PersistableBundle().apply {
                    putString(KEY_TARGET_PATHS_FILE_PATH, targetPathsFile.absolutePath)
                    putBoolean(KEY_NEED_DOWNLOADED, true)
                }
            )
        }

        private fun schedule(context: Context, extras: PersistableBundle): Boolean {
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, DropboxMediaSyncJobService::class.java)
            )
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(
                    ESTIMATED_BYTES_PER_TARGET,
                    JobInfo.NETWORK_BYTES_UNKNOWN.toLong()
                )
                .setExtras(extras)
                .build()

            val result = context.getSystemService(JobScheduler::class.java)?.schedule(jobInfo)
            Timber.d("qgeck scheduled sync job: $result")

            return result == JobScheduler.RESULT_SUCCESS
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }

        fun isScheduled(context: Context): Boolean =
            context.getSystemService(JobScheduler::class.java)
                ?.allPendingJobs
                ?.any { it.id == JOB_ID } == true

        private fun writeTargetPaths(context: Context, name: String, paths: List<String>): File {
            val dir = File(context.dataDir, TARGET_PATHS_DIR_NAME)
            if (dir.exists().not()) dir.mkdir()

            val staleThreshold = System.currentTimeMillis() - STALE_TARGET_PATHS_MILLIS
            dir.listFiles()?.forEach { if (it.lastModified() < staleThreshold) it.delete() }

            return File(dir, "$name.json").apply {
                writeText(targetPathsJson.encodeToString(paths))
            }
        }

        private fun readTargetPaths(file: File): List<String> =
            targetPathsJson.decodeFromString(file.readText())
    }

    @Serializable
    private data class RetrieveTarget(
        val id: String,
        val pathLower: String,
        val pathDisplay: String,
        val size: Long,
        val serverModifiedAt: Long,
        val url: String,
        val urlExpiredAt: Long,
    )

    private val db by lazy { DB.getInstance(this) }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var runningJob: Job? = null

    @Volatile
    private var isStopped = false

    private val targets = mutableListOf<RetrieveTarget>()
    private val targetsMutex = Mutex()
    private val progressMutex = Mutex()
    private val dbMutex = Mutex()
    private val linkFetchSemaphore = Semaphore(LINK_FETCH_CONCURRENCY)
    private val storeSemaphore = Semaphore(STORE_CONCURRENCY)
    private val currentPaths = mutableListOf<String>()

    private var isRetrieving = true
    private var retryGeneration = 0
    private var seed = 0L
    private var totalFilesSize = 0L
    private var processedFilesSize = 0L
    private var lastProcessedFileSize = 0L
    private var lastProgressSampledTime = 0L
    private var lastNotifiedTime = 0L
    private var speeds = listOf<Float>()
    private var completedTargetsCount = 0
    private var storedTargetsCount = 0
    private var skippedTargetsCount = 0
    private var expiredTargetsCount = 0

    private val notificationBitmap =
        createBitmap(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)

    private val remainingFilesCount
        get() = if (isRetrieving) targets.size else targets.size - completedTargetsCount
    private val currentPathNames: List<String>
        get() = currentPaths.toList().map { it.substringAfterLast('/') }
    private val progressFraction: Float
        get() =
            if (isRetrieving || totalFilesSize <= 0) 0f
            else processedFilesSize.toFloat() / totalFilesSize
    private val remainingDuration
        get() = ((totalFilesSize - processedFilesSize) / speeds.average()).toLong()

    override fun onStartJob(params: JobParameters): Boolean {
        seed = System.currentTimeMillis()
        retryGeneration = params.extras.getInt(KEY_RETRY_GENERATION, 0)

        setNotification(
            params,
            NOTIFICATION_ID_RETRIEVE,
            getNotification(notificationBitmap),
            JOB_END_NOTIFICATION_POLICY_DETACH
        )

        runningJob = scope.launch {
            runCatching { sync(params) }
                .onFailure {
                    if (it is CancellationException) return@onFailure

                    Timber.e(it)
                }

            SyncProgressState.update(null)
            jobFinished(params, false)
        }

        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        isStopped = true
        Timber.d("qgeck sync job stopped: ${params.stopReason}")

        runningJob?.cancel()
        SyncProgressState.update(null)

        return true
    }

    override fun onDestroy() {
        scope.cancel()

        super.onDestroy()
    }

    private suspend fun sync(params: JobParameters) {
        val client = obtainDbxClient(applicationContext).firstOrNull() ?: return
        val rootPath = params.extras.getString(KEY_ROOT_PATH)
        val targetPathsFile = params.extras.getString(KEY_TARGET_PATHS_FILE_PATH)?.let { File(it) }
        if (rootPath == null && targetPathsFile == null) return

        val needDownloaded = params.extras.getBoolean(KEY_NEED_DOWNLOADED, false)

        if (rootPath != null) {
            applicationContext.setPendingMediaRetrieve(
                PendingMediaRetrieve(
                    rootPath = rootPath,
                    needDownloaded = needDownloaded,
                    generation = retryGeneration,
                )
            )
        }

        targets.clear()
        totalFilesSize = 0L
        skippedTargetsCount = 0
        updateProgress(params)

        coroutineScope {
            if (rootPath != null) {
                retrieveAudioFilePaths(this, params, rootPath, client, needDownloaded)
            } else {
                retrieveTargetPaths(this, params, requireNotNull(targetPathsFile), client)
            }
        }
        if (isStopped) return

        targetPathsFile?.delete()

        Timber.d("qgeck retrieved files count: ${targets.size}")

        runCatching {
            updateEstimatedNetworkBytes(
                params,
                totalFilesSize,
                JobInfo.NETWORK_BYTES_UNKNOWN.toLong()
            )
        }.onFailure { Timber.e(it) }

        isRetrieving = false
        completedTargetsCount = 0
        processedFilesSize = 0L
        updateProgress(params)

        coroutineScope {
            targets.forEach { target ->
                launch(Dispatchers.IO) {
                    storeSemaphore.withPermit {
                        if (isStopped) return@withPermit

                        target.storeMediaInfo(params, needDownloaded)
                    }
                }
            }
        }
        if (isStopped) return

        if (rootPath != null) {
            if (expiredTargetsCount > 0) {
                savePendingRetrieve(rootPath, needDownloaded)
            } else {
                applicationContext.setPendingMediaRetrieve(null)
            }
        }

        Timber.d("qgeck track in db count: ${db.trackDao().count()}")
    }

    private suspend fun retrieveTargetPaths(
        scope: CoroutineScope,
        params: JobParameters,
        targetPathsFile: File,
        client: DbxClientV2,
    ) {
        val targetPaths = runCatching { readTargetPaths(targetPathsFile) }
            .onFailure { Timber.e(it) }
            .getOrNull()
            ?: return

        targetPaths.forEach { targetPath ->
            scope.launch(Dispatchers.IO) {
                linkFetchSemaphore.withPermit {
                    if (isStopped) return@withPermit

                    val metadata = runCatching {
                        client.files().getMetadata(targetPath) as? FileMetadata
                    }.onFailure { Timber.e(it) }.getOrNull() ?: return@withPermit
                    val target = metadata.toRetrieveTargetIfNeeded(params, client, true)
                        ?: return@withPermit

                    targetsMutex.withLock {
                        targets.add(target)
                        totalFilesSize += target.size
                        updateProgress(params)
                    }
                }
            }
        }
    }

    private suspend fun retrieveAudioFilePaths(
        scope: CoroutineScope,
        params: JobParameters,
        root: String,
        client: DbxClientV2,
        needDownloaded: Boolean,
        retryCount: Int = 0,
    ) {
        if (isStopped) return
        try {
            var fileAndFolders = client.files().listFolder(root)
            val entries = fileAndFolders.entries.toMutableList()
            while (fileAndFolders.hasMore) {
                if (isStopped) return

                fileAndFolders = client.files().listFolderContinue(fileAndFolders.cursor)
                entries += fileAndFolders.entries
            }

            entries.filterIsInstance<FileMetadata>()
                .filter { it.name.isAudioFilePath }
                .forEach { metadata ->
                    scope.launch(Dispatchers.IO) {
                        linkFetchSemaphore.withPermit {
                            if (isStopped) return@withPermit

                            val target =
                                metadata.toRetrieveTargetIfNeeded(params, client, needDownloaded)
                                    ?: return@withPermit

                            targetsMutex.withLock {
                                targets.add(target)
                                totalFilesSize += target.size
                                updateProgress(params)
                            }
                        }
                    }
                }

            entries.filterIsInstance<FolderMetadata>().forEach { metadata ->
                retrieveAudioFilePaths(
                    scope,
                    params,
                    metadata.pathLower ?: return@forEach,
                    client,
                    needDownloaded
                )
            }
        } catch (e: RateLimitException) {
            if (retryCount >= MAX_RATE_LIMIT_RETRY_COUNT) throw e
            delay(e.backoffMillis.milliseconds)
            retrieveAudioFilePaths(scope, params, root, client, needDownloaded, retryCount + 1)
        } catch (e: ServerException) {
            if (retryCount >= MAX_RETRY_COUNT) throw e
            delay(3000.milliseconds)
            retrieveAudioFilePaths(scope, params, root, client, needDownloaded, retryCount + 1)
        } catch (e: NetworkIOException) {
            if (retryCount >= MAX_RETRY_COUNT) throw e
            delay(3000.milliseconds)
            retrieveAudioFilePaths(scope, params, root, client, needDownloaded, retryCount + 1)
        }
    }

    private suspend fun FileMetadata.toRetrieveTargetIfNeeded(
        params: JobParameters,
        client: DbxClientV2,
        needDownloaded: Boolean,
    ): RetrieveTarget? {
        val path = pathLower ?: return null
        val existingTrack = db.trackDao().getByDropboxPath(path)?.track
        val existingTrackLastModified = existingTrack?.lastModified
        if (existingTrackLastModified != null &&
            existingTrackLastModified >= serverModified.time &&
            (needDownloaded.not() || existingTrack.isDownloaded)
        ) {
            targetsMutex.withLock {
                skippedTargetsCount++
                updateProgress(params)
            }
            return null
        }

        return toRetrieveTarget(client)
    }

    private suspend fun FileMetadata.toRetrieveTarget(
        client: DbxClientV2,
        retryCount: Int = 0,
    ): RetrieveTarget? {
        val path = pathLower ?: return null

        return runCatching {
            RetrieveTarget(
                id = id,
                pathLower = path,
                pathDisplay = pathDisplay ?: path,
                size = size,
                serverModifiedAt = serverModified.time,
                url = client.files().getTemporaryLink(path).link,
                urlExpiredAt = System.currentTimeMillis() + DROPBOX_EXPIRES_IN,
            )
        }.getOrElse { t ->
            when {
                t is CancellationException -> throw t

                t is RateLimitException && retryCount < MAX_RATE_LIMIT_RETRY_COUNT -> {
                    delay(t.backoffMillis.milliseconds)
                    toRetrieveTarget(client, retryCount + 1)
                }

                (t is ServerException || t is NetworkIOException) &&
                        retryCount < MAX_RETRY_COUNT -> {
                    delay(3000.milliseconds)
                    toRetrieveTarget(client, retryCount + 1)
                }

                else -> {
                    Timber.e(t)
                    null
                }
            }
        }
    }

    private val String.isAudioFilePath: Boolean
        get() = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(this.getExtension())
            ?.contains("audio") == true

    private suspend fun RetrieveTarget.storeMediaInfo(
        params: JobParameters,
        needDownloaded: Boolean,
    ) {
        val existingTrack = db.trackDao().getByDropboxPath(pathLower)?.track
        if (existingTrack != null &&
            existingTrack.lastModified >= serverModifiedAt &&
            (needDownloaded.not() || existingTrack.isDownloaded)
        ) {
            completeTarget(params, size) { skippedTargetsCount++ }
            return
        }

        if (urlExpiredAt <= System.currentTimeMillis()) {
            Timber.d("qgeck link has expired: $pathDisplay")
            completeTarget(params, size) { expiredTargetsCount++ }
            return
        }

        addCurrentPath(params, pathDisplay)
        try {
            var retryCount = 0
            while (true) {
                val failure = runCatching { download(params, needDownloaded, existingTrack) }
                    .exceptionOrNull()
                    ?: return

                when {
                    failure is CancellationException -> throw failure

                    failure is DownloadFailedException && failure.code in 400..499 -> {
                        Timber.e(failure)
                        completeTarget(params, size) { expiredTargetsCount++ }
                        return
                    }

                    retryCount >= MAX_RETRY_COUNT -> {
                        Timber.e(failure)
                        completeTarget(params, size) { }
                        return
                    }

                    else -> {
                        Timber.e(failure)
                        retryCount++
                        delay(3000.milliseconds)
                    }
                }
            }
        } finally {
            removeCurrentPath(pathDisplay)
        }
    }

    private suspend fun RetrieveTarget.download(
        params: JobParameters,
        needDownloaded: Boolean,
        existingTrack: Track?,
    ) {
        var processedSize = 0L
        var stored = false
        var target: File? = null
        val fileAndProgressFlow =
            if (needDownloaded) saveAudioFileFromUrl(applicationContext, id, pathLower, url)
            else saveTempAudioFileFromUrl(applicationContext, id, pathLower, url)

        try {
            fileAndProgressFlow
                .onCompletion { cause ->
                    if (cause != null) return@onCompletion

                    target?.let { file ->
                        dbMutex.withLock {
                            file.storeMediaInfo(
                                applicationContext,
                                if (needDownloaded) Uri.fromFile(file).toString() else url,
                                existingTrack?.id,
                                null,
                                pathLower,
                                urlExpiredAt,
                                serverModifiedAt
                            )
                        }
                        if (needDownloaded.not()) file.delete()
                        stored = true
                    }
                }
                .collectLatest { (file, processed) ->
                    if (processed == null) {
                        return@collectLatest
                    }
                    target = file
                    advanceProgress(params, processed - processedSize)
                    processedSize = processed
                }
        } catch (t: Throwable) {
            advanceProgress(params, -processedSize)
            throw t
        }

        if (stored.not()) {
            advanceProgress(params, -processedSize)
            throw IllegalStateException("Downloaded nothing: $pathDisplay")
        }

        completeTarget(params, size - processedSize) { storedTargetsCount++ }
    }

    private suspend fun completeTarget(
        params: JobParameters,
        deltaSize: Long,
        count: () -> Unit,
    ) {
        progressMutex.withLock {
            count()
            completedTargetsCount++
            processedFilesSize += deltaSize
            sampleProgress(params, force = true)
        }
    }

    private suspend fun advanceProgress(params: JobParameters, deltaSize: Long) {
        progressMutex.withLock {
            processedFilesSize += deltaSize
            sampleProgress(params)
        }
    }

    private fun sampleProgress(params: JobParameters, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastProgressSampledTime
        if (elapsed < PROGRESS_UPDATE_THRESHOLD_MILLIS) {
            if (force) updateProgress(params)
            return
        }

        if (lastProgressSampledTime > 0) {
            speeds = (speeds +
                    ((processedFilesSize - lastProcessedFileSize).toFloat() / elapsed)).takeLast(10)
        }
        lastProgressSampledTime = now
        lastProcessedFileSize = processedFilesSize
        updateProgress(params)
    }

    private suspend fun addCurrentPath(params: JobParameters, path: String) {
        progressMutex.withLock {
            currentPaths.add(path)
            updateNotification(params)
        }
    }

    private suspend fun removeCurrentPath(path: String) {
        progressMutex.withLock {
            currentPaths.remove(path)
        }
    }

    private suspend fun savePendingRetrieve(rootPath: String, needDownloaded: Boolean) {
        if (storedTargetsCount < 1 || retryGeneration >= MAX_RETRY_GENERATION) {
            Timber.d(
                "qgeck gave up retrieving $expiredTargetsCount expired targets " +
                        "at generation $retryGeneration"
            )
            applicationContext.setPendingMediaRetrieve(null)
            return
        }

        applicationContext.setPendingMediaRetrieve(
            PendingMediaRetrieve(
                rootPath = rootPath,
                needDownloaded = needDownloaded,
                generation = retryGeneration + 1,
            )
        )
    }

    private fun updateProgress(params: JobParameters) {
        if (isStopped) return

        SyncProgressState.update(
            SyncProgress(
                title = getString(R.string.progress_title_retrieve_media),
                progressFraction = progressFraction,
                remainingFiles = remainingFilesCount,
                skippedFiles = skippedTargetsCount,
                totalFilesSize = totalFilesSize,
                processedFilesSize = processedFilesSize,
                remainingDuration = remainingDuration,
                paths = currentPathNames,
            )
        )
        updateNotification(params)
    }

    private fun updateNotification(params: JobParameters) {
        if (isStopped) return

        val now = System.currentTimeMillis()
        if (now - lastNotifiedTime < NOTIFICATION_UPDATE_THRESHOLD_MILLIS) return

        lastNotifiedTime = now
        runCatching {
            setNotification(
                params,
                NOTIFICATION_ID_RETRIEVE,
                getNotification(notificationBitmap),
                JOB_END_NOTIFICATION_POLICY_DETACH
            )
        }.onFailure { Timber.e(it) }
    }

    private fun Bitmap.drawProgressIcon(progressFraction: Float, seed: Long): Bitmap {
        val maxTileNumber = 24
        val tileNumber = (maxTileNumber * progressFraction).toInt()
        val canvas = Canvas(this)
        val paint = Paint().apply {
            isAntiAlias = true
        }
        val offset = PointF(canvas.width * 0.5f, canvas.height * 0.5f)
        val innerR = canvas.width * 0.35f
        val outerR = canvas.width * 0.45f
        val random = Random(seed)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        repeat(tileNumber + 1) {
            val start = 3
            val angleLeft = ((start + it) * PI / 12).toFloat()
            val angleRight = ((start + it + 1) * PI / 12).toFloat()
            val path = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                moveTo(offset.x + outerR * cos(angleLeft), offset.y + outerR * sin(angleLeft))
                lineTo(offset.x + outerR * cos(angleRight), offset.y + outerR * sin(angleRight))
                lineTo(offset.x + innerR * cos(angleRight), offset.y + innerR * sin(angleRight))
                lineTo(offset.x + innerR * cos(angleLeft), offset.y + innerR * sin(angleLeft))
                close()
            }
            val alphaC =
                if (it == tileNumber) maxTileNumber * progressFraction % 1f
                else 1f
            paint.color = Color.argb(
                (150 * alphaC).toInt(),
                random.nextInt(255),
                random.nextInt(255),
                random.nextInt(255)
            )
            canvas.drawPath(path, paint)
        }

        return this
    }

    private fun getNotification(bitmap: Bitmap): Notification {
        val text = currentPathNames.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.let {
                getString(
                    R.string.notification_text_retriever_with_path,
                    remainingFilesCount,
                    "${processedFilesSize.toFloat().getReadableStringWithUnit()}B",
                    "${totalFilesSize.toFloat().getReadableStringWithUnit()}B",
                    skippedTargetsCount,
                    remainingDuration.getTimeString(),
                    it
                )
            } ?: getString(R.string.notification_text_retriever, remainingFilesCount)

        return getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setLargeIcon(bitmap.drawProgressIcon(progressFraction, seed))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    App.REQUEST_CODE_LAUNCH_APP,
                    LauncherActivity.createIntent(applicationContext),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setContentTitle(getString(R.string.notification_title_retriever))
            .setContentText(text)
            .build()
    }
}
