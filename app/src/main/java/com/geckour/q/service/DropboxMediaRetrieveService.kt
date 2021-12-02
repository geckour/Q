package com.geckour.q.service

import android.Manifest
import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.webkit.MimeTypeMap
import androidx.preference.PreferenceManager
import com.dropbox.core.RateLimitException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
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
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import timber.log.Timber

class DropboxMediaRetrieveService : IntentService(NAME) {

    companion object {
        private const val NAME = "DropboxMediaRetrieveService"

        private const val KEY_ROOT_PATH = "key_root_path"

        fun getIntent(
            context: Context,
            rootPath: String,
            clear: Boolean
        ): Intent = Intent(context, DropboxMediaRetrieveService::class.java).apply {
            putExtra(KEY_ROOT_PATH, rootPath)
            putExtra(KEY_CLEAR, clear)
        }

        fun cancel(context: Context) {
            context.sendBroadcast(Intent(ACTION_CANCEL))
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {
                ACTION_CANCEL -> {
                    expired = true
                }
            }
        }
    }

    private var expired = false

    private var filesCount = 0

    override fun onCreate() {
        super.onCreate()

        registerReceiver(receiver, IntentFilter(ACTION_CANCEL))
    }

    override fun onHandleIntent(intent: Intent?) {
        intent ?: return

        if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val dbxClient = obtainDbxClient(get()) ?: return

            val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
            val seed = System.currentTimeMillis()
            Timber.d("qgeck Dropbox media retrieve service started")
            val db = DB.getInstance(applicationContext)
            if (intent.getBooleanExtra(KEY_CLEAR, false)) {
                db.clearAllTables()
            }

            val rootPath = requireNotNull(intent.getStringExtra(KEY_ROOT_PATH))
            Timber.d("qgeck rootPath: $rootPath")

            sendBroadcast(MainActivity.createProgressIntent(0))
            startForeground(NOTIFICATION_ID_RETRIEVE, getNotification(null, 0))

            retrieveAudioFilePaths(
                this,
                db,
                rootPath,
                dbxClient,
                seed,
                bitmap
            )

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            Timber.d("qgeck media retrieve worker with state: ${expired.not()}")
            Thread.sleep(200)
            sendBroadcast(MainActivity.createSyncCompleteIntent(true))
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }

    private fun retrieveAudioFilePaths(
        context: Context,
        db: DB,
        root: String,
        client: DbxClientV2,
        seed: Long,
        bitmap: Bitmap
    ) {
        try {
            var fileAndFolders = client.files().listFolder(root)
            while (true) {
                if (fileAndFolders.hasMore.not()) break

                fileAndFolders = client.files().listFolderContinue(fileAndFolders.cursor)
            }

            fileAndFolders.entries.asSequence().forEach {
                it.storeMediaInfo(context, db, client, seed, bitmap)
            }
        } catch (e: RateLimitException) {
            Thread.sleep(e.backoffMillis)
            retrieveAudioFilePaths(context, db, root, client, seed, bitmap)
        }
    }

    private val String.isAudioFilePath: Boolean
        get() = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(this.getExtension())
            ?.contains("audio") == true

    private fun getNotification(path: String?, progress: Int): Notification =
        getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    App.REQUEST_CODE_LAUNCH_APP,
                    LauncherActivity.createIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setContentTitle(getString(R.string.notification_title_retriever))
            .setContentText(
                path?.let {
                    getString(
                        R.string.notification_text_retriever_dropbox_with_path,
                        progress,
                        it
                    )
                } ?: getString(R.string.notification_text_retriever_dropbox, progress)
            )
            .build()

    private fun Metadata.storeMediaInfo(
        context: Context,
        db: DB,
        client: DbxClientV2,
        seed: Long,
        bitmap: Bitmap
    ) {
        if (expired.not()) {
            when (this) {
                is FolderMetadata -> {
                    retrieveAudioFilePaths(
                        context,
                        db,
                        pathLower,
                        client,
                        seed,
                        bitmap
                    )
                }
                is FileMetadata -> {
                    if (name.isAudioFilePath) {
                        filesCount++
                        sendBroadcast(
                            MainActivity.createProgressIntent(
                                filesCount,
                                progressPath = this.pathDisplay
                            )
                        )
                        startForeground(
                            NOTIFICATION_ID_RETRIEVE,
                            getNotification(this.pathDisplay, filesCount)
                        )

                        runCatching {
                            storeMediaInfo(context, db, client, this@storeMediaInfo)
                        }.onFailure { t ->
                            if (t is RateLimitException) {
                                Thread.sleep(t.backoffMillis)
                                this.storeMediaInfo(context, db, client, seed, bitmap)
                            } else {
                                Timber.e(t)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun storeMediaInfo(
        context: Context,
        db: DB,
        client: DbxClientV2,
        dropboxMetadata: FileMetadata
    ): Long =
        runBlocking {
            val currentTime = System.currentTimeMillis()
            val url = client.files().getTemporaryLink(dropboxMetadata.pathLower).link

            db.trackDao().getBySourcePath(url)?.track?.let {
                if (it.lastModified >= dropboxMetadata.serverModified.time) it.id
                else null
            } ?: client.saveTempAudioFile(context, dropboxMetadata.pathLower)
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