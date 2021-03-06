package com.geckour.q.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.work.*
import com.geckour.q.data.db.DB
import com.geckour.q.util.MediaRetrieveWorker.Companion.projection
import timber.log.Timber

class MediaObserveWorker : Worker {

    constructor() : super()
    constructor(context: Context, parameters: WorkerParameters)
            : super(context.applicationContext, parameters)

    override fun doWork(): Result =
            if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || applicationContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Result.FAILURE
            } else {
                Timber.d("qgeck uris: ${triggeredContentUris?.toList()}")
                triggeredContentUris?.mapNotNull {
                    it?.let {
                        try {
                            ContentUris.parseId(it)
                        } catch (t: Throwable) {
                            null
                        }
                    }
                }?.firstOrNull()?.also { mediaId ->
                    Timber.d("qgeck media id: $mediaId")
                    try {
                        applicationContext.contentResolver
                                .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                                        "${MediaStore.Audio.Media._ID}=$mediaId",
                                        null, null)?.use {
                                    if (it.moveToFirst()) {
                                        Timber.d("qgeck push start")
                                        pushMedia(applicationContext,
                                                DB.getInstance(applicationContext), it)
                                    } else deleteFromDB(mediaId)
                                }
                    } catch (t: Throwable) {
                        Timber.e(t)
                    }
                }
                Result.RETRY
            }

    private fun deleteFromDB(mediaId: Long) {
        Timber.d("qgeck delete start")
        DB.getInstance(applicationContext).apply {
            trackDao().getByMediaId(mediaId)?.apply {
                trackDao().delete(this.id)
                if (trackDao().findByAlbum(this.albumId).isEmpty())
                    albumDao().delete(this.albumId)
                if (trackDao().findByArtist(this.artistId).isEmpty())
                    artistDao().delete(this.artistId)
            }
        }
    }
}