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
import androidx.core.app.NotificationCompat
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
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.getTimeString
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveAudioFile
import com.geckour.q.util.saveTempAudioFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import timber.log.Timber
import java.io.File

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
    private var processedFilesSize = 0L
    private val files = mutableListOf<FileMetadata>()
    private val remainingFilesCount: Int
        get() {
            return files.size -
                    (files.indices
                        .firstOrNull {
                            processedFilesSize < files.take(it + 1).sumOf { it.size }
                        }
                        ?: 0)
        }
    private val progressFraction get() = processedFilesSize.toFloat() / files.sumOf { it.size }
    private val remainingDuration get() = ((files.sumOf { it.size } - processedFilesSize) / speeds.average()).toLong()
    private var currentPath: String? = null
    private val notificationBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
    private var seed: Long = -1
    private var speeds = listOf<Float>()

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
                    progressFraction = 0f
                )
            )

            files.clear()
            retrieveAudioFilePaths(rootPath, dbxClient, needDownloaded)
            files.forEach {
                if (isStopped) {
                    return Result.success(
                        Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
                    )
                }

                it.storeMediaInfo(dbxClient, needDownloaded)
            }

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            delay(200)
            return Result.success(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
        }

        return Result.failure(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        if (Build.VERSION.SDK_INT < 29) {
            ForegroundInfo(
                NOTIFICATION_ID_RETRIEVE,
                getNotification(
                    currentPath,
                    progressFraction,
                    seed,
                    notificationBitmap
                )
            )
        } else {
            ForegroundInfo(
                NOTIFICATION_ID_RETRIEVE,
                getNotification(
                    currentPath,
                    progressFraction,
                    seed,
                    notificationBitmap
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }

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
                        if (needDownloaded || (db.trackDao()
                                .getByDropboxPath(metadata.pathLower)?.track?.lastModified
                                ?: 0) < metadata.serverModified.time
                        ) {
                            files.add(metadata)
                            setProgress(
                                createProgressData(
                                    title = applicationContext.getString(R.string.progress_title_retrieve_media),
                                    progressFraction = progressFraction,
                                    remainingFiles = remainingFilesCount,
                                    processedFileSize = processedFilesSize
                                )
                            )
                            setForeground(getForegroundInfo())
                        }
                    }

                    else -> Unit
                }
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
        progressFraction: Float,
        seed: Long,
        bitmap: Bitmap
    ): Notification {
        val text = path?.let {
            applicationContext.getString(
                R.string.notification_text_retriever_with_path,
                remainingFilesCount,
                "${processedFilesSize.toFloat().getReadableStringWithUnit()}B",
                remainingDuration.getTimeString(),
                it
            )
        } ?: applicationContext.getString(
            R.string.notification_text_retriever,
            remainingFilesCount
        )
        return applicationContext.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
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
            .setContentTitle(applicationContext.getString(R.string.notification_title_retriever))
            .setContentText(text)
            .build()
    }

    private suspend fun FileMetadata.storeMediaInfo(
        client: DbxClientV2,
        needDownloaded: Boolean,
    ) {
        if (name.isAudioFilePath) {
            setForeground(getForegroundInfo())
            currentPath = this.pathDisplay

            runCatching {
                storeMediaInfo(client, this@storeMediaInfo, needDownloaded)
            }.onFailure { t ->
                if (t is RateLimitException) {
                    Thread.sleep(t.backoffMillis)
                    this.storeMediaInfo(client, needDownloaded)
                } else {
                    Timber.e(t)
                    return
                }
            }
        }
    }

    private suspend fun updateProgress() {
        setProgress(
            createProgressData(
                title = applicationContext.getString(R.string.progress_title_retrieve_media),
                progressFraction = progressFraction,
                remainingFiles = remainingFilesCount,
                processedFileSize = processedFilesSize,
                remainingDuration = remainingDuration,
                path = currentPath,
            )
        )
        setForeground(getForegroundInfo())
    }

    private suspend fun storeMediaInfo(
        client: DbxClientV2,
        dropboxMetadata: FileMetadata,
        needDownloaded: Boolean
    ) {
        val url = client.files().getTemporaryLink(dropboxMetadata.pathLower).link
        val currentTime = System.currentTimeMillis()

        val processedFilesSizeSnapshot = processedFilesSize
        var lastProgressSampledTime = currentTime
        val fileAndProgressFlow = if (needDownloaded) client.saveAudioFile(
            applicationContext,
            dropboxMetadata.id,
            dropboxMetadata.pathLower
        ) else client.saveTempAudioFile(
            applicationContext, dropboxMetadata.pathLower
        )
        var target: File? = null
        fileAndProgressFlow
            .onCompletion {
                target?.let {
                    it.storeMediaInfo(
                        applicationContext,
                        if (needDownloaded) Uri.fromFile(it).toString() else url,
                        db.trackDao().getByDropboxPath(dropboxMetadata.pathLower)?.track?.id,
                        null,
                        dropboxMetadata.pathLower,
                        currentTime + DROPBOX_EXPIRES_IN,
                        dropboxMetadata.serverModified.time
                    )
                }
                processedFilesSize = processedFilesSizeSnapshot + dropboxMetadata.size
                updateProgress()
            }
            .collectLatest { (file, processed) ->
                if (processed == null) {
                    return@collectLatest
                }
                target = file
                val now = System.currentTimeMillis()
                processedFilesSize = processedFilesSizeSnapshot + processed
                speeds =
                    (speeds + (processed.toFloat() / (now - lastProgressSampledTime))).take(10)
                lastProgressSampledTime = now
                updateProgress()
            }
    }
}