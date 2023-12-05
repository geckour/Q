package com.geckour.q.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import androidx.work.Data
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal const val NOTIFICATION_ID_RETRIEVE = 300

internal const val MEDIA_RETRIEVE_WORKER_NAME = "MediaRetrieveWorker"

internal const val KEY_PROGRESS_TITLE = "key_progress_title"
internal const val KEY_PROGRESS_PROGRESS_NUMERATOR = "key_progress_progress_numerator"
internal const val KEY_PROGRESS_PROGRESS_DENOMINATOR = "key_progress_progress_denominator"
internal const val KEY_PROGRESS_PROGRESS_TOTAL_FILES = "key_progress_progress_total_files"
internal const val KEY_PROGRESS_PROGRESS_PATH = "key_progress_progress_path"
internal const val KEY_PROGRESS_REMAINING = "key_progress_remaining"
internal const val KEY_PROGRESS_REMAINING_FILES_SIZE = "key_progress_remaining_files_size"
internal const val KEY_PROGRESS_FINISHED = "key_progress_finished"

internal fun createProgressData(
    title: String,
    numerator: Int,
    denominator: Int = -1,
    totalFiles: Int = -1,
    path: String? = null,
    remaining: Long = -1,
    remainingFileSize: Long = -1,
): Data =
    Data.Builder()
        .putString(KEY_PROGRESS_TITLE, title)
        .putInt(KEY_PROGRESS_PROGRESS_NUMERATOR, numerator)
        .putInt(KEY_PROGRESS_PROGRESS_DENOMINATOR, denominator)
        .putInt(KEY_PROGRESS_PROGRESS_TOTAL_FILES, totalFiles)
        .putString(KEY_PROGRESS_PROGRESS_PATH, path)
        .putLong(KEY_PROGRESS_REMAINING, remaining)
        .putLong(KEY_PROGRESS_REMAINING_FILES_SIZE, remainingFileSize)
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

    val audioFile = AudioFileIO.read(this@storeMediaInfo)
    val tag = audioFile.tag ?: throw IllegalArgumentException("No media metadata found.")
    val header = audioFile.audioHeader

    val duration = header.trackLength.toLong() * 1000
    val codec = header.encodingType
    val bitrate = audioFile.file.length() * 8 / (header.trackLength * 1000L)
    val sampleRate = header.sampleRateAsNumber

    val title = tag.getAll(FieldKey.TITLE).lastOrNull { it.isNotBlank() }
        ?: this@storeMediaInfo.name
    val titleSort = (tag.getAll(FieldKey.TITLE_SORT).lastOrNull { it.isNotBlank() } ?: title)
        ?.hiraganized
        ?: this@storeMediaInfo.name

    val albumTitle = tag.getAll(FieldKey.ALBUM).lastOrNull { it.isNotBlank() }
    val cachedAlbum = albumTitle?.let { db.albumDao().findAllByTitle(it).firstOrNull() }
    val albumTitleSort =
        (tag.getAll(FieldKey.ALBUM_SORT).lastOrNull { it.isNotBlank() } ?: albumTitle)
            ?.hiraganized
            ?: cachedAlbum?.album?.titleSort

    val artistTitle = tag.getAll(FieldKey.ARTIST).firstOrNull { it.isNotBlank() }
    val cachedArtist = artistTitle?.let { db.artistDao().getAllByTitle(it).firstOrNull() }
    val artistTitleSort =
        (tag.getAll(FieldKey.ARTIST_SORT).firstOrNull { it.isNotBlank() } ?: artistTitle)
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
        ?: cachedAlbum?.album?.artworkUriString

    val artist = Artist(
        id = 0,
        title = artistTitle ?: UNKNOWN,
        titleSort = artistTitleSort ?: UNKNOWN,
        playbackCount = 0,
        totalDuration = 0,
        artworkUriString = artworkUriString ?: cachedArtist?.artworkUriString
    )
    val artistId = db.artistDao().upsert(db, artist, duration)
    val albumArtistId =
        if (albumArtistTitle != null && albumArtistTitleSort != null) {
            val albumArtist = Artist(
                id = 0,
                title = albumArtistTitle,
                titleSort = albumArtistTitleSort,
                playbackCount = 0,
                totalDuration = 0,
                artworkUriString = artworkUriString ?: cachedAlbumArtist?.artworkUriString
            )
            db.artistDao().upsert(db, albumArtist, duration)
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
    val albumId = db.albumDao().upsert(db, album, duration)

    val track = Track(
        id = trackId ?: 0,
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
        playbackCount = 0
    )

    return@withContext db.trackDao().upsert(track, albumId, artistId, duration)
}

internal fun Bitmap.drawProgressIcon(
    progressNumerator: Int,
    progressDenominator: Int,
    seed: Long
): Bitmap {
    val maxTileNumber = 24
    val progressRatio = progressNumerator.toFloat() / progressDenominator
    val tileNumber = (maxTileNumber * progressRatio).toInt()
    val canvas = Canvas(this)
    val paint = Paint().apply {
        isAntiAlias = true
    }
    val offset = PointF(canvas.width * 0.5f, canvas.height * 0.5f)
    val innerR = canvas.width * 0.35f
    val outerR = canvas.width * 0.45f
    val random = Random(seed)
    canvas.drawColor(0, PorterDuff.Mode.CLEAR)
    repeat(tileNumber + 1) {
        val start = 3
        val angleLeft = ((start + it) * PI / 12).toFloat()
        val angleRight = ((start + it + 1) * PI / 12).toFloat()
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(offset.x + outerR * cos(angleLeft), offset.y + outerR * sin(angleLeft))
            lineTo(offset.x + outerR * cos(angleRight), offset.y + outerR * sin(angleRight))
            lineTo(offset.x + innerR * cos(angleRight), offset.y + innerR * sin(angleRight))
            lineTo(offset.x + innerR * cos(angleLeft), offset.y + innerR * sin(angleLeft))
            close()
        }
        val alphaC =
            if (it == tileNumber) maxTileNumber * progressRatio % 1f
            else 1f
        paint.color = Color.argb(
            (150 * alphaC).toInt(),
            random.nextInt(255),
            random.nextInt(255),
            random.nextInt(255)
        )
        canvas.drawPath(path, paint)
    }

    return this
}