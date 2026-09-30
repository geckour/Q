package com.geckour.q.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.icu.util.Calendar
import android.icu.util.TimeZone
import androidx.core.graphics.createBitmap
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.R
import com.geckour.q.data.db.BoolConverter
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.dao.ArtistDao
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedSavedQueueTrack
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.JoinedTrackHistory
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.data.db.model.Track
import com.geckour.q.data.db.model.TrackRef
import com.geckour.q.domain.model.UiTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random


const val UNKNOWN: String = "UNKNOWN"

const val DROPBOX_EXPIRES_IN = 14400000L

private const val AUDIO_DIR_NAME = "audio"

private const val DOWNLOAD_BUFFER_SIZE = 16 * 1024

private const val DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 30L

private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L

private val downloadClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

private val random = Random(System.currentTimeMillis())

val dailyRandom: Random
    get() = Calendar.getInstance(TimeZone.getDefault()).let {
        Random(it.get(Calendar.YEAR) * 1000L + it.get(Calendar.DAY_OF_YEAR))
    }

val dropboxUrlPattern = Regex("^https://.+\\.dl\\.dropboxusercontent\\.com/.+$")

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
    val queue: List<JoinedTrack>
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

fun SpotifyTrack.toUiTrack(nowPlaying: Boolean = false): UiTrack {
    val (year, month, day) = releaseDate.releaseDates
    val album = Album(
        id = 0,
        artistId = 0,
        title = albumName,
        titleSort = albumName,
        artworkUriString = artworkUrl,
        hasAlbumArtist = albumArtistName != null,
        playbackCount = 0,
        totalDuration = 0,
    )
    return UiTrack(
        key = "${random.nextLong()}-$uri",
        id = 0,
        mediaId = -1,
        codec = "Spotify",
        bitrate = 0,
        sampleRate = 0f,
        album = album,
        title = title,
        titleSort = title,
        artist = spotifyArtist(artistName),
        albumArtist = albumArtistName?.let { spotifyArtist(it) },
        composer = null,
        composerSort = null,
        thumbUriString = artworkUrl,
        duration = duration,
        trackNum = trackNum,
        trackTotal = trackTotal,
        discNum = discNum,
        discTotal = null,
        releaseYear = year,
        releaseMonth = month,
        releaseDay = day,
        genreName = null,
        sourcePath = uri,
        dropboxPath = null,
        dropboxExpiredAt = null,
        artworkUriString = artworkUrl,
        ignored = false,
        nowPlaying = nowPlaying,
        isFavorite = isFavorite,
    )
}

fun JoinedTrackHistory.toUiTrack(): UiTrack? =
    joinedTrack?.toUiTrack() ?: spotifyTrack?.toUiTrack()

fun JoinedSavedQueueTrack.toUiTrack(): UiTrack? =
    joinedTrack?.toUiTrack() ?: spotifyTrack?.toUiTrack()

val UiTrack.trackRef: TrackRef
    get() = if (isSpotify) TrackRef.ofSpotify(sourcePath) else TrackRef(id)

private fun SpotifyTrack.spotifyArtist(name: String): Artist =
    Artist(
        id = 0,
        title = name,
        titleSort = name,
        playbackCount = 0,
        totalDuration = 0,
        artworkUriString = artworkUrl,
    )

val JoinedTrack.dates: Triple<Int?, Int?, Int?>
    get() = track.releaseDate.releaseDates

private val String?.releaseDates: Triple<Int?, Int?, Int?>
    get() {
        val releaseDateString = this ?: return Triple(null, null, null)

        runCatching {
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).parse(releaseDateString)
            }
            return Triple(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
        runCatching {
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM", Locale.JAPAN).parse(releaseDateString)
            }
            return Triple(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                null
            )
        }
        runCatching {
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy", Locale.JAPAN).parse(releaseDateString)
            }
            return Triple(
                calendar.get(Calendar.YEAR),
                null,
                null
            )
        }

        return Triple(null, null, null)
    }

val UiTrack.isDownloaded get() = dropboxPath != null && sourcePath.existsAsFile()

val Track.isDownloaded get() = dropboxPath != null && sourcePath.existsAsFile()

fun ArtistDao.isAllIncludingTracksDownloadedAsFlow(artistId: Long): Flow<Boolean> =
    combine(
        getIncludingDropboxSourcePathsAsFlow(artistId),
        DownloadState.changedCount
    ) { sourcePaths, _ ->
        sourcePaths.all { it.existsAsFile() }
    }.flowOn(Dispatchers.IO)

private fun String.existsAsFile(): Boolean =
    isNotBlank() &&
            matches(dropboxUrlPattern).not() &&
            runCatching { toUri().toFile().exists() }.getOrDefault(false)

suspend fun List<String?>.getThumb(context: Context): Bitmap? {
    if (this.isEmpty()) return null
    val unit = 100
    val width = ((this.size * 0.9 - 0.1) * unit).toInt()
    val bitmap = createBitmap(width, unit)
    val canvas = Canvas(bitmap)
    withContext(Dispatchers.IO) {
        this@getThumb.reversed()
            .fold(emptyList<String?>()) { acc, uriString ->
                if (acc.isNotEmpty() && acc.last() == uriString) acc else acc + uriString
            }
            .forEachIndexed { i, uriString ->
                val b = catchAsNull {
                    SingletonImageLoader.get(context)
                        .execute(
                            ImageRequest.Builder(context)
                                .data(uriString ?: R.drawable.ic_empty)
                                .size(unit)
                                .scale(Scale.FIT)
                                .allowHardware(false)
                                .build()
                        )
                        .image
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
    getMediaItemOrNull(context) ?: this.getMediaItem()

suspend fun String.getMediaItemOrNull(context: Context): MediaItem? {
    val db = DB.getInstance(context)
    return if (isSpotifySourcePath) db.spotifyTrackDao().get(this)?.getMediaItem()
    else db.trackDao().getBySourcePath(this)?.getMediaItem()
}

private fun String.getMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(this)
    .setUri(this.toUri())
    .build()

fun JoinedTrack.getMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.sourcePath)
        .setUri(track.sourcePath.toUri())
        .setMediaMetadata(getMediaMetadata())
        .build()

fun SpotifyTrack.getMediaItem(): MediaItem {
    val (year, month, day) = releaseDate.releaseDates

    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setSubtitle(artistName)
                .setDescription(albumName)
                .setArtist(artistName)
                .setAlbumArtist(albumArtistName)
                .setAlbumTitle(albumName)
                .setReleaseYear(year)
                .setReleaseMonth(month)
                .setReleaseDay(day)
                .setArtworkUri(artworkUrl?.toUri())
                .setDurationMs(duration)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply {
                    trackNum?.let { setTrackNumber(it) }
                    trackTotal?.let { setTotalTrackCount(it) }
                    discNum?.let { setDiscNumber(it) }
                }
                .build()
        )
        .build()
}

fun List<JoinedTrack>.orderModified(
    classType: OrientedClassType,
    actionType: InsertActionType
): List<JoinedTrack> {
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
            album to tracks.groupBy { it.track.discNum }
                .map { (diskNum, track) ->
                    diskNum to track.sortedBy { it.track.trackNum }
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

@JvmName("orderModifiedUiTracks")
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
    return groupBy { if (it.isSpotify) it.album.title else it.album.id }
        .values
        .map { tracks -> tracks.sortedWith(compareBy({ it.discNum }, { it.trackNum })) }
        .let {
            if (shuffleConditional && classType == OrientedClassType.ALBUM) it.shuffled() else it
        }
        .groupBy { tracks ->
            val track = tracks.first()
            if (track.isSpotify) (track.albumArtist ?: track.artist).title
            else track.album.artistId
        }
        .values
        .let {
            if (shuffleConditional && classType == OrientedClassType.ARTIST) it.shuffled() else it
        }
        .flatMap { albums -> albums.flatten() }
}

fun JoinedTrack.getMediaMetadata(): MediaMetadata {
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
        .setArtworkUri((track.artworkUriString ?: album.artworkUriString)?.toUri())
        .setGenre(track.genre)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply {
            track.trackNum?.let { setTrackNumber(it) }
            track.trackTotal?.let { setTotalTrackCount(it) }
            track.discNum?.let { setDiscNumber(it) }
            track.discTotal?.let { setTotalDiscCount(it) }
        }
        .build()
}

fun Long.getDateTimeString(): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.JAPAN).format(Date(this))

fun Long.getTimeString(withMillis: Boolean = false): String {
    val absoluteValue = abs(this)
    val hour = absoluteValue / 3600000
    val minute = (absoluteValue % 3600000) / 60000
    val second = (absoluteValue % 60000) / 1000
    val secondWithMillis = (absoluteValue % 60000) / 1000.0
    return (if (this < 0) "-" else "") +
            (if (hour > 0) String.format("%d:", hour) else "") +
            (if (withMillis) String.format("%02d:%05.2f", minute, secondWithMillis)
            else String.format("%02d:%02d", minute, second))
}

fun saveTempAudioFileFromUrl(
    context: Context,
    id: String,
    pathLower: String,
    url: String,
): Flow<Pair<File, Long?>> = saveFileFromUrl(tempAudioFile(context, id, pathLower), url)

fun saveAudioFileFromUrl(
    context: Context,
    id: String,
    pathLower: String,
    url: String,
): Flow<Pair<File, Long?>> = saveFileFromUrl(audioFile(context, id, pathLower), url)

private fun tempAudioFile(context: Context, id: String, pathLower: String): File {
    val dir = File(context.cacheDir, AUDIO_DIR_NAME)
    val file = File(dir, "temp_$id.${pathLower.getExtension()}")

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    return file
}

private fun audioFile(context: Context, id: String, pathLower: String): File {
    val dir = File(context.dataDir, AUDIO_DIR_NAME)
    val file = File(dir, "$id.${pathLower.getExtension()}")

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    return file
}

class DownloadFailedException(val code: Int) : IOException("Failed to download: $code")

private fun saveFileFromUrl(
    file: File,
    url: String,
): Flow<Pair<File, Long?>> {
    return callbackFlow {
        downloadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful.not()) {
                throw DownloadFailedException(response.code)
            }

            response.body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var processed = 0L
                    while (true) {
                        ensureActive()

                        val read = input.read(buffer)
                        if (read < 0) break

                        output.write(buffer, 0, read)
                        processed += read
                        trySend(file to processed)
                    }
                }
            }
        }
        trySend(file to null)
        channel.close()
    }.flowOn(Dispatchers.IO)
}

val ExoPlayer.currentSourcePaths: List<String>
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) get() = List(this.mediaItemCount) { index ->
        this.getMediaItemAt(index).localConfiguration?.uri?.toString()
    }.filterNotNull()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun MediaItem.toUiTrack(db: DB): UiTrack? =
    (localConfiguration?.uri ?: mediaId).toString().toUiTrack(db)

suspend fun String.toUiTrack(db: DB): UiTrack? =
    if (isSpotifySourcePath) db.spotifyTrackDao().get(this)?.toUiTrack()
    else db.trackDao().getBySourcePath(this)?.toUiTrack()

suspend fun List<String>.toDomainTracks(db: DB): List<UiTrack> {
    val (spotifyUris, sourcePaths) = partition { it.isSpotifySourcePath }
    return db.trackDao().getAllBySourcePaths(sourcePaths).map { it.toUiTrack() } +
            db.spotifyTrackDao().getAllByUris(spotifyUris).map { it.toUiTrack() }
}

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
                    && track.sourcePath.toUri().toFile().exists().not())
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

val String.escapeSql: String
    get() = replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")