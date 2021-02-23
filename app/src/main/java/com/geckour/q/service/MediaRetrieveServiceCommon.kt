package com.geckour.q.service

import android.content.Context
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Track
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.hiraganized
import com.geckour.q.util.storeArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

internal const val NOTIFICATION_ID_RETRIEVE = 300

internal const val ACTION_CANCEL = "com.geckour.q.service.retrieve.cancel"
internal const val KEY_CLEAR = "key_clear"

internal suspend fun File.storeMediaInfo(
    context: Context,
    trackPath: String,
    trackMediaId: Long?,
    dropboxPath: String?,
    dropboxExpiredAt: Long?,
    lastModified: Long
): Long = withContext(Dispatchers.IO) {
    val db = DB.getInstance(context)

    val audioFile = AudioFileIO.read(this@storeMediaInfo)
    val tag = audioFile.tag ?: throw IllegalArgumentException("No media metadata found.")
    val header = audioFile.audioHeader

    val codec = header.encodingType
    val bitrate = header.bitRateAsNumber
    val sampleRate = header.sampleRateAsNumber

    val title = tag.getAll(FieldKey.TITLE).lastOrNull { it.isNotBlank() }
    val titleSort =
        (tag.getAll(FieldKey.TITLE_SORT).lastOrNull { it.isNotBlank() }
            ?: title)?.hiraganized

    val albumTitle = tag.getAll(FieldKey.ALBUM).lastOrNull { it.isNotBlank() }
    val cachedAlbum = albumTitle?.let { db.albumDao().getAllByTitle(it).firstOrNull() }
    val albumTitleSort =
        (tag.getAll(FieldKey.ALBUM_SORT).lastOrNull { it.isNotBlank() } ?: albumTitle)
            ?.hiraganized
            ?: cachedAlbum?.album?.titleSort

    val artistTitle = tag.getAll(FieldKey.ARTIST).lastOrNull { it.isNotBlank() }
    val cachedArtist = artistTitle?.let { db.artistDao().getAllByTitle(it).firstOrNull() }
    val artistTitleSort =
        (tag.getAll(FieldKey.ARTIST_SORT).lastOrNull { it.isNotBlank() } ?: artistTitle)
            ?.hiraganized
            ?: cachedArtist?.titleSort

    val albumArtistTitle = tag.getAll(FieldKey.ALBUM_ARTIST).firstOrNull { it.isNotBlank() }
    val cachedAlbumArtist =
        albumArtistTitle?.let { db.artistDao().getAllByTitle(it).firstOrNull() }
    val albumArtistTitleSort =
        (tag.getAll(FieldKey.ALBUM_ARTIST_SORT).firstOrNull { it.isNotBlank() }
            ?: albumArtistTitle)
            ?.hiraganized
            ?: cachedAlbumArtist?.titleSort

    val duration = header.trackLength.toLong() * 1000
    val pastTrackDuration =
        trackMediaId?.let { db.trackDao().getDurationWithMediaId(trackMediaId) ?: 0 }
            ?: db.trackDao().getDurationWithTitles(
                title ?: UNKNOWN,
                albumTitle ?: UNKNOWN,
                artistTitle ?: UNKNOWN
            ) ?: 0
    val trackNum = catchAsNull {
        tag.getFirst(FieldKey.TRACK).let { if (it.isNullOrBlank()) null else it }?.toInt()
    }
    val discNum = catchAsNull {
        tag.getFirst(FieldKey.DISC_NO).let { if (it.isNullOrBlank()) null else it }?.toInt()
    }

    val composerTitle = tag.getAll(FieldKey.COMPOSER).lastOrNull { it.isNotBlank() }
    val composerTitleSort =
        (tag.getAll(FieldKey.COMPOSER_SORT).lastOrNull { it.isNotBlank() } ?: composerTitle)
            ?.hiraganized

    val artworkUriString = tag.artworkList.lastOrNull()?.binaryData?.storeArtwork(context)
        ?: cachedAlbum?.album?.artworkUriString

    val artist = Artist(
        id = 0,
        title = artistTitle ?: UNKNOWN,
        titleSort = artistTitleSort ?: UNKNOWN,
        playbackCount = 0,
        totalDuration = duration,
        artworkUriString = artworkUriString ?: cachedArtist?.artworkUriString
    )
    val artistId = db.artistDao().upsert(artist, pastTrackDuration)
    val albumArtistId =
        if (albumArtistTitle != null && albumArtistTitleSort != null) {
            val albumArtist = Artist(
                id = 0,
                title = albumArtistTitle,
                titleSort = albumArtistTitleSort,
                playbackCount = 0,
                totalDuration = duration,
                artworkUriString = artworkUriString ?: cachedAlbumArtist?.artworkUriString
            )
            db.artistDao().upsert(albumArtist, pastTrackDuration)
        } else null

    val album = Album(
        id = 0,
        artistId = albumArtistId ?: artistId,
        title = albumTitle ?: UNKNOWN,
        titleSort = albumTitleSort ?: UNKNOWN,
        artworkUriString = artworkUriString,
        hasAlbumArtist = albumArtistId != null,
        playbackCount = 0,
        totalDuration = duration
    )
    val albumId = db.albumDao().upsert(album, pastTrackDuration)

    val track = Track(
        id = 0,
        mediaId = trackMediaId ?: -1,
        codec = codec,
        bitrate = bitrate,
        sampleRate = sampleRate,
        lastModified = lastModified,
        albumId = albumId,
        artistId = artistId,
        albumArtistId = albumArtistId,
        sourcePath = trackPath,
        dropboxPath = dropboxPath,
        dropboxExpiredAt = dropboxExpiredAt,
        title = title ?: UNKNOWN,
        titleSort = titleSort ?: UNKNOWN,
        composer = composerTitle ?: UNKNOWN,
        composerSort = composerTitleSort ?: UNKNOWN,
        duration = duration,
        trackNum = trackNum,
        discNum = discNum,
        artworkUriString = artworkUriString,
        playbackCount = 0
    )

    return@withContext db.trackDao().upsert(track, albumId, artistId, pastTrackDuration)
}