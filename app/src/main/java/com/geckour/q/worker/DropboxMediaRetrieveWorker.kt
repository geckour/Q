package com.geckour.q.worker

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
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
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.util.DROPBOX_EXPIRES_IN
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getExtension
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.saveTempAudioFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import timber.log.Timber

class DropboxMediaRetrieveWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {

    companion object {

        const val KEY_ROOT_PATH = "key_root_path"
    }

    private var processedFilesCount = 0
    private val files = mutableListOf<FileMetadata>()
    private var currentPath: String? = null
    private val notificationBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
    private var seed: Long = -1

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33
            || applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            val dbxClient = obtainDbxClient(get()) ?: return Result.failure()

            seed = System.currentTimeMillis()
            Timber.d("qgeck Dropbox media retrieve service started")
            val db = DB.getInstance(applicationContext)

            val rootPath = requireNotNull(inputData.getString(KEY_ROOT_PATH))
            Timber.d("qgeck rootPath: $rootPath")

            applicationContext.sendBroadcast(MainActivity.createProgressIntent(0))

            files.clear()
            retrieveAudioFilePaths(rootPath, dbxClient)
            files.forEach {
                if (isStopped) return Result.success()

                it.storeMediaInfo(applicationContext, db, dbxClient)
            }

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            delay(200)
            applicationContext.sendBroadcast(MainActivity.createSyncCompleteIntent(true))
            return Result.success()
        }

        return Result.failure()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID_RETRIEVE,
            getNotification(currentPath, processedFilesCount, files.size, seed, notificationBitmap)
        )

    private suspend fun retrieveAudioFilePaths(root: String, client: DbxClientV2) {
        if (isStopped) return
        try {
            var fileAndFolders = client.files().listFolder(root)
            while (true) {
                if (fileAndFolders.hasMore.not()) break

                fileAndFolders = client.files().listFolderContinue(fileAndFolders.cursor)
            }

            fileAndFolders.entries.mapNotNull {
                when (it) {
                    is FolderMetadata -> {
                        retrieveAudioFilePaths(it.pathLower, client)
                        null
                    }
                    is FileMetadata -> it
                    else -> null
                }
            }.apply {
                files.addAll(this)
                applicationContext.sendBroadcast(
                    MainActivity.createProgressIntent(
                        processedFilesCount,
                        files.size
                    )
                )
            }
        } catch (e: RateLimitException) {
            delay(e.backoffMillis)
            retrieveAudioFilePaths(root, client)
        } catch (e: ServerException) {
            delay(3000)
            retrieveAudioFilePaths(root, client)
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
        context: Context,
        db: DB,
        client: DbxClientV2
    ) {
        if (name.isAudioFilePath) {
            processedFilesCount++
            applicationContext.sendBroadcast(
                MainActivity.createProgressIntent(
                    processedFilesCount,
                    files.size,
                    progressPath = this.pathDisplay
                )
            )
            currentPath = this.pathDisplay

            runCatching {
                storeMediaInfo(context, db, client, this@storeMediaInfo)
            }.onFailure { t ->
                if (t is RateLimitException) {
                    Thread.sleep(t.backoffMillis)
                    this.storeMediaInfo(context, db, client)
                } else {
                    Timber.e(t)
                    return
                }
            }
        }
    }

    private suspend fun storeMediaInfo(
        context: Context,
        db: DB,
        client: DbxClientV2,
        dropboxMetadata: FileMetadata
    ): Long {
        db.trackDao().getByDropboxPath(dropboxMetadata.pathLower)?.track?.let {
            if (it.lastModified >= dropboxMetadata.serverModified.time) return it.id
        }
        val url = client.files().getTemporaryLink(dropboxMetadata.pathLower).link
        val currentTime = System.currentTimeMillis()

        return client.saveTempAudioFile(context, dropboxMetadata.pathLower)
            .storeMediaInfo(
                context,
                url,
                null,
                dropboxMetadata.pathLower,
                currentTime + DROPBOX_EXPIRES_IN,
                dropboxMetadata.serverModified.time
            )
    }
}