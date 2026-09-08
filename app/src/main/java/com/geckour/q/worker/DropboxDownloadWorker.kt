package com.geckour.q.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dropbox.core.RateLimitException
import com.dropbox.core.ServerException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.getReadableStringWithUnit
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveAudioFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onCompletion
import org.koin.core.component.KoinComponent
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.milliseconds


internal const val DROPBOX_DOWNLOAD_WORKER_NAME = "DropboxDownloadWorker"

class DropboxDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {

    companion object {

        const val TAG = "dropbox_download_worker"

        const val KEY_TARGET_PATHS = "key_target_paths"

        private const val PROGRESS_UPDATE_THRESHOLD_MILLIS = 200
    }

    private var targetPaths = emptyList<String>()
    private var files = emptyList<FileMetadata>()
    private var seed = 0L
    private var currentPath: String? = null
    private var remainingFilesCount = 0
    private var processedFilesSize = 0L
    private var lastProcessedFileSize = processedFilesSize
    private var speeds = listOf<Float>()
    private val remainingDuration get() = ((files.sumOf { it.size } - processedFilesSize) / speeds.average()).toLong()
    private val progressFraction get() = processedFilesSize.toFloat() / files.sumOf { it.size }
    private val notificationBitmap = createBitmap(1000, 1000)

    override suspend fun doWork(): Result {
        val dbxClient = obtainDbxClient(applicationContext).firstOrNull()
            ?: return Result.failure(
                Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
            )

        try {
            setForeground(getForegroundInfo())
        } catch (_: Throwable) {
            return Result.failure(
                Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
            )
        }

        val startTime = System.currentTimeMillis()
        seed = startTime

        Timber.d("qgeck Dropbox download worker started")
        val db = DB.getInstance(applicationContext)

        setProgress(
            createProgressData(
                title = applicationContext.getString(R.string.progress_title_download_dropbox),
                progressFraction = 0f
            )
        )

        targetPaths = requireNotNull(inputData.getStringArray(KEY_TARGET_PATHS)).toList()
        Timber.d("qgeck target paths: $targetPaths")

        download(db, targetPaths, startTime, dbxClient)

        Timber.d("qgeck track in db count: ${db.trackDao().count()}")
        delay(200.milliseconds)
        return Result.success(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID_RETRIEVE,
            getNotification(notificationBitmap),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    private suspend fun download(
        db: DB,
        targetPaths: List<String>,
        startTime: Long,
        client: DbxClientV2
    ) {
        if (isStopped) return
        try {
            files = targetPaths.mapNotNull { targetPath ->
                client.files().getMetadata(targetPath) as? FileMetadata
            }
            files.forEach { fileMetadata ->
                val path = fileMetadata.pathLower ?: return@forEach
                currentPath = fileMetadata.pathDisplay
                setForeground(getForegroundInfo())
                val processedFilesSizeSnapshot = processedFilesSize
                var lastProgressSampledTime = System.currentTimeMillis()
                var target: File? = null
                client.saveAudioFile(
                    applicationContext,
                    fileMetadata.id,
                    path
                ).onCompletion {
                    processedFilesSize = processedFilesSizeSnapshot + fileMetadata.size
                    updateProgress()
                    target?.let { file ->
                        db.trackDao().getByDropboxPath(path)?.let {
                            db.trackDao().insert(
                                it.track.copy(sourcePath = Uri.fromFile(file).toString())
                            )
                        }
                    }
                }.collectLatest { (file, processed) ->
                    Timber.d("qgeck file path: ${file.path}, processed: $processed")
                    if (processed == null) {
                        return@collectLatest
                    }
                    target = file
                    processedFilesSize = processedFilesSizeSnapshot + processed

                    val now = System.currentTimeMillis()
                    if (now - lastProgressSampledTime < PROGRESS_UPDATE_THRESHOLD_MILLIS) {
                        return@collectLatest
                    }
                    speeds =
                        (speeds + ((processedFilesSize - lastProcessedFileSize).toFloat() / (now - lastProgressSampledTime)))
                            .takeLast(10)
                    lastProgressSampledTime = now
                    lastProcessedFileSize = processedFilesSize
                    updateProgress()
                }
            }
        } catch (e: RateLimitException) {
            delay(e.backoffMillis.milliseconds)
            download(db, targetPaths, startTime, client)
        } catch (_: ServerException) {
            delay(3000.milliseconds)
            download(db, targetPaths, startTime, client)
        }
    }

    private fun getNotification(
        bitmap: Bitmap
    ): Notification =
        applicationContext.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
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
            .setContentText(
                currentPath?.let {
                    applicationContext.getString(
                        R.string.notification_text_retriever_with_path,
                        remainingFilesCount,
                        "${processedFilesSize.toFloat().getReadableStringWithUnit()}B",
                        "-",
                        it
                    )
                } ?: applicationContext.getString(
                    R.string.notification_text_retriever,
                    remainingFilesCount
                )
            )
            .build()

    private suspend fun updateProgress() {
        setProgress(
            createProgressData(
                title = applicationContext.getString(R.string.progress_title_download_dropbox),
                progressFraction = progressFraction,
                remainingFiles = remainingFilesCount,
                processedFileSize = processedFilesSize,
                remainingDuration = remainingDuration,
                path = currentPath
            )
        )
        setForeground(getForegroundInfo())
    }
}