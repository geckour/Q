package com.geckour.q.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.geckour.q.data.db.DB
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
        const val UNKNOWN: String = "UNKNOWN"
    }

    private var forceStop = false

    override fun doWork(): Result {
        return if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Result.FAILURE
        } else {
            Timber.d("qgeck media retrieve worker started")
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
                            pushMedia(current to total, trackId, albumId, artistId, trackPath)
                        }
                        Timber.d("qgeck media retrieve worker completed, successfully: ${forceStop.not()}")
                        if (forceStop) Result.FAILURE else Result.SUCCESS
                    } ?: Result.FAILURE
        }
    }

    override fun onStopped(cancelled: Boolean) {
        super.onStopped(cancelled)

        if (cancelled) forceStop = true
    }

    private fun pushMedia(progress: Pair<Int, Int>, trackId: Long, albumId: Long, artistId: Long, trackPath: String) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

        if (File(trackPath).exists().not()) {
            applicationContext.contentResolver.delete(uri, null, null)
            return
        }

        MediaMetadataRetriever().also { retriever ->
            try {
                retriever.setDataSource(applicationContext, uri)

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: UNKNOWN
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
                        ?: UNKNOWN
                val artistTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: UNKNOWN
                val albumArtist = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val artworkUriString =
                        getArtworkUriFromAlbumId(albumId).toString()

                val artist = Artist(artistId, artistTitle)
                DB.getInstance(applicationContext).artistDao().upsert(artist)

                val albumArtistId = albumArtist?.let {
                    DB.getInstance(applicationContext).artistDao()
                            .findArtist(albumArtist).firstOrNull()?.id
                }
                val album = Album(albumId, albumTitle, albumArtistId ?: artistId, artworkUriString)
                DB.getInstance(applicationContext).albumDao().upsert(album)

                val track = Track(trackId, title, albumId, artistId, albumArtistId, duration,
                        trackNum, trackTotal, discNum, discTotal, trackPath)
                DB.getInstance(applicationContext).trackDao().upsert(track)
                applicationContext.sendBroadcast(MainActivity.createProgressIntent(progress))
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }
}