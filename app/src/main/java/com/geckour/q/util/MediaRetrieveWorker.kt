package com.geckour.q.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.dao.upsert
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Track
import com.geckour.q.ui.MainActivity
import timber.log.Timber
import java.io.File
import java.util.*

class MediaRetrieveWorker(context: Context, parameters: WorkerParameters? = null) :
        Worker(context.applicationContext,
                parameters ?: WorkerParameters(UUID.randomUUID(),
                        Data.EMPTY, emptyList<String>(),
                        WorkerParameters.RuntimeExtras(),
                        0,
                        {},
                        { appContext, workerClassName, workerParameters ->
                            if (workerClassName == MediaRetrieveWorker::class.java.name)
                                MediaRetrieveWorker(appContext, workerParameters)
                            else null
                        })) {

    companion object {
        const val WORK_NAME = "media_retrieve_work"
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
                    .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Audio.Media._ID,
                                    MediaStore.Audio.Media.ALBUM_ID,
                                    MediaStore.Audio.Media.ARTIST_ID,
                                    MediaStore.Audio.Media.DATA),
                            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                            null,
                            "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                        val total = cursor.count

                        while (forceStop.not() && cursor.moveToNext()) {
                            val current = cursor.position
                            val trackId = cursor.getLong(
                                    cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                            val albumId = cursor.getLong(
                                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                            val artistId = cursor.getLong(
                                    cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID))
                            val trackPath = cursor.getString(
                                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                            pushMedia(db, current to total, trackId, albumId, artistId, trackPath)
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

    private fun pushMedia(db: DB, progress: Pair<Int, Int>, trackMediaId: Long, albumMediaId: Long, artistMediaId: Long, trackPath: String) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackMediaId)

        if (File(trackPath).exists().not()) {
            applicationContext.contentResolver.delete(uri, null, null)
            return
        }

        MediaMetadataRetriever().also { retriever ->
            try {
                retriever.setDataSource(applicationContext, uri)

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val duration = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                val trackSplit = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.split("/")
                val trackNum = trackSplit?.first()?.toInt()
                val trackTotal = trackSplit?.last()?.toInt()
                val discSplit = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.split("/")
                val discNum = discSplit?.first()?.toInt()
                val discTotal = discSplit?.last()?.toInt()
                val albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val artistTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val albumArtistTitle = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val artworkUriString = albumMediaId.getArtworkUriIfExist()?.toString()

                val artistId = Artist(0, artistMediaId, artistTitle).upsert(db) ?: return@also
                val albumArtistId =
                        if (albumArtistTitle == null) null
                        else Artist(0, null, albumArtistTitle).upsert(db)

                val albumId = db.albumDao().getByMediaId(albumMediaId).let {
                    if (it == null || it.hasAlbumArtist.not()) {
                        val album = Album(0, albumMediaId, albumTitle,
                                albumArtistId ?: artistId,
                                artworkUriString, albumArtistId != null)
                        album.upsert(db)
                    } else it.id
                }

                val track = Track(0, trackMediaId, title, albumId, artistId, albumArtistId, duration,
                        trackNum, trackTotal, discNum, discTotal, trackPath)
                track.upsert(db)
                applicationContext.sendBroadcast(MainActivity.createProgressIntent(progress))
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun Long.getArtworkUriIfExist(): Uri? =
            this.getArtworkUriFromMediaId().let { uri ->
                applicationContext.contentResolver.query(uri,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null, null, null)?.use {
                    if (it.moveToFirst()
                            && File(it.getString(it.getColumnIndex(MediaStore.MediaColumns.DATA))).exists())
                        uri
                    else null
                }
            }
}