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
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveAudioFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import timber.log.Timber


internal const val DROPBOX_DOWNLOAD_WORKER_NAME = "DropboxDownloadWorker"

class DropboxDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {

    companion object {

        const val TAG = "dropbox_download_worker"

        const val KEY_TARGET_PATHS = "key_target_paths"
    }

    private var targetPaths = emptyList<String>()
    private var seed = 0L
    private var processedFilesCount = 0
    private var currentPath: String? = null
    private val notificationBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)

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

            val startTime = System.currentTimeMillis()
            seed = startTime

            Timber.d("qgeck Dropbox download worker started")
            val db = DB.getInstance(applicationContext)

            setProgress(
                createProgressData(
                    title = applicationContext.getString(R.string.progress_title_download_dropbox),
                    numerator = 0
                )
            )

            targetPaths = requireNotNull(inputData.getStringArray(KEY_TARGET_PATHS)).toList()

            download(db, targetPaths, startTime, dbxClient)

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            delay(200)
            return Result.success(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
        }

        return Result.failure(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID_RETRIEVE,
            getNotification(
                currentPath,
                processedFilesCount,
                targetPaths.size,
                seed,
                notificationBitmap
            ),
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
            val files = targetPaths.mapNotNull { targetPath ->
                client.files().getMetadata(targetPath) as? FileMetadata
            }
            files.forEachIndexed { index, fileMetadata ->
                setProgress(
                    createProgressData(
                        title = applicationContext.getString(R.string.progress_title_download_dropbox),
                        numerator = index + 1,
                        denominator = targetPaths.size,
                        totalFiles = targetPaths.size,
                        path = fileMetadata.pathDisplay,
                        remaining = if (index < 1) -1
                        else run {
                            val elapsedTime = System.currentTimeMillis() - startTime
                            val processedSize = files.take(index).sumOf { it.size }
                            val remainingSize = files.sumOf { it.size } - processedSize
                            (remainingSize * elapsedTime.toDouble() / processedSize).toLong()
                        },
                        remainingFileSize = files.takeLast(files.size - index)
                            .sumOf { it.size }
                    )
                )
                val savedFile = client.saveAudioFile(
                    applicationContext,
                    fileMetadata.id,
                    fileMetadata.pathLower
                )
                db.trackDao().getByDropboxPath(fileMetadata.pathLower)?.let {
                    db.trackDao().insert(
                        it.track.copy(sourcePath = Uri.fromFile(savedFile).toString())
                    )
                }
            }
        } catch (e: RateLimitException) {
            delay(e.backoffMillis)
            download(db, targetPaths, startTime, client)
        } catch (e: ServerException) {
            delay(3000)
            download(db, targetPaths, startTime, client)
        }
    }

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
}