package com.geckour.q.util

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.work.Worker
import com.geckour.q.data.DB
import com.geckour.q.data.model.Album
import com.geckour.q.data.model.Artist
import com.geckour.q.data.model.Track
import java.io.File

class MediaRetrieveWorker : Worker() {

    companion object {
        private const val UNKNOWN = "UNKNOWN"
    }

    override fun doWork(): Result {
        return if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Result.FAILURE
        } else {
            applicationContext.contentResolver
                    .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Audio.Media._ID,
                                    MediaStore.Audio.Media.ALBUM_ID,
                                    MediaStore.Audio.Media.ARTIST_ID,
                                    MediaStore.Audio.Media.DATA),
                            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                            null,
                            "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                        while (cursor.moveToFirst()) {
                            val trackId = cursor.getLong(
                                    cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                            val albumId = cursor.getLong(
                                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                            val artistId = cursor.getLong(
                                    cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID))
                            val trackPath = cursor.getString(
                                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                            pushMedia(trackId, albumId, artistId, trackPath)
                        }

                        Result.SUCCESS
                    } ?: Result.FAILURE
        }
    }

    private fun pushMedia(trackId: Long, albumId: Long, artistId: Long, trackPath: String) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

        if (File(trackPath).exists().not()) {
            applicationContext.contentResolver.delete(uri, null, null)
            return
        }

        MediaMetadataRetriever().also { retriever ->
            retriever.setDataSource(applicationContext, uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: UNKNOWN
            val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION).let { it.toLong() }
            val albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: UNKNOWN
            val artistTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: UNKNOWN
            val albumArtist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: UNKNOWN
            val artworkUriString = getArtworkUriFromAlbumId(applicationContext, albumId).toString()

            val artist = Artist(artistId, artistTitle)
            DB.getInstance(applicationContext).artistDao().upsert(artist)

            val albumArtistId =
                    DB.getInstance(applicationContext).artistDao().findArtist(albumArtist)?.id
            val album = Album(albumId, albumTitle, artworkUriString, albumArtistId)
            DB.getInstance(applicationContext).albumDao().upsert(album)

            val track = Track(trackId, title, albumId, artistId, duration, trackPath)
            DB.getInstance(applicationContext).trackDao().upsert(track)
        }
    }
}