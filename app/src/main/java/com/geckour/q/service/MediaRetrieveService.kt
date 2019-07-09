package com.geckour.q.service

import android.Manifest
import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.geckour.q.data.db.DB
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.util.pushMedia
import timber.log.Timber

class MediaRetrieveService : IntentService(NAME) {

    companion object {
        private const val NAME = "MediaRetrieveService"

        private const val ACTION_CANCEL = "com.geckour.q.service.retrieve.cancel"

        internal val projection = arrayOf(MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATA)

        fun getIntent(context: Context): Intent = Intent(context, MediaRetrieveService::class.java)

        fun cancel(context: Context) {
            context.sendBroadcast(Intent(ACTION_CANCEL))
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.apply {
                when (action) {
                    ACTION_CANCEL -> {
                        expired = true
                    }
                }
            }
        }
    }

    private var expired = false

    override fun onCreate() {
        super.onCreate()

        registerReceiver(receiver, IntentFilter(ACTION_CANCEL))
    }

    override fun onHandleIntent(intent: Intent?) {
        if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Timber.d("qgeck media retrieve service started")
            val db = DB.getInstance(applicationContext)
            db.clearAllTables()
            applicationContext.contentResolver
                    .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                            null,
                            "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                        val retriever = MediaMetadataRetriever()
                        while (expired.not() && cursor.moveToNext()) {
                            retriever.pushMedia(applicationContext, db, cursor)
                        }

                        Timber.d("qgeck track in db count: ${db.trackDao().count()}")
                        Timber.d("qgeck media retrieve worker with state: ${expired.not()}")
                        sendBroadcast(MainActivity.createSyncCompleteIntent(true))
                    }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }
}