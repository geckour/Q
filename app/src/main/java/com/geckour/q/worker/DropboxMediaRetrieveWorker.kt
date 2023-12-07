package com.geckour.q.worker

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dropbox.core.RateLimitException
import com.dropbox.core.ServerException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.DROPBOX_EXPIRES_IN
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getExtension
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveAudioFile
import com.geckour.q.util.saveTempAudioFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import timber.log.Timber

class DropboxMediaRetrieveWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {

    companion object {

        const val TAG = "dropbox_media_retrieve_worker"
        const val KEY_ROOT_PATH = "key_root_path"
        const val KEY_NEED_DOWNLOADED = "key_need_downloaded"
    }

    private val db = DB.getInstance(context)
    private var processedFilesCount = 0
    private val files = mutableListOf<FileMetadata>()
    private var totalFilesCount = 0
    private var totalFilesSize = 0L
    private var currentPath: String? = null
    private val currentProcessingSpeeds = mutableListOf<ProcessingSpeed>()
    private val notificationBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
    private var seed: Long = -1
    private var startTime: Long = 0

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33
            || applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            val dbxClient = obtainDbxClient(applicationContext).firstOrNull()
                ?: return Result.failure(
                    Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
                )

            try {
                setForeground(getForegroundInfo())
            } catch (t: Throwable) {
                return Result.failure(
                    Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
                )
            }

            seed = System.currentTimeMillis()
            Timber.d("qgeck Dropbox media retrieve worker started")

            val rootPath = requireNotNull(inputData.getString(KEY_ROOT_PATH))
            Timber.d("qgeck rootPath: $rootPath")
            val needDownloaded = requireNotNull(inputData.getBoolean(KEY_NEED_DOWNLOADED, false))

            setProgress(
                createProgressData(
                    title = applicationContext.getString(R.string.progress_title_retrieve_media),
                    numerator = 0
                )
            )

            files.clear()
            retrieveAudioFilePaths(rootPath, dbxClient, needDownloaded)
            totalFilesSize = files.sumOf { it.size }
            startTime = System.currentTimeMillis()
            files.forEach {
                if (isStopped) {
                    return Result.success(
                        Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
                    )
                }

                it.storeMediaInfo(dbxClient, startTime, needDownloaded)
            }

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            delay(200)
            return Result.success(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
        }

        return Result.failure(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID_RETRIEVE,
            getNotification(currentPath, processedFilesCount, files.size, seed, notificationBitmap),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    private suspend fun retrieveAudioFilePaths(
        root: String,
        client: DbxClientV2,
        needDownloaded: Boolean
    ) {
        if (isStopped) return
        try {
            var fileAndFolders = client.files().listFolder(root)
            while (true) {
                if (fileAndFolders.hasMore.not()) break

                fileAndFolders = client.files().listFolderContinue(fileAndFolders.cursor)
            }

            fileAndFolders.entries.forEach { metadata ->
                when (metadata) {
                    is FolderMetadata -> {
                        retrieveAudioFilePaths(metadata.pathLower, client, needDownloaded)
                    }

                    is FileMetadata -> {
                        totalFilesCount++
                        if (needDownloaded || (db.trackDao()
                                .getByDropboxPath(metadata.pathLower)?.track?.lastModified
                                ?: 0) < metadata.serverModified.time
                        ) {
                            totalFilesSize += metadata.size
                            setProgress(
                                createProgressData(
                                    title = applicationContext.getString(R.string.progress_title_retrieve_media),
                                    numerator = processedFilesCount,
                                    denominator = files.size,
                                    totalFiles = totalFilesCount,
                                    remainingFileSize = totalFilesSize
                                )
                            )
                            files.add(metadata)
                        }
                    }

                    else -> Unit
                }
            }.apply {
                setForeground(getForegroundInfo())
            }
        } catch (e: RateLimitException) {
            delay(e.backoffMillis)
            retrieveAudioFilePaths(root, client, needDownloaded)
        } catch (e: ServerException) {
            delay(3000)
            retrieveAudioFilePaths(root, client, needDownloaded)
        }
    }

    private val String.isAudioFilePath: Boolean
        get() = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(this.getExtension())
            ?.contains("audio") == true

    private fun getNotification(
        path: String?,
        progressNumerator: Int,
        progressDenominator: Int,
        seed: Long,
        bitmap: Bitmap
    ): Notification =
        applicationContext.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setLargeIcon(bitmap.drawProgressIcon(progressNumerator, progressDenominator, seed))
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
            .setContentTitle(applicationContext.getString(R.string.notification_title_retriever))
            .setContentText(
                path?.let {
                    applicationContext.getString(
                        R.string.notification_text_retriever_with_path,
                        progressNumerator,
                        progressDenominator,
                        it
                    )
                } ?: applicationContext.getString(
                    R.string.notification_text_retriever,
                    progressNumerator,
                    progressDenominator
                )
            )
            .build()

    private suspend fun FileMetadata.storeMediaInfo(
        client: DbxClientV2,
        startTime: Long,
        needDownloaded: Boolean
    ) {
        if (name.isAudioFilePath) {
            processedFilesCount++
            if (currentProcessingSpeeds.isEmpty()) {
                currentProcessingSpeeds.add(
                    ProcessingSpeed(
                        timing = System.currentTimeMillis(),
                        speed = -1.0
                    )
                )
            }
            setForeground(getForegroundInfo())
            currentPath = this.pathDisplay

            runCatching {
                storeMediaInfo(client, this@storeMediaInfo, needDownloaded)
                currentProcessingSpeeds.lastOrNull()?.let {
                    if (currentProcessingSpeeds.size > 6) {
                        currentProcessingSpeeds.drop(1)
                    }
                    currentProcessingSpeeds.add(
                        ProcessingSpeed.calcProcessingSpeed(
                            prev = it,
                            size = this@storeMediaInfo.size
                        )
                    )
                }

                val remainingFileSize = files
                    .takeLast(files.size - processedFilesCount)
                    .sumOf { it.size }
                val targetProcessingSpeeds = currentProcessingSpeeds.filter { it.speed > 0 }
                val averageSpeed = targetProcessingSpeeds.sumOf { it.speed } / targetProcessingSpeeds.size
                setProgress(
                    createProgressData(
                        title = applicationContext.getString(R.string.progress_title_retrieve_media),
                        numerator = processedFilesCount,
                        denominator = files.size,
                        totalFiles = totalFilesCount,
                        path = pathDisplay,
                        remaining = (remainingFileSize / averageSpeed).toLong(),
                        remainingFileSize = remainingFileSize
                    )
                )
            }.onFailure { t ->
                if (t is RateLimitException) {
                    Thread.sleep(t.backoffMillis)
                    this.storeMediaInfo(client, startTime, needDownloaded)
                } else {
                    Timber.e(t)
                    return
                }
            }
        }
    }

    private suspend fun storeMediaInfo(
        client: DbxClientV2,
        dropboxMetadata: FileMetadata,
        needDownloaded: Boolean
    ): Long {
        val url = client.files().getTemporaryLink(dropboxMetadata.pathLower).link
        val currentTime = System.currentTimeMillis()

        return client.let {
            val file = if (needDownloaded) it.saveAudioFile(
                applicationContext,
                dropboxMetadata.id,
                dropboxMetadata.pathLower
            ) else it.saveTempAudioFile(applicationContext, dropboxMetadata.pathLower)
            file.storeMediaInfo(
                applicationContext,
                if (needDownloaded) Uri.fromFile(file).toString() else url,
                db.trackDao().getByDropboxPath(dropboxMetadata.pathLower)?.track?.id,
                null,
                dropboxMetadata.pathLower,
                currentTime + DROPBOX_EXPIRES_IN,
                dropboxMetadata.serverModified.time
            )
        }
    }

    private data class ProcessingSpeed(
        val timing: Long,
        val speed: Double
    ) {

        companion object {

            fun calcProcessingSpeed(prev: ProcessingSpeed, size: Long): ProcessingSpeed {
                val now = System.currentTimeMillis()
                return ProcessingSpeed(
                    timing = now,
                    speed = size.toDouble() / (now - prev.timing)
                )
            }
        }
    }
}