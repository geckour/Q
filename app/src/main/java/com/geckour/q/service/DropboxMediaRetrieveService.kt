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
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.RateLimitException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.App
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.presentation.LauncherActivity
import com.geckour.q.presentation.main.MainActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.dropboxToken
import com.geckour.q.util.getExtension
import com.geckour.q.util.getNotificationBuilder
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

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

    private val dbxRequestConfig =
        DbxRequestConfig.newBuilder("qp/${BuildConfig.VERSION_NAME}").build()

    private lateinit var token: String

    private var filesCount = 0

    override fun onCreate() {
        super.onCreate()

        registerReceiver(receiver, IntentFilter(ACTION_CANCEL))

        token = PreferenceManager.getDefaultSharedPreferences(this).dropboxToken
    }

    override fun onHandleIntent(intent: Intent?) {
        intent ?: return

        if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
            val seed = System.currentTimeMillis()
            startForeground(NOTIFICATION_ID_RETRIEVE, getNotification(0))
            Timber.d("qgeck Dropbox media retrieve service started")
            val db = DB.getInstance(applicationContext)
            if (intent.getBooleanExtra(KEY_CLEAR, false)) {
                db.clearAllTables()
            }

            val rootPath = requireNotNull(intent.getStringExtra(KEY_ROOT_PATH))
            Timber.d("qgeck rootPath: $rootPath")

            sendBroadcast(MainActivity.createProgressIntent(0 to -1))
            startForeground(
                NOTIFICATION_ID_RETRIEVE,
                getNotification(0)
            )

            retrieveAudioFilePaths(
                this,
                db,
                rootPath,
                DbxClientV2(dbxRequestConfig, token),
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
                when (it) {
                    is FolderMetadata -> {
                        retrieveAudioFilePaths(
                            context,
                            db,
                            it.pathLower,
                            client,
                            seed,
                            bitmap
                        )
                    }
                    is FileMetadata -> {
                        if (it.name.isAudioFilePath) {
                            runCatching {
                                storeMediaInfo(context, client, it)
                            }.onSuccess {
                                filesCount++
                            }.onFailure { t ->
                                if (t is RateLimitException) {
                                    Thread.sleep(t.backoffMillis)
                                    storeMediaInfo(context, client, it)
                                } else {
                                    Timber.e(t)
                                    return@forEach
                                }
                            }

                            sendBroadcast(MainActivity.createProgressIntent(filesCount to -1))
                            startForeground(
                                NOTIFICATION_ID_RETRIEVE,
                                getNotification(filesCount)
                            )
                        }
                    }
                }
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

    private fun getNotification(progress: Int): Notification =
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
                getString(
                    R.string.notification_text_retriever_dropbox,
                    progress
                )
            )
            .build()

    private fun storeMediaInfo(
        context: Context,
        client: DbxClientV2,
        dropboxMetadata: FileMetadata
    ): Long =
        runBlocking {
            val existingUrl = client.sharing()
                .listSharedLinksBuilder()
                .withPath(dropboxMetadata.pathLower)
                .start()
                .links
                .maxByOrNull { it.expires?.time ?: 0 }
                ?.url
            val url = (existingUrl ?: client.sharing()
                .createSharedLinkWithSettings(dropboxMetadata.pathLower)
                .url).replace("://www", "://dl")

            val dirName = "audio"
            val fileName = "temp_audio.${dropboxMetadata.pathLower.getExtension()}"
            val dir = File(context.cacheDir, dirName)
            val file = File(dir, fileName)

            if (file.exists()) file.delete()
            if (dir.exists().not()) dir.mkdir()

            FileOutputStream(file).use {
                client.files().download(dropboxMetadata.pathLower).download(it)
                file.storeMediaInfo(context, url, null, dropboxMetadata.serverModified.time)
            }
        }
}