package com.geckour.q.worker

import android.content.Context
import androidx.work.Data
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Bool
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

internal const val MEDIA_RETRIEVE_WORKER_NAME = "MediaRetrieveWorker"

internal const val KEY_PROGRESS_TITLE = "key_progress_title"
internal const val KEY_PROGRESS_PROGRESS_FRACTION = "key_progress_progress_fraction"
internal const val KEY_PROGRESS_REMAINING_FILES = "key_progress_remaining_files"
internal const val KEY_PROGRESS_SKIPPED_FILES = "key_progress_skipped_files"
internal const val KEY_PROGRESS_TOTAL_FILES_SIZE = "key_progress_total_files_size"
internal const val KEY_PROGRESS_PROCESSED_FILES_SIZE = "key_progress_processed_files_size"
internal const val KEY_PROGRESS_REMAINING_DURATION = "key_progress_processed_remaining_duration"
internal const val KEY_PROGRESS_PROGRESS_PATHS = "key_progress_progress_paths"
internal const val KEY_PROGRESS_FINISHED = "key_progress_finished"

internal fun createProgressData(
    title: String,
    progressFraction: Float = -1f,
    remainingFiles: Int = -1,
    skippedFiles: Int = 0,
    totalFilesSize: Long = 1,
    processedFileSize: Long = 0,
    remainingDuration: Long = -1,
    paths: List<String> = emptyList(),
): Data =
    Data.Builder()
        .putString(KEY_PROGRESS_TITLE, title)
        .putFloat(KEY_PROGRESS_PROGRESS_FRACTION, progressFraction)
        .putInt(KEY_PROGRESS_REMAINING_FILES, remainingFiles)
        .putInt(KEY_PROGRESS_SKIPPED_FILES, skippedFiles)
        .putLong(KEY_PROGRESS_TOTAL_FILES_SIZE, totalFilesSize)
        .putLong(KEY_PROGRESS_PROCESSED_FILES_SIZE, processedFileSize)
        .putLong(KEY_PROGRESS_REMAINING_DURATION, remainingDuration)
        .putStringArray(KEY_PROGRESS_PROGRESS_PATHS, paths.toTypedArray())
        .build()

internal suspend fun File.storeMediaInfo(
    context: Context,
    trackPath: String,
    trackId: Long?,
    trackMediaId: Long?,
    dropboxPath: String?,
    dropboxExpiredAt: Long?,
    lastModified: Long
): Long = withContext(Dispatchers.IO) {
    val db = DB.getInstance(context)

    val existingTrack = trackId?.let { db.trackDao().get(it)?.track }

    val audioFile = AudioFileIO.read(this@storeMediaInfo)
    val tag = audioFile.tag ?: throw IllegalArgumentException("No media metadata found.")
    val header = audioFile.audioHeader

    val duration = header.trackLength.toLong() * 1000
    val durationToAdd = duration - (existingTrack?.duration ?: 0)
    val codec = header.encodingType
    val bitrate = audioFile.file.length() * 8 / (header.trackLength * 1000L)
    val sampleRate = header.sampleRateAsNumber

    val title = tag.getAll(FieldKey.TITLE).lastOrNull { it.isNotBlank() }
        ?: this@storeMediaInfo.name
    val titleSort = (tag.getAll(FieldKey.TITLE_SORT).lastOrNull { it.isNotBlank() }
        ?: title)?.hiraganized

    val albumTitle = tag.getAll(FieldKey.ALBUM).lastOrNull { it.isNotBlank() }
    val existingAlbum = albumTitle?.let { db.albumDao().findAllByTitle(it).firstOrNull() }
    val albumTitleSort =
        (tag.getAll(FieldKey.ALBUM_SORT).lastOrNull { it.isNotBlank() }
            ?: existingAlbum?.album?.titleSort
            ?: albumTitle)?.hiraganized

    val artistTitle = tag.getAll(FieldKey.ARTIST).firstOrNull { it.isNotBlank() }
    val existingArtist = artistTitle?.let { db.artistDao().getAllByTitle(it).firstOrNull() }
    val artistTitleSort =
        (tag.getAll(FieldKey.ARTIST_SORT).firstOrNull { it.isNotBlank() }
            ?: existingArtist?.titleSort
            ?: artistTitle)?.hiraganized

    val albumArtistTitle = tag.getAll(FieldKey.ALBUM_ARTIST).firstOrNull { it.isNotBlank() }
    val existingAlbumArtist =
        albumArtistTitle?.let { db.artistDao().getAllByTitle(it).firstOrNull() }
    val albumArtistTitleSort =
        (tag.getAll(FieldKey.ALBUM_ARTIST_SORT).firstOrNull { it.isNotBlank() }
            ?: existingAlbumArtist?.titleSort
            ?: albumArtistTitle)?.hiraganized

    val trackNum = catchAsNull {
        tag.getFirst(FieldKey.TRACK).let { if (it.isNullOrBlank()) null else it }?.toInt()
    }
    val trackTotal = catchAsNull {
        tag.getFirst(FieldKey.TRACK_TOTAL).let { if (it.isNullOrBlank()) null else it }?.toInt()
    }
    val discNum = catchAsNull {
        tag.getFirst(FieldKey.DISC_NO).let { if (it.isNullOrBlank()) null else it }?.toInt()
    }
    val discTotal = catchAsNull {
        tag.getFirst(FieldKey.DISC_TOTAL).let { if (it.isNullOrBlank()) null else it }?.toInt()
    }
    val releaseDate = catchAsNull { tag.getAll(FieldKey.YEAR).lastOrNull { it.isNotBlank() } }
    val genre = tag.getAll(FieldKey.GENRE).lastOrNull { it.isNotBlank() }

    val composerTitle = tag.getAll(FieldKey.COMPOSER).lastOrNull { it.isNotBlank() }
    val composerTitleSort =
        (tag.getAll(FieldKey.COMPOSER_SORT).lastOrNull { it.isNotBlank() } ?: composerTitle)
            ?.hiraganized

    val artworkUriString = tag.artworkList.lastOrNull()?.binaryData?.storeArtwork(context)
        ?: existingAlbum?.album?.artworkUriString

    val artist = Artist(
        id = 0,
        title = artistTitle ?: UNKNOWN,
        titleSort = artistTitleSort ?: UNKNOWN,
        playbackCount = 0,
        totalDuration = 0,
        artworkUriString = artworkUriString ?: existingArtist?.artworkUriString
    )
    val artistId = db.artistDao().upsert(db, artist, durationToAdd)
    val albumArtistId =
        if (albumArtistTitle != null && albumArtistTitleSort != null) {
            val albumArtist = Artist(
                id = 0,
                title = albumArtistTitle,
                titleSort = albumArtistTitleSort,
                playbackCount = 0,
                totalDuration = 0,
                artworkUriString = artworkUriString ?: existingAlbumArtist?.artworkUriString
            )
            db.artistDao().upsert(db, albumArtist, durationToAdd)
        } else null

    val album = Album(
        id = 0,
        artistId = albumArtistId ?: artistId,
        title = albumTitle ?: UNKNOWN,
        titleSort = albumTitleSort ?: UNKNOWN,
        artworkUriString = artworkUriString,
        hasAlbumArtist = albumArtistId != null,
        playbackCount = 0,
        totalDuration = 0
    )
    val albumId = db.albumDao().upsert(db, album, durationToAdd)

    val track = Track(
        id = trackId ?: 0,
        mediaId = trackMediaId ?: existingTrack?.mediaId ?: -1,
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
        composer = composerTitle,
        composerSort = composerTitleSort,
        duration = duration,
        trackNum = trackNum,
        trackTotal = trackTotal,
        discNum = discNum,
        discTotal = discTotal,
        releaseDate = releaseDate,
        genre = genre,
        artworkUriString = artworkUriString,
        playbackCount = existingTrack?.playbackCount ?: 0,
        ignored = existingTrack?.ignored ?: Bool.FALSE,
        isFavorite = existingTrack?.isFavorite ?: false,
    )

    return@withContext db.trackDao().insert(track)
}
