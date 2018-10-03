package com.geckour.q.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.geckour.q.data.db.DB
import timber.log.Timber

class MediaRetrieveWorker : Worker {

    constructor() : super()
    constructor(context: Context, parameters: WorkerParameters)
            : super(context.applicationContext, parameters)

    companion object {
        const val WORK_NAME = "media_retrieve_work"

        val projection = arrayOf(MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATA)
    }

    private var forceStop = false

    override fun doWork(): Result {
        return if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Result.FAILURE
        } else {
            Timber.d("qgeck media retrieve worker started")
            val db = DB.getInstance(applicationContext)
            db.clearAllTables()
            applicationContext.contentResolver
                    .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                            null,
                            "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->

                        while (forceStop.not() && cursor.moveToNext()) {
                            pushMedia(applicationContext, db, cursor)
                        }
                        Timber.d("qgeck track in db count: ${db.trackDao().count()}")
                        Timber.d("qgeck media retrieve worker completed, successfully: ${forceStop.not()}")
                        if (forceStop) Result.FAILURE else Result.SUCCESS
                    } ?: Result.FAILURE
        }
    }

    override fun onStopped(cancelled: Boolean) {
        super.onStopped(cancelled)

        if (cancelled) forceStop = true
    }
}