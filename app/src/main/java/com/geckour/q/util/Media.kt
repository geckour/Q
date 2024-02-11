package com.geckour.q.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.icu.util.Calendar
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import coil.Coil
import coil.request.ImageRequest
import coil.size.Scale
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.BuildConfig
import com.geckour.q.data.db.BoolConverter
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.DialogEditMetadataBinding
import com.geckour.q.domain.model.UiTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random


const val UNKNOWN: String = "UNKNOWN"

const val DROPBOX_EXPIRES_IN = 14400000L

private val random = Random(System.currentTimeMillis())

val dropboxUrlPattern = Regex("^https://.+\\.dl\\.dropboxusercontent\\.com/.+$")
val dropboxCachePathPattern = Regex("^.*/com\\.geckour\\.q.*/cache/audio/id%3A.+$")

enum class InsertActionType {
    NEXT,
    LAST,
    OVERRIDE,
    SHUFFLE_NEXT,
    SHUFFLE_LAST,
    SHUFFLE_OVERRIDE,
    SHUFFLE_SIMPLE_NEXT,
    SHUFFLE_SIMPLE_LAST,
    SHUFFLE_SIMPLE_OVERRIDE
}

enum class ShuffleActionType {
    SHUFFLE_SIMPLE,
    SHUFFLE_ALBUM_ORIENTED,
    SHUFFLE_ARTIST_ORIENTED
}

enum class OrientedClassType {
    ARTIST,
    ALBUM,
    TRACK,
    GENRE
}

data class QueueMetadata(
    val actionType: InsertActionType,
    val classType: OrientedClassType
)

data class QueueInfo(
    val metadata: QueueMetadata,
    val queue: List<UiTrack>
)

fun JoinedTrack.toUiTrack(
    trackNum: Int? = null,
    nowPlaying: Boolean = false
): UiTrack {
    val (year, month, day) = dates
    return UiTrack(
        "${random.nextLong()}-${track.id}",
        track.id,
        track.mediaId,
        track.codec.uppercase(Locale.getDefault()),
        track.bitrate,
        track.sampleRate / 1000f,
        album,
        track.title,
        track.titleSort,
        artist,
        albumArtist,
        track.composer,
        track.composerSort,
        album.artworkUriString,
        track.duration,
        trackNum ?: track.trackNum,
        track.trackTotal,
        track.discNum,
        track.discTotal,
        year,
        month,
        day,
        track.genre,
        track.sourcePath,
        track.dropboxPath,
        track.dropboxExpiredAt,
        track.artworkUriString,
        BoolConverter().toBoolean(track.ignored),
        nowPlaying,
        isFavorite = track.isFavorite
    )
}

val JoinedTrack.dates: Triple<Int?, Int?, Int?>
    get() {
        val calendar = track.releaseDate?.parseDateLong()?.let {
            Calendar.getInstance().apply { time = Date(it) }
        }

        return Triple(
            calendar?.get(Calendar.YEAR),
            calendar?.get(Calendar.MONTH),
            calendar?.get(Calendar.DAY_OF_MONTH)
        )
    }

val UiTrack.isDownloaded
    get() = dropboxPath != null && sourcePath.isNotBlank() && sourcePath.matches(dropboxUrlPattern)
        .not()

val Track.isDownloaded
    get() = dropboxPath != null && sourcePath.isNotBlank() && sourcePath.matches(dropboxUrlPattern)
        .not()

suspend fun DB.searchArtistByFuzzyTitle(title: String): List<Artist> =
    this@searchArtistByFuzzyTitle.artistDao().findAllByTitle("%${title.escapeSql}%")

suspend fun DB.searchAlbumByFuzzyTitle(title: String): List<JoinedAlbum> =
    this@searchAlbumByFuzzyTitle.albumDao().findAllByTitle("%${title.escapeSql}%")

suspend fun DB.searchTrackByFuzzyTitle(title: String): List<JoinedTrack> =
    this@searchTrackByFuzzyTitle.trackDao().getAllByTitle("%${title.escapeSql}%")

suspend fun List<String>.getThumb(context: Context): Bitmap? {
    if (this.isEmpty()) return null
    val unit = 100
    val width = ((this.size * 0.9 - 0.1) * unit).toInt()
    val bitmap = Bitmap.createBitmap(width, unit, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    withContext(Dispatchers.IO) {
        this@getThumb.reversed().forEachIndexed { i, uriString ->
            val b = catchAsNull {
                Coil.imageLoader(context)
                    .execute(
                        ImageRequest.Builder(context)
                            .data(uriString)
                            .size(unit)
                            .scale(Scale.FIT)
                            .allowHardware(false)
                            .build()
                    )
                    .drawable
                    ?.toBitmap()
            } ?: return@forEachIndexed
            canvas.drawBitmap(
                b,
                bitmap.width - (i + 1) * unit * 0.9f,
                (unit - b.height) / 2f,
                Paint()
            )
        }
    }
    return bitmap
}

suspend fun String.getMediaItem(context: Context): MediaItem =
    DB.getInstance(context)
        .trackDao()
        .getBySourcePath(this)
        ?.getMediaItem(context)
        ?: this.getMediaItem()

private fun String.getMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(this)
    .setUri(Uri.parse(this))
    .build()

fun JoinedTrack.getMediaItem(context: Context): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.sourcePath)
        .setUri(Uri.parse(track.sourcePath))
        .setMediaMetadata(getMediaMetadata(context))
        .build()

fun List<UiTrack>.orderModified(
    classType: OrientedClassType,
    actionType: InsertActionType
): List<UiTrack> {
    val simpleShuffleConditional = actionType in listOf(
        InsertActionType.SHUFFLE_SIMPLE_OVERRIDE,
        InsertActionType.SHUFFLE_SIMPLE_NEXT,
        InsertActionType.SHUFFLE_SIMPLE_LAST,
    )
    if (simpleShuffleConditional) return shuffled()

    val shuffleConditional = actionType in listOf(
        InsertActionType.SHUFFLE_OVERRIDE,
        InsertActionType.SHUFFLE_NEXT,
        InsertActionType.SHUFFLE_LAST,
    )
    return this.groupBy { it.album }
        .map { (album, tracks) ->
            album to tracks.groupBy { it.discNum }
                .map { (diskNum, track) ->
                    diskNum to track.sortedBy { it.trackNum }
                }
                .sortedBy { it.first }
                .flatMap { it.second }
        }
        .let {
            if (shuffleConditional && classType == OrientedClassType.ALBUM) it.shuffled() else it
        }
        .groupBy { it.first.artistId }
        .toList()
        .let {
            if (shuffleConditional && classType == OrientedClassType.ARTIST) it.shuffled() else it
        }
        .flatMap { (_, albumTrackMap) ->
            albumTrackMap.flatMap { it.second }
        }
}

fun JoinedTrack.getMediaMetadata(context: Context): MediaMetadata {
    val (year, month, day) = dates

    return MediaMetadata.Builder()
        .setTitle(track.title)
        .setDisplayTitle(track.title)
        .setSubtitle(artist.title)
        .setDescription(album.title)
        .setArtist(artist.title)
        .setAlbumArtist(albumArtist?.title)
        .setAlbumTitle(album.title)
        .setComposer(track.composer)
        .setReleaseYear(year)
        .setReleaseMonth(month)
        .setReleaseDay(day)
        .apply {
            val artworkUriString =
                (track.artworkUriString ?: album.artworkUriString)
                    .getTempArtworkUriString(context)
            setArtworkUri(artworkUriString?.let { Uri.parse(it) })
            track.trackNum?.let { setTrackNumber(it) }
            track.trackTotal?.let { setTotalTrackCount(it) }
            track.discNum?.let { setDiscNumber(it) }
            track.discTotal?.let { setTotalDiscCount(it) }
        }
        .build()
}

fun String.parseDateLong(): Long? = catchAsNull {
    SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).parse(this)?.time
} ?: catchAsNull {
    SimpleDateFormat("yyyy-MM", Locale.JAPAN).parse(this)?.time
} ?: catchAsNull {
    SimpleDateFormat("yyyy", Locale.JAPAN).parse(this)?.time
}

fun String?.getTempArtworkUriString(context: Context): String? =
    this?.let { uriString ->
        val ext = MimeTypeMap.getFileExtensionFromUrl(uriString)
        val dirName = "images"
        val fileName = "temp_artwork.$ext"
        val dir = File(context.cacheDir, dirName)
        val file = File(dir, fileName)

        if (file.exists()) file.delete()
        if (dir.exists().not()) dir.mkdirs()

        File(uriString).copyTo(file, overwrite = true)
        return FileProvider.getUriForFile(context, BuildConfig.FILES_AUTHORITY, file)
            .toString()
    }

fun Long.getTimeString(): String {
    val hour = this / 3600000
    val minute = (this % 3600000) / 60000
    val second = (this % 60000) / 1000
    return (if (hour > 0) String.format("%d:", hour) else "") + String.format(
        "%02d:%02d", minute, second
    )
}

fun DbxClientV2.saveTempAudioFile(context: Context, pathLower: String): File {
    val dirName = "audio"
    val fileName = "temp_audio.${pathLower.getExtension()}"
    val dir = File(context.cacheDir, dirName)
    val file = File(dir, fileName)

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    FileOutputStream(file).use { files().download(pathLower).download(it) }

    return file
}

fun DbxClientV2.saveAudioFile(context: Context, id: String, pathLower: String): File {
    val dirName = "audio"
    val dir = File(context.cacheDir, dirName)
    val file = File(dir, "$id.${pathLower.getExtension()}")

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    FileOutputStream(file).use { files().download(pathLower).download(it) }

    return file
}

fun InputStream.saveTempAudioFile(context: Context): File {
    val ext = URLConnection.guessContentTypeFromStream(this)
        ?.replace(Regex(".+/(.+)"), ".$1")
        ?: ""

    val dirName = "audio"
    val fileName = "temp_audio$ext"
    val dir = File(context.cacheDir, dirName)
    val file = File(dir, fileName)

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    FileUtils.copyToFile(this, file)

    return file
}

suspend fun DialogEditMetadataBinding.updateFileMetadata(
    context: Context,
    db: DB,
    targets: List<JoinedTrack>
) {
    targets.asSequence().forEach {
        it.updateFileMetadata(
            context,
            db,
            inputTrackName.text?.toString(),
            inputTrackNameKana.text?.toString(),
            inputAlbumName.text?.toString(),
            inputAlbumNameKana.text?.toString(),
            inputArtistName.text?.toString(),
            inputArtistNameKana.text?.toString(),
            inputComposerName.text?.toString(),
            inputComposerNameKana.text?.toString(),
        )
    }
}

/**
 * Media placed at the outside of the device will be updated only data on the database
 */
suspend fun JoinedTrack.updateFileMetadata(
    context: Context,
    db: DB,
    newTrackName: String? = null,
    newTrackNameSort: String? = null,
    newAlbumName: String? = null,
    newAlbumNameSort: String? = null,
    newArtistName: String? = null,
    newArtistNameSort: String? = null,
    newComposerName: String? = null,
    newComposerNameSort: String? = null,
    newArtwork: Bitmap? = null
) = withContext(Dispatchers.IO) {
    catchAsNull {
        val artworkUriString = newArtwork?.toByteArray()?.storeArtwork(context)
        val artwork = artworkUriString?.let { ArtworkFactory.createArtworkFromFile(File(it)) }
        when {
            newArtistName.isNullOrBlank().not() || newArtistNameSort.isNullOrBlank().not() -> {
                db.trackDao().getAllByArtist(artist.id)
                    .mapNotNull {
                        if (it.track.sourcePath.startsWith("http")) null
                        else AudioFileIO.read(File(it.track.sourcePath))
                    }
                    .forEach { existingAudioFile ->
                        existingAudioFile.tag?.apply {
                            newArtistName?.let { setField(FieldKey.ARTIST, it) }
                            newArtistNameSort?.let { setField(FieldKey.ARTIST_SORT, it) }
                            newAlbumName?.let { setField(FieldKey.ALBUM, it) }
                            newAlbumNameSort?.let { setField(FieldKey.ALBUM_SORT, it) }
                            artwork?.let { setField(it) }
                            newTrackName?.let { setField(FieldKey.TITLE, it) }
                            newTrackNameSort?.let { setField(FieldKey.TITLE_SORT, it) }
                            newComposerName?.let { setField(FieldKey.COMPOSER, it) }
                            newComposerNameSort?.let { setField(FieldKey.COMPOSER_SORT, it) }
                        }

                        existingAudioFile.let { AudioFileIO.write(it) }
                    }
                val artistId = db.artistDao().upsert(
                    db,
                    artist.let {
                        it.copy(
                            title = newArtistName ?: it.title,
                            titleSort = newArtistNameSort ?: it.titleSort
                        )
                    }
                )
                val albumId = if (newAlbumName.isNullOrBlank().not()
                    || newAlbumNameSort.isNullOrBlank().not()
                ) {
                    db.albumDao().upsert(
                        db,
                        album.let {
                            it.copy(
                                title = newAlbumName ?: it.title,
                                titleSort = newAlbumNameSort ?: it.titleSort,
                                artistId = artistId,
                                artworkUriString = artworkUriString ?: it.artworkUriString
                            )
                        }
                    )
                } else album.id
                if (newTrackName.isNullOrBlank().not() || newTrackNameSort.isNullOrBlank().not()) {
                    db.trackDao().insert(
                        track.copy(
                            title = newTrackName ?: track.title,
                            titleSort = newTrackNameSort ?: track.titleSort,
                            composer = newComposerName ?: track.composer,
                            composerSort = newComposerNameSort ?: track.composerSort,
                            artistId = artistId,
                            albumId = albumId
                        )
                    )
                }
            }

            newAlbumName.isNullOrBlank().not()
                    || newAlbumNameSort.isNullOrBlank().not()
                    || newArtwork != null -> {
                db.trackDao().getAllByAlbum(album.id)
                    .mapNotNull {
                        if (it.track.sourcePath.startsWith("http")) null
                        else AudioFileIO.read(File(it.track.sourcePath))
                    }
                    .forEach { existingAudioFile ->
                        existingAudioFile.tag?.apply {
                            newAlbumName?.let { setField(FieldKey.ALBUM, it) }
                            newAlbumNameSort?.let { setField(FieldKey.ALBUM_SORT, it) }
                            artwork?.let { setField(it) }
                            newTrackName?.let { setField(FieldKey.TITLE, it) }
                            newTrackNameSort?.let { setField(FieldKey.TITLE_SORT, it) }
                            newComposerName?.let { setField(FieldKey.COMPOSER, it) }
                            newComposerNameSort?.let { setField(FieldKey.COMPOSER_SORT, it) }
                        }

                        existingAudioFile.let { AudioFileIO.write(it) }
                    }
                val albumId = db.albumDao().upsert(
                    db,
                    album.let {
                        it.copy(
                            title = newAlbumName ?: it.title,
                            titleSort = newAlbumNameSort ?: it.titleSort,
                            artworkUriString = artworkUriString
                                ?: it.artworkUriString
                        )
                    }
                )
                if (newTrackName.isNullOrBlank().not() || newTrackNameSort.isNullOrBlank().not()) {
                    db.trackDao().insert(
                        track.copy(
                            title = newTrackName ?: track.title,
                            titleSort = newTrackNameSort ?: track.titleSort,
                            composer = newComposerName ?: track.composer,
                            composerSort = newComposerNameSort ?: track.composerSort,
                            albumId = albumId
                        )
                    )
                }
            }

            newTrackName.isNullOrBlank().not() || newTrackNameSort.isNullOrBlank().not() -> {
                if (track.sourcePath.startsWith("http").not()) {
                    AudioFileIO.read(File(track.sourcePath))?.let { audioFile ->
                        audioFile.tag?.apply {
                            newTrackName?.let { setField(FieldKey.TITLE, it) }
                            newTrackNameSort?.let { setField(FieldKey.TITLE_SORT, it) }
                            newComposerName?.let { setField(FieldKey.COMPOSER, it) }
                            newComposerNameSort?.let { setField(FieldKey.COMPOSER_SORT, it) }
                        }
                        AudioFileIO.write(audioFile)
                    }
                }

                db.trackDao().update(
                    track.copy(
                        title = newTrackName ?: track.title,
                        titleSort = newTrackNameSort ?: track.titleSort,
                        composer = newComposerName ?: track.composer,
                        composerSort = newComposerNameSort ?: track.composerSort
                    )
                )
            }
        }
        return@withContext
    }
}

val ExoPlayer.currentSourcePaths: List<String>
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) get() = List(this.mediaItemCount) { index ->
        this.getMediaItemAt(index).localConfiguration?.uri?.toString()
    }.filterNotNull()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun MediaItem.toUiTrack(db: DB): UiTrack? =
    (localConfiguration?.uri ?: mediaId).toString().toUiTrack(db)

suspend fun String.toUiTrack(db: DB): UiTrack? =
    db.trackDao().getBySourcePath(this)?.toUiTrack()

suspend fun List<String>.toDomainTracks(db: DB): List<UiTrack> =
    db.trackDao().getAllBySourcePaths(this).map { it.toUiTrack() }

fun com.geckour.q.domain.model.MediaItem?.isFavoriteToggled(): com.geckour.q.domain.model.MediaItem? =
    when (this) {
        is UiTrack -> {
            copy(isFavorite = isFavorite.not())
        }

        is Album -> {
            copy(isFavorite = isFavorite.not())
        }

        is Artist -> {
            copy(isFavorite = isFavorite.not())
        }

        else -> null
    }

suspend fun JoinedTrack.verifiedWithDropbox(
    context: Context,
    client: DbxClientV2,
    force: Boolean = false
): JoinedTrack? =
    withContext(Dispatchers.IO) {
        track.dropboxPath ?: return@withContext null

        if (force
            || track.sourcePath.isBlank()
            || (track.sourcePath.matches(dropboxUrlPattern)
                    && (track.dropboxExpiredAt ?: 0) <= System.currentTimeMillis())
            || (track.sourcePath.matches(dropboxUrlPattern).not()
                    && Uri.parse(track.sourcePath).toFile().exists().not())
        ) {
            val url = client.files().getTemporaryLink(track.dropboxPath).link
            val expiredAt = System.currentTimeMillis() + DROPBOX_EXPIRES_IN

            val trackDao = DB.getInstance(context).trackDao()
            trackDao.get(track.id)?.let { joinedTrack ->
                trackDao.update(
                    joinedTrack.track.copy(
                        sourcePath = url,
                        dropboxExpiredAt = expiredAt
                    )
                )
            }

            return@withContext copy(
                track = track.copy(
                    sourcePath = url,
                    dropboxExpiredAt = expiredAt
                )
            )
        }

        return@withContext null
    }

suspend fun UiTrack.verifiedWithDropbox(
    context: Context,
    client: DbxClientV2,
    force: Boolean = false
): UiTrack? =
    withContext(Dispatchers.IO) {
        dropboxPath ?: return@withContext null

        if (force
            || sourcePath.isBlank()
            || (sourcePath.matches(dropboxUrlPattern)
                    && (dropboxExpiredAt ?: 0) <= System.currentTimeMillis())
            || (sourcePath.matches(dropboxUrlPattern).not()
                    && Uri.parse(sourcePath).toFile().exists().not())
        ) {
            val url = client.files().getTemporaryLink(dropboxPath).link
            val expiredAt = System.currentTimeMillis() + DROPBOX_EXPIRES_IN

            val trackDao = DB.getInstance(context).trackDao()
            trackDao.get(id)?.let { joinedTrack ->
                trackDao.update(
                    joinedTrack.track.copy(
                        sourcePath = url,
                        dropboxExpiredAt = expiredAt
                    )
                )
            }

            return@withContext copy(
                sourcePath = url,
                dropboxExpiredAt = expiredAt
            )
        }

        return@withContext null
    }

private fun Bitmap.toByteArray(): ByteArray =
    ByteArrayOutputStream().apply { compress(Bitmap.CompressFormat.PNG, 100, this) }
        .toByteArray()

private val String.escapeSql: String get() = replace("'", "''")