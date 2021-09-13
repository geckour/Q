package com.geckour.q.util

import android.app.Notification
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.databinding.DialogEditMetadataBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


const val UNKNOWN: String = "UNKNOWN"

const val DROPBOX_EXPIRES_IN = 14400000L

enum class InsertActionType {
    NEXT, LAST, OVERRIDE, SHUFFLE_NEXT, SHUFFLE_LAST, SHUFFLE_OVERRIDE, SHUFFLE_SIMPLE_NEXT, SHUFFLE_SIMPLE_LAST, SHUFFLE_SIMPLE_OVERRIDE
}

enum class ShuffleActionType {
    SHUFFLE_SIMPLE, SHUFFLE_ALBUM_ORIENTED, SHUFFLE_ARTIST_ORIENTED
}

enum class OrientedClassType {
    ARTIST, ALBUM, TRACK, GENRE
}

enum class PlayerControlCommand {
    DESTROY
}

enum class SettingCommand {
    SET_EQUALIZER, UNSET_EQUALIZER, REFLECT_EQUALIZER_SETTING
}

data class QueueMetadata(
    val actionType: InsertActionType, val classType: OrientedClassType
)

data class QueueInfo(
    val metadata: QueueMetadata, val queue: List<DomainTrack>
)

suspend fun getTrackListFromTrackMediaId(
    db: DB, dbTrackIdList: List<Long>,
    genreId: Long? = null
): List<DomainTrack> = dbTrackIdList.mapNotNull { getDomainTrack(db, it, genreId) }

suspend fun getDomainTrack(
    db: DB,
    trackMediaId: Long,
    genreId: Long? = null,
    trackNum: Int? = null
): DomainTrack? = db.trackDao()
    .getByMediaId(trackMediaId)
    ?.toDomainTrack(genreId, trackNum)

fun JoinedTrack.toDomainTrack(
    genreId: Long? = null,
    trackNum: Int? = null
): DomainTrack {
    return DomainTrack(
        track.id,
        track.mediaId,
        track.codec.uppercase(Locale.getDefault()),
        track.bitrate,
        track.sampleRate / 1000f,
        album,
        track.title,
        track.titleSort,
        artist,
        track.composer,
        track.composerSort,
        album.artworkUriString,
        track.duration,
        trackNum ?: track.trackNum,
        track.trackTotal,
        track.discNum,
        track.discTotal,
        track.releaseDate,
        genreId,
        track.sourcePath,
        track.dropboxPath,
        track.dropboxExpiredAt,
        track.artworkUriString,
        BoolConverter().toBoolean(track.ignored)
    )
}

suspend fun DB.searchArtistByFuzzyTitle(title: String): List<Artist> =
    this@searchArtistByFuzzyTitle.artistDao().findAllByTitle("%${title.escapeSql}%")

suspend fun DB.searchAlbumByFuzzyTitle(title: String): List<JoinedAlbum> =
    this@searchAlbumByFuzzyTitle.albumDao().findAllByTitle("%${title.escapeSql}%")

suspend fun DB.searchTrackByFuzzyTitle(title: String): List<JoinedTrack> =
    this@searchTrackByFuzzyTitle.trackDao().getAllByTitle("%${title.escapeSql}%")

fun Context.searchGenreByFuzzyTitle(title: String): List<Genre> = contentResolver.query(
    MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
    arrayOf(
        MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME
    ),
    "${MediaStore.Audio.Genres.NAME} like '%${title.escapeSql}%'",
    null,
    MediaStore.Audio.Genres.DEFAULT_SORT_ORDER
).use {
    it ?: return@use emptyList()
    val result: MutableList<Genre> = mutableListOf()
    while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres._ID))
        val name = it.getString(it.getColumnIndex(MediaStore.Audio.Genres.NAME)) ?: UNKNOWN
        result.add(Genre(id, null, name, 0))
    }

    return@use result
}

fun <T> List<T>.takeOrFillNull(n: Int): List<T?> =
    this.take(n).let { it + List(n - it.size) { null } }

suspend fun List<String?>.getThumb(context: Context): Bitmap? {
    if (this.isEmpty()) return null
    val unit = 100
    val bitmap = Bitmap.createBitmap(
        ((this.size * 0.9 - 0.1) * unit).toInt(), unit, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    withContext(Dispatchers.IO) {
        this@getThumb.filterNotNull().reversed().forEachIndexed { i, uriString ->
            val b = catchAsNull {
                Glide.with(context)
                    .asBitmap()
                    .load(uriString)
                    .applyDefaultSettings()
                    .submit()
                    .get()
                    ?.let {
                        Bitmap.createScaledBitmap(
                            it, unit, (it.height * unit.toFloat() / it.width).toInt(), false
                        )
                    }
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

fun Genre.getTrackMediaIds(context: Context): List<Long> =
    getTrackMediaIdsByGenreId(context, this.id)

fun getTrackMediaIdsByGenreId(context: Context, genreId: Long): List<Long> =
    context.contentResolver.query(
        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
        arrayOf(MediaStore.Audio.Genres.Members._ID),
        null,
        null,
        null
    )?.use {
        val trackMediaIdList: ArrayList<Long> = ArrayList()
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres.Members._ID))
            trackMediaIdList.add(id)
        }

        return@use trackMediaIdList
    } ?: emptyList()

fun DomainTrack.getMediaSource(mediaSourceFactory: ProgressiveMediaSource.Factory): MediaSource =
    mediaSourceFactory.createMediaSource(
        MediaItem.fromUri(
            if (mediaId < 0) Uri.parse(sourcePath)
            else Uri.fromFile(File(sourcePath))
        )
    )

fun List<DomainTrack>.sortedByTrackOrder(): List<DomainTrack> =
    this.groupBy { it.album }
        .map { (album, tracks) ->
            album to tracks.groupBy { it.discNum }
                .map { (diskNum, track) ->
                    diskNum to track.sortedBy { it.trackNum }
                }
                .sortedBy { it.first }
                .flatMap { it.second }
        }
        .groupBy { it.first.artistId }
        .flatMap { (_, albumTrackMap) ->
            albumTrackMap.flatMap { it.second }
        }

fun List<DomainTrack>.shuffleByClassType(classType: OrientedClassType): List<DomainTrack> =
    when (classType) {
        OrientedClassType.ARTIST -> {
            val artists = this.map { it.artist }.distinct().shuffled()
            artists.map { artist ->
                this.filter { it.artist == artist }
            }.flatten()
        }
        OrientedClassType.ALBUM -> {
            val albumIds = this.map { it.album.id }.distinct().shuffled()
            albumIds.map { id ->
                this.filter { it.album.id == id }
            }.flatten()
        }
        OrientedClassType.TRACK -> {
            this.shuffled()
        }
        OrientedClassType.GENRE -> {
            val genreIds = this.map { it.genreId }.distinct().shuffled()
            genreIds.map { id ->
                this.filter { it.genreId == id }
            }.flatten()
        }
    }

suspend fun DomainTrack.getMediaMetadata(context: Context): MediaMetadataCompat =
    MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId.toString())
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, sourcePath)
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist.title)
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, album.title)
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist.title)
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album.title)
        .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, composer)
        .apply {
            val artworkUriString = getTempArtworkUriString(context)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artworkUriString)
            putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artworkUriString)
            trackNum?.let { putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, it.toLong()) }
            trackTotal?.let { putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, it.toLong()) }
            discNum?.let { putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, it.toLong()) }
            releaseDate?.parseDateLong()?.let { putLong(MediaMetadataCompat.METADATA_KEY_YEAR, it) }
            DB.getInstance(context).artistDao().get(album.artistId)?.title?.let {
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, it)
            }
        }
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        .build()

fun String.parseDateLong(): Long? = catchAsNull {
    SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).parse(this)?.time
} ?: catchAsNull {
    SimpleDateFormat("yyyy-MM", Locale.JAPAN).parse(this)?.time
} ?: catchAsNull {
    SimpleDateFormat("yyyy", Locale.JAPAN).parse(this)?.time
}

fun DomainTrack.getTempArtworkUriString(context: Context): String? =
    album.artworkUriString?.let { uriString ->
        val ext = MimeTypeMap.getFileExtensionFromUrl(uriString)
        File(uriString).inputStream().use { inputStream ->
            val title = "Q_temporary_artwork"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, title)
                put(MediaStore.Images.Media.DISPLAY_NAME, title)
                put(
                    MediaStore.Images.Media.DESCRIPTION,
                    "Temporary stored artwork to set on Notification"
                )
                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                )
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            val uri = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null,
                "${MediaStore.Images.Media.TITLE} = '$title'",
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        it.getLong(it.getColumnIndex(MediaStore.Images.Media._ID))
                    )
                } else null
            } ?: context.contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

            context.contentResolver
                .openOutputStream(uri)
                ?.use { it.write(inputStream.readBytes()) }

            return@use uri.toString()
        }
    }

fun getPlayerNotification(
    context: Context,
    mediaSession: MediaSessionCompat,
    playing: Boolean
): Notification {
    val description = mediaSession.controller.metadata.description

    return context.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_PLAYER)
        .setSmallIcon(R.drawable.ic_notification_player)
        .setLargeIcon(description.iconBitmap)
        .setContentTitle(description.title)
        .setContentText(description.subtitle)
        .setSubText(description.description)
        .setOngoing(playing)
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.sessionToken)
        )
        .setShowWhen(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(mediaSession.controller.sessionActivity)
        .setDeleteIntent(
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_STOP
            )
        )
        .addAction(
            NotificationCompat.Action(
                R.drawable.ic_backward,
                context.getString(R.string.notification_action_prev),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
        )
        .addAction(
            if (playing) {
                NotificationCompat.Action(
                    R.drawable.ic_pause,
                    context.getString(R.string.notification_action_pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PAUSE
                    )
                )
            } else {
                NotificationCompat.Action(
                    R.drawable.ic_play,
                    context.getString(R.string.notification_action_play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PLAY
                    )
                )
            }
        )
        .addAction(
            NotificationCompat.Action(
                R.drawable.ic_forward,
                context.getString(R.string.notification_action_next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
        )
        .build()
}

fun Long.getTimeString(): String {
    val hour = this / 3600000
    val minute = (this % 3600000) / 60000
    val second = (this % 60000) / 1000
    return (if (hour > 0) String.format("%d:", hour) else "") + String.format(
        "%02d:%02d", minute, second
    )
}

val String?.orDefaultForModel get() = this?.let { File(this) } ?: R.drawable.ic_empty
val Bitmap?.orDefaultForModel get() = this ?: R.drawable.ic_empty

inline fun <reified T> RequestBuilder<T>.applyDefaultSettings() =
    this.diskCacheStrategy(DiskCacheStrategy.NONE)

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

    FileOutputStream(file).use { it.write(this.readBytes()) }

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
                var albumId = album.id
                if (newAlbumName.isNullOrBlank().not() || newAlbumNameSort.isNullOrBlank().not()) {
                    albumId = db.albumDao().upsert(
                        db,
                        album.let {
                            it.copy(
                                title = newAlbumName ?: it.title,
                                titleSort = newAlbumNameSort ?: it.titleSort,
                                artistId = artistId,
                                artworkUriString = artworkUriString
                                    ?: it.artworkUriString
                            )
                        }
                    )
                }
                if (newTrackName.isNullOrBlank().not() || newTrackNameSort.isNullOrBlank().not()) {
                    db.trackDao().upsert(
                        track.copy(
                            title = newTrackName ?: track.title,
                            titleSort = newTrackNameSort ?: track.titleSort,
                            composer = newComposerName ?: track.composer,
                            composerSort = newComposerNameSort ?: track.composerSort,
                            artistId = artistId,
                            albumId = albumId
                        ),
                        albumId,
                        artistId,
                        track.duration
                    )
                }
            }
            newAlbumName.isNullOrBlank().not() || newAlbumNameSort.isNullOrBlank()
                .not() || newArtwork != null -> {
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
                    db.trackDao().upsert(
                        track.copy(
                            title = newTrackName ?: track.title,
                            titleSort = newTrackNameSort ?: track.titleSort,
                            composer = newComposerName ?: track.composer,
                            composerSort = newComposerNameSort ?: track.composerSort,
                            albumId = albumId
                        ),
                        artist.id,
                        albumId,
                        track.duration
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

val ConcatenatingMediaSource.currentSourcePaths: List<String> get() =
    (0 until this.size).mapNotNull {
        getMediaSource(it).mediaItem.playbackProperties?.uri?.toString()
    }

suspend fun MediaSource.toDomainTrack(db: DB): DomainTrack? =
    mediaItem.playbackProperties?.uri?.toString()?.toDomainTrack(db)

suspend fun String.toDomainTrack(db: DB): DomainTrack? =
    db.trackDao().getBySourcePath(this)?.toDomainTrack()

/**
 * @return First value of `Pair` is the old (passed) sourcePath.
 */
suspend fun DomainTrack.verifyWithDropbox(
    context: Context,
    client: DbxClientV2,
    force: Boolean = false
): DomainTrack =
    withContext(Dispatchers.IO) {
        dropboxPath ?: return@withContext this@verifyWithDropbox

        if (force || ((dropboxExpiredAt ?: 0) <= System.currentTimeMillis())) {
            val currentTime = System.currentTimeMillis()
            val url = client.files().getTemporaryLink(dropboxPath).link
            val expiredAt = currentTime + DROPBOX_EXPIRES_IN

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

        return@withContext this@verifyWithDropbox
    }

private fun Bitmap.toByteArray(): ByteArray =
    ByteArrayOutputStream().apply { compress(Bitmap.CompressFormat.PNG, 100, this) }
        .toByteArray()

private val String.escapeSql: String get() = replace("'", "''")