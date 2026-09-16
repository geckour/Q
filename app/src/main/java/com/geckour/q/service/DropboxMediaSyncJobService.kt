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
import com.geckour.q.domain.model.SyncProgress
import com.geckour.q.domain.model.SyncSizeAlert
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.DROPBOX_EXPIRES_IN
import com.geckour.q.util.DownloadFailedException
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.SyncProgressState
import com.geckour.q.util.SyncSizeAlertState
import com.geckour.q.util.getExtension
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isDownloaded
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveAudioFileFromUrl
import com.geckour.q.util.saveTempAudioFileFromUrl
import com.geckour.q.worker.storeMediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

        private const val KEY_TOKEN = "key_token"

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

        private const val REQUESTS_DIR_NAME = "dropbox_sync_requests"

        private const val AVAILABLE_SIZE_USAGE_LIMIT_RATIO = 0.9

        private val requestJson = Json { ignoreUnknownKeys = true }

        private val requestLock = Any()

        private var activeSession: DropboxMediaSyncJobService.Session? = null

        private var pendingSizeApproval: CompletableDeferred<Boolean>? = null

        fun schedule(context: Context, rootPath: String, needDownloaded: Boolean): Boolean =
            enqueue(context, SyncRequest(rootPath = rootPath, needDownloaded = needDownloaded))

        fun schedule(context: Context, targetPaths: List<String>): Boolean {
            if (targetPaths.isEmpty()) return false

            return enqueue(context, SyncRequest(targetPaths = targetPaths, needDownloaded = true))
        }

        fun resume(context: Context): Boolean = synchronized(requestLock) {
            if (activeSession != null || isScheduled(context) || requestFiles(context).isEmpty()) {
                return@synchronized false
            }

            scheduleJob(context)
        }

        fun cancel(context: Context) {
            synchronized(requestLock) {
                requestFiles(context).forEach { it.delete() }
                activeSession?.stop()
                activeSession = null
                pendingSizeApproval = null
                context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            }
            SyncSizeAlertState.update(null)
        }

        fun respondSizeConfirmation(approved: Boolean) {
            val approval = synchronized(requestLock) {
                pendingSizeApproval.also { pendingSizeApproval = null }
            }
            SyncSizeAlertState.update(null)
            approval?.complete(approved)
        }

        private fun enqueue(context: Context, request: SyncRequest): Boolean =
            synchronized(requestLock) {
                writeRequest(context, request)
                activeSession != null || scheduleJob(context)
            }

        private fun scheduleJob(context: Context): Boolean {
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
                .setExtras(
                    PersistableBundle().apply {
                        putString(KEY_TOKEN, UUID.randomUUID().toString())
                    }
                )
                .build()

            val result = context.getSystemService(JobScheduler::class.java)?.schedule(jobInfo)
            Timber.d("qgeck scheduled sync job: $result")

            return result == JobScheduler.RESULT_SUCCESS
        }

        private fun isScheduled(context: Context): Boolean =
            context.getSystemService(JobScheduler::class.java)
                ?.allPendingJobs
                ?.any { it.id == JOB_ID } == true

        private fun requestFiles(context: Context): List<File> =
            File(context.dataDir, REQUESTS_DIR_NAME).listFiles()?.sortedBy { it.name }.orEmpty()

        private fun writeRequest(context: Context, request: SyncRequest) {
            val dir = File(context.dataDir, REQUESTS_DIR_NAME)
            if (dir.exists().not()) dir.mkdir()

            File(dir, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
                .writeText(requestJson.encodeToString(request))
        }
    }

    @Serializable
    private data class SyncRequest(
        val rootPath: String? = null,
        val targetPaths: List<String> = emptyList(),
        val needDownloaded: Boolean,
        val generation: Int = 0,
        val sizeConfirmed: Boolean = false,
    )

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
    private val dbMutex = Mutex()

    private val notificationBitmap =
        createBitmap(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)

    override fun onStartJob(params: JobParameters): Boolean {
        val session = Session(params)
        synchronized(requestLock) { activeSession = session }

        setNotification(
            params,
            NOTIFICATION_ID_RETRIEVE,
            getNotification(getString(R.string.notification_text_retriever, 0), 0f, session.seed),
            JOB_END_NOTIFICATION_POLICY_REMOVE
        )

        session.start()

        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Timber.d("qgeck sync job stopped: ${params.stopReason}")

        val token = params.extras.getString(KEY_TOKEN)
        synchronized(requestLock) {
            activeSession?.takeIf { it.token == token }?.let {
                it.stop()
                activeSession = null
            }
        }

        return true
    }

    override fun onDestroy() {
        scope.cancel()

        super.onDestroy()
    }

    private inner class Session(val params: JobParameters) {

        val token: String? = params.extras.getString(KEY_TOKEN)
        val seed = System.currentTimeMillis()

        @Volatile
        var isStopped = false
            private set

        private var job: Job? = null
        private val failedRequestFiles = mutableSetOf<File>()

        fun start() {
            job = scope.launch { process() }
        }

        fun stop() {
            isStopped = true
            job?.cancel()
            SyncProgressState.update(null)
        }

        private suspend fun process() {
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()

                    val (file, request) = takeRequest() ?: break
                    val succeeded = runCatching { SyncTask(this, file, request).execute() }
                        .onFailure { if (it !is CancellationException) Timber.e(it) }
                        .isSuccess
                    if (isStopped) return

                    if (succeeded) file.delete()
                    else failedRequestFiles += file
                }

                SyncProgressState.update(null)
                jobFinished(params, false)
            } finally {
                synchronized(requestLock) {
                    if (activeSession === this) activeSession = null
                }
            }
        }

        private fun takeRequest(): Pair<File, SyncRequest>? = synchronized(requestLock) {
            requestFiles(this@DropboxMediaSyncJobService)
                .filterNot { it in failedRequestFiles }
                .firstNotNullOfOrNull { file ->
                    runCatching {
                        file to requestJson.decodeFromString<SyncRequest>(file.readText())
                    }.onFailure {
                        Timber.e(it)
                        file.delete()
                    }.getOrNull()
                }
                ?: run {
                    if (activeSession === this) activeSession = null
                    null
                }
        }
    }

    private class SizeDeclinedException : Exception()

    private inner class SyncTask(
        private val session: Session,
        private val requestFile: File,
        private val request: SyncRequest,
    ) {

        private val params get() = session.params
        private val isStopped get() = session.isStopped

        private val targets = mutableListOf<RetrieveTarget>()
        private val targetsMutex = Mutex()
        private val progressMutex = Mutex()
        private val linkFetchSemaphore = Semaphore(LINK_FETCH_CONCURRENCY)
        private val storeSemaphore = Semaphore(STORE_CONCURRENCY)
        private val currentPaths = mutableListOf<String>()
        private val availableSize by lazy { applicationContext.dataDir.usableSpace }

        private var sizeApproval: CompletableDeferred<Boolean>? = null
        private var sizeApproved = false
        private var isRetrieving = true
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

        suspend fun execute() {
            val client = obtainDbxClient(applicationContext).firstOrNull() ?: return

            updateProgress()

            try {
                coroutineScope {
                    if (request.rootPath != null) {
                        retrieveAudioFilePaths(this, request.rootPath, client)
                    } else {
                        retrieveTargetPaths(this, client)
                    }
                }
            } catch (e: SizeDeclinedException) {
                Timber.d("qgeck declined to download $totalFilesSize bytes")
                return
            } finally {
                clearSizeApproval()
            }
            if (isStopped) return

            Timber.d("qgeck retrieved files count: ${targets.size}")

            runCatching {
                updateEstimatedNetworkBytes(
                    params,
                    totalFilesSize,
                    JobInfo.NETWORK_BYTES_UNKNOWN.toLong()
                )
            }.onFailure { Timber.e(it) }

            isRetrieving = false
            updateProgress()

            coroutineScope {
                targets.forEach { target ->
                    launch(Dispatchers.IO) {
                        storeSemaphore.withPermit {
                            if (isStopped) return@withPermit

                            target.storeMediaInfo()
                        }
                    }
                }
            }
            if (isStopped) return

            if (expiredTargetsCount > 0) enqueueRetry()

            Timber.d("qgeck track in db count: ${db.trackDao().count()}")
        }

        private suspend fun awaitSizeApproval() {
            val approval = targetsMutex.withLock {
                currentCoroutineContext().ensureActive()

                if (request.needDownloaded.not()) return

                if (totalFilesSize > availableSize) {
                    Timber.d("qgeck download size exceeds available size: $totalFilesSize bytes")
                    DropboxMediaSyncJobService.cancel(applicationContext)
                    SyncSizeAlertState.update(
                        SyncSizeAlert.Exceeded(
                            downloadSize = totalFilesSize,
                            availableSize = availableSize,
                        )
                    )
                    currentCoroutineContext().ensureActive()
                }

                if (request.sizeConfirmed ||
                    sizeApproved ||
                    totalFilesSize < availableSize * AVAILABLE_SIZE_USAGE_LIMIT_RATIO
                ) {
                    return
                }

                sizeApproval ?: CompletableDeferred<Boolean>().also { approval ->
                    sizeApproval = approval
                    synchronized(requestLock) { pendingSizeApproval = approval }
                    SyncSizeAlertState.update(
                        SyncSizeAlert.Confirmation(
                            downloadSize = totalFilesSize,
                            availableSize = availableSize,
                        )
                    )
                }
            }

            if (approval.await().not()) throw SizeDeclinedException()

            targetsMutex.withLock {
                if (sizeApproved) return

                sizeApproved = true
                synchronized(requestLock) {
                    requestFile.writeText(
                        requestJson.encodeToString(request.copy(sizeConfirmed = true))
                    )
                }
            }
        }

        private fun clearSizeApproval() {
            val approval = sizeApproval ?: return
            synchronized(requestLock) {
                if (pendingSizeApproval !== approval) return

                pendingSizeApproval = null
            }
            SyncSizeAlertState.update(null)
        }

        private fun retrieveTargetPaths(scope: CoroutineScope, client: DbxClientV2) {
            request.targetPaths.forEach { targetPath ->
                scope.launch(Dispatchers.IO) {
                    linkFetchSemaphore.withPermit {
                        if (isStopped) return@withPermit

                        awaitSizeApproval()

                        val metadata = runCatching {
                            client.files().getMetadata(targetPath) as? FileMetadata
                        }.onFailure { Timber.e(it) }.getOrNull() ?: return@withPermit
                        val target = metadata.toRetrieveTargetIfNeeded(client)
                            ?: return@withPermit

                        targetsMutex.withLock {
                            targets.add(target)
                            totalFilesSize += target.size
                            updateProgress()
                        }

                        awaitSizeApproval()
                    }
                }
            }
        }

        private suspend fun retrieveAudioFilePaths(
            scope: CoroutineScope,
            root: String,
            client: DbxClientV2,
            retryCount: Int = 0,
        ) {
            if (isStopped) return
            awaitSizeApproval()
            try {
                var fileAndFolders = client.files().listFolder(root)
                val entries = fileAndFolders.entries.toMutableList()
                while (fileAndFolders.hasMore) {
                    if (isStopped) return
                    awaitSizeApproval()

                    fileAndFolders = client.files().listFolderContinue(fileAndFolders.cursor)
                    entries += fileAndFolders.entries
                }

                entries.filterIsInstance<FileMetadata>()
                    .filter { it.name.isAudioFilePath }
                    .forEach { metadata ->
                        scope.launch(Dispatchers.IO) {
                            linkFetchSemaphore.withPermit {
                                if (isStopped) return@withPermit

                                awaitSizeApproval()

                                val target = metadata.toRetrieveTargetIfNeeded(client)
                                    ?: return@withPermit

                                targetsMutex.withLock {
                                    targets.add(target)
                                    totalFilesSize += target.size
                                    updateProgress()
                                }

                                awaitSizeApproval()
                            }
                        }
                    }

                entries.filterIsInstance<FolderMetadata>().forEach { metadata ->
                    retrieveAudioFilePaths(scope, metadata.pathLower ?: return@forEach, client)
                }
            } catch (e: RateLimitException) {
                if (retryCount >= MAX_RATE_LIMIT_RETRY_COUNT) throw e
                delay(e.backoffMillis.milliseconds)
                retrieveAudioFilePaths(scope, root, client, retryCount + 1)
            } catch (e: ServerException) {
                if (retryCount >= MAX_RETRY_COUNT) throw e
                delay(3000.milliseconds)
                retrieveAudioFilePaths(scope, root, client, retryCount + 1)
            } catch (e: NetworkIOException) {
                if (retryCount >= MAX_RETRY_COUNT) throw e
                delay(3000.milliseconds)
                retrieveAudioFilePaths(scope, root, client, retryCount + 1)
            }
        }

        private suspend fun FileMetadata.toRetrieveTargetIfNeeded(
            client: DbxClientV2,
        ): RetrieveTarget? {
            val path = pathLower ?: return null
            val existingTrack = db.trackDao().getByDropboxPath(path)?.track
            val existingTrackLastModified = existingTrack?.lastModified
            if (existingTrackLastModified != null &&
                existingTrackLastModified >= serverModified.time &&
                (request.needDownloaded.not() || existingTrack.isDownloaded)
            ) {
                targetsMutex.withLock {
                    skippedTargetsCount++
                    updateProgress()
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

        private suspend fun RetrieveTarget.storeMediaInfo() {
            val existingTrack = db.trackDao().getByDropboxPath(pathLower)?.track
            if (existingTrack != null &&
                existingTrack.lastModified >= serverModifiedAt &&
                (request.needDownloaded.not() || existingTrack.isDownloaded)
            ) {
                completeTarget(size) { skippedTargetsCount++ }
                return
            }

            if (urlExpiredAt <= System.currentTimeMillis()) {
                Timber.d("qgeck link has expired: $pathDisplay")
                completeTarget(size) { expiredTargetsCount++ }
                return
            }

            addCurrentPath(pathDisplay)
            try {
                var retryCount = 0
                while (true) {
                    val failure = runCatching { download(existingTrack) }
                        .exceptionOrNull()
                        ?: return

                    when {
                        failure is CancellationException -> throw failure

                        failure is DownloadFailedException && failure.code in 400..499 -> {
                            Timber.e(failure)
                            completeTarget(size) { expiredTargetsCount++ }
                            return
                        }

                        retryCount >= MAX_RETRY_COUNT -> {
                            Timber.e(failure)
                            completeTarget(size) { }
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

        private suspend fun RetrieveTarget.download(existingTrack: Track?) {
            var processedSize = 0L
            var stored = false
            var target: File? = null
            val fileAndProgressFlow =
                if (request.needDownloaded) {
                    saveAudioFileFromUrl(applicationContext, id, pathLower, url)
                } else {
                    saveTempAudioFileFromUrl(applicationContext, id, pathLower, url)
                }

            try {
                fileAndProgressFlow
                    .onCompletion { cause ->
                        if (cause != null) return@onCompletion

                        target?.let { file ->
                            dbMutex.withLock {
                                file.storeMediaInfo(
                                    applicationContext,
                                    if (request.needDownloaded) Uri.fromFile(file).toString()
                                    else url,
                                    existingTrack?.id,
                                    null,
                                    pathLower,
                                    urlExpiredAt,
                                    serverModifiedAt
                                )
                            }
                            if (request.needDownloaded.not()) file.delete()
                            stored = true
                        }
                    }
                    .collectLatest { (file, processed) ->
                        if (processed == null) {
                            return@collectLatest
                        }
                        target = file
                        advanceProgress(processed - processedSize)
                        processedSize = processed
                    }
            } catch (t: Throwable) {
                advanceProgress(-processedSize)
                throw t
            }

            if (stored.not()) {
                advanceProgress(-processedSize)
                throw IllegalStateException("Downloaded nothing: $pathDisplay")
            }

            completeTarget(size - processedSize) { storedTargetsCount++ }
        }

        private suspend fun completeTarget(deltaSize: Long, count: () -> Unit) {
            progressMutex.withLock {
                count()
                completedTargetsCount++
                processedFilesSize += deltaSize
                sampleProgress(force = true)
            }
        }

        private suspend fun advanceProgress(deltaSize: Long) {
            progressMutex.withLock {
                processedFilesSize += deltaSize
                sampleProgress()
            }
        }

        private fun sampleProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastProgressSampledTime
            if (elapsed < PROGRESS_UPDATE_THRESHOLD_MILLIS) {
                if (force) updateProgress()
                return
            }

            if (lastProgressSampledTime > 0) {
                speeds = (speeds +
                        ((processedFilesSize - lastProcessedFileSize).toFloat() / elapsed))
                    .takeLast(10)
            }
            lastProgressSampledTime = now
            lastProcessedFileSize = processedFilesSize
            updateProgress()
        }

        private suspend fun addCurrentPath(path: String) {
            progressMutex.withLock {
                currentPaths.add(path)
                updateNotification()
            }
        }

        private suspend fun removeCurrentPath(path: String) {
            progressMutex.withLock {
                currentPaths.remove(path)
            }
        }

        private fun enqueueRetry() {
            if (storedTargetsCount < 1 || request.generation >= MAX_RETRY_GENERATION) {
                Timber.d(
                    "qgeck gave up retrieving $expiredTargetsCount expired targets " +
                            "at generation ${request.generation}"
                )
                return
            }

            synchronized(requestLock) {
                writeRequest(
                    applicationContext,
                    request.copy(generation = request.generation + 1)
                )
            }
        }

        private fun updateProgress() {
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
            updateNotification()
        }

        private fun updateNotification() {
            if (isStopped) return

            val now = System.currentTimeMillis()
            if (now - lastNotifiedTime < NOTIFICATION_UPDATE_THRESHOLD_MILLIS) return

            lastNotifiedTime = now
            runCatching {
                setNotification(
                    params,
                    NOTIFICATION_ID_RETRIEVE,
                    getNotification(notificationText, progressFraction, session.seed),
                    JOB_END_NOTIFICATION_POLICY_REMOVE
                )
            }.onFailure { Timber.e(it) }
        }

        private val notificationText: String
            get() = currentPathNames.takeIf { it.isNotEmpty() }
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

    private fun getNotification(text: String, progressFraction: Float, seed: Long): Notification =
        getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setLargeIcon(notificationBitmap.drawProgressIcon(progressFraction, seed))
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
