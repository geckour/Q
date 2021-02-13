package com.geckour.q.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.App
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.LauncherActivity
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
import kotlin.math.min


const val UNKNOWN: String = "UNKNOWN"

enum class InsertActionType {
    NEXT, LAST, OVERRIDE, SHUFFLE_NEXT, SHUFFLE_LAST, SHUFFLE_OVERRIDE, SHUFFLE_SIMPLE_NEXT, SHUFFLE_SIMPLE_LAST, SHUFFLE_SIMPLE_OVERRIDE
}

enum class ShuffleActionType {
    SHUFFLE_SIMPLE, SHUFFLE_ALBUM_ORIENTED, SHUFFLE_ARTIST_ORIENTED
}

enum class OrientedClassType {
    ARTIST, ALBUM, SONG, GENRE, PLAYLIST, DROPBOX
}

enum class PlayerControlCommand {
    PLAY_PAUSE, PAUSE, NEXT, PREV, DESTROY
}

enum class SettingCommand {
    SET_EQUALIZER, UNSET_EQUALIZER, REFLECT_EQUALIZER_SETTING
}

data class QueueMetadata(
    val actionType: InsertActionType, val classType: OrientedClassType
)

data class QueueInfo(
    val metadata: QueueMetadata, val queue: List<Song>
)

suspend fun getSongListFromTrackMediaId(
    db: DB, dbTrackIdList: List<Long>, genreId: Long? = null, playlistId: Long? = null
): List<Song> = dbTrackIdList.mapNotNull { getSong(db, it, genreId, playlistId) }

suspend fun getSongListFromTrackMediaIdWithTrackNum(
    db: DB,
    dbTrackMediaIdWithTrackNumList: List<Pair<Long, Int>>,
    genreId: Long? = null,
    playlistId: Long? = null
): List<Song> = dbTrackMediaIdWithTrackNumList.mapNotNull {
    getSong(db, it.first, genreId, playlistId, trackNum = it.second)
}

suspend fun getSong(
    db: DB,
    trackMediaId: Long,
    genreId: Long? = null,
    playlistId: Long? = null,
    trackNum: Int? = null
): Song? = db.trackDao()
    .getByMediaId(trackMediaId)
    ?.toSong(genreId, playlistId, trackNum = trackNum)

fun JoinedTrack.toSong(
    genreId: Long? = null,
    playlistId: Long? = null,
    trackNum: Int? = null
): Song {
    return Song(
        track.id,
        track.mediaId,
        album,
        track.title,
        track.titleSort,
        artist,
        track.composer,
        track.composerSort,
        album.artworkUriString,
        track.duration,
        trackNum ?: track.trackNum,
        track.discNum,
        genreId,
        playlistId,
        track.sourcePath,
        BoolConverter().toBoolean(track.ignored)
    )
}

suspend fun fetchPlaylists(context: Context): List<Playlist> = context.contentResolver.query(
    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
    arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME),
    null,
    null,
    MediaStore.Audio.Playlists.DATE_MODIFIED
)?.use {
    val db = DB.getInstance(context)
    val list: ArrayList<Playlist> = ArrayList()
    while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists._ID))
        val tracks = getTrackMediaIdByPlaylistId(context, id).mapNotNull {
            db.trackDao().getByMediaId(it.first)
        }
        val totalDuration = tracks.map { it.track.duration }.sum()
        val name = it.getString(it.getColumnIndex(MediaStore.Audio.Playlists.NAME)).let {
            if (it.isBlank()) UNKNOWN else it
        }
        val count = context.contentResolver.query(
            MediaStore.Audio.Playlists.Members.getContentUri("external", id),
            null,
            null,
            null,
            null
        )?.use { it.count } ?: 0
        val playlist =
            Playlist(id, tracks.getPlaylistThumb(context), name, count, totalDuration)
        list.add(playlist)
    }

    return@use list.toList().sortedBy { it.name }
} ?: emptyList()

private suspend fun List<JoinedTrack>.getPlaylistThumb(context: Context): Bitmap? =
    this@getPlaylistThumb.takeOrFillNull(10).map { joinedTrack ->
        joinedTrack?.album?.artworkUriString
    }.getThumb(context)

suspend fun DB.searchArtistByFuzzyTitle(title: String): List<Artist> =
    this@searchArtistByFuzzyTitle.artistDao().findAllByTitle("%${title.escapeSql}%")

suspend fun DB.searchAlbumByFuzzyTitle(title: String): List<JoinedAlbum> =
    this@searchAlbumByFuzzyTitle.albumDao().getAllByTitle("%${title.escapeSql}%")

suspend fun DB.searchTrackByFuzzyTitle(title: String): List<JoinedTrack> =
    this@searchTrackByFuzzyTitle.trackDao().getAllByTitle("%${title.escapeSql}%")

fun Context.searchPlaylistByFuzzyTitle(title: String): List<Playlist> = contentResolver.query(
    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
    arrayOf(
        MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME
    ),
    "${MediaStore.Audio.Playlists.NAME} like '%${title.escapeSql}%'",
    null,
    MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER
).use {
    it ?: return@use emptyList()
    val result: MutableList<Playlist> = mutableListOf()
    while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists._ID))
        val name = it.getString(it.getColumnIndex(MediaStore.Audio.Playlists.NAME)) ?: UNKNOWN
        result.add(Playlist(id, null, name, 0, 0))
    }

    return@use result
}

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

fun Playlist.getTrackMediaIds(context: Context): List<Pair<Long, Int>> =
    getTrackMediaIdByPlaylistId(context, this.id)

fun getTrackMediaIdByPlaylistId(context: Context, playlistId: Long): List<Pair<Long, Int>> =
    context.contentResolver.query(
        MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId), arrayOf(
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.PLAY_ORDER
        ), null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER
    )?.use {
        val trackMediaIdList: ArrayList<Pair<Long, Int>> = ArrayList()
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID))
            val order = it.getInt(it.getColumnIndex(MediaStore.Audio.Playlists.Members.PLAY_ORDER))
            trackMediaIdList.add(id to order)
        }

        return@use trackMediaIdList
    } ?: emptyList()

fun Song.getMediaSource(mediaSourceFactory: ProgressiveMediaSource.Factory): MediaSource =
    mediaSourceFactory.createMediaSource(
        if (mediaId < 0) Uri.parse(sourcePath)
        else Uri.fromFile(File(sourcePath))
    )

fun List<Song>.sortedByTrackOrder(): List<Song> =
    this.groupBy { it.album }
        .map {
            it.key to it.value.groupBy { it.discNum }
                .map { it.key to it.value.sortedBy { it.trackNum } }
                .sortedBy { it.first }
                .flatMap { it.second }
        }
        .groupBy { it.first.artistId }
        .flatMap { it.value.flatMap { it.second } }

fun List<Song>.shuffleByClassType(classType: OrientedClassType): List<Song> = when (classType) {
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
    OrientedClassType.SONG -> {
        this.shuffled()
    }
    OrientedClassType.GENRE -> {
        val genreIds = this.map { it.genreId }.distinct().shuffled()
        genreIds.map { id ->
            this.filter { it.genreId == id }
        }.flatten()
    }
    OrientedClassType.PLAYLIST -> {
        val playlistIds = this.map { it.playlistId }.distinct().shuffled()
        playlistIds.map { id ->
            this.filter { it.playlistId == id }
        }.flatten()
    }
    else -> emptyList()
}

suspend fun Song.getMediaMetadata(context: Context): MediaMetadataCompat {
    val uriString = album.artworkUriString

    return MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId.toString())
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist.title)
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album.title)
        .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, composer)
        .apply {
            uriString?.let {
                val bitmap = withContext(Dispatchers.IO) {
                    catchAsNull {
                        Glide.with(context)
                            .asDrawable()
                            .load(it)
                            .applyDefaultSettings()
                            .submit()
                            .get()
                            .bitmap()
                    }
                }
                putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            }
        }
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        .build()
}

suspend fun getPlayerNotification(
    context: Context,
    sessionToken: MediaSessionCompat.Token,
    song: Song, playing: Boolean
): Notification {
    val artwork = try {
        withContext(Dispatchers.IO) {
            Glide.with(context)
                .asDrawable()
                .load(song.album.artworkUriString.orDefaultForModel)
                .applyDefaultSettings()
                .submit()
                .get()
                .bitmap()
        }
    } catch (t: Throwable) {
        Timber.e(t)
        null
    }

    return context.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_PLAYER)
        .setSmallIcon(R.drawable.ic_notification_player)
        .setLargeIcon(artwork)
        .setContentTitle(song.title)
        .setContentText(song.artist.title)
        .setSubText(song.album.title)
        .setOngoing(playing)
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(sessionToken)
        )
        .setShowWhen(false)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                App.REQUEST_CODE_LAUNCH_APP,
                LauncherActivity.createIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_backward,
                context.getString(R.string.notification_action_prev),
                getCommandPendingIntent(context, PlayerControlCommand.PREV)
            ).build()
        )
        .addAction(
            if (playing) {
                NotificationCompat.Action.Builder(
                    R.drawable.ic_pause,
                    context.getString(R.string.notification_action_pause),
                    getCommandPendingIntent(context, PlayerControlCommand.PLAY_PAUSE)
                ).build()
            } else {
                NotificationCompat.Action.Builder(
                    R.drawable.ic_play,
                    context.getString(R.string.notification_action_play),
                    getCommandPendingIntent(context, PlayerControlCommand.PLAY_PAUSE)
                ).build()
            }
        )
        .addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_forward,
                context.getString(R.string.notification_action_next),
                getCommandPendingIntent(context, PlayerControlCommand.NEXT)
            ).build()
        )
        .build()
}

fun getCommandIntent(context: Context, command: PlayerControlCommand): Intent =
    PlayerService.createIntent(context).apply {
        action = command.name
        putExtra(PlayerService.ARGS_KEY_CONTROL_COMMAND, command.ordinal)
    }

private fun getCommandPendingIntent(
    context: Context,
    command: PlayerControlCommand
): PendingIntent =
    PendingIntent.getService(
        context,
        343,
        getCommandIntent(context, command),
        PendingIntent.FLAG_CANCEL_CURRENT
    )

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

private fun Drawable.bitmap(minimumSideLength: Int = 1000, supportAlpha: Boolean = false): Bitmap {
    val min = min(intrinsicWidth, intrinsicHeight)
    val scale = if (min < minimumSideLength) minimumSideLength.toFloat() / min else 1f
    return Bitmap.createBitmap(
        (intrinsicWidth * scale).toInt(), (intrinsicHeight * scale).toInt(), Bitmap.Config.ARGB_8888
    ).apply {
        bounds = Rect(0, 0, width, height)
        draw(Canvas(this).apply {
            if (supportAlpha.not()) drawColor(Color.WHITE)
        })
    }
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

suspend fun updateMetadata(
    context: Context,
    db: DB,
    joinedTrack: JoinedTrack,
    newTrackName: String? = null,
    newAlbumName: String? = null,
    newArtistName: String? = null,
    newComposerName: String? = null,
    newArtwork: Bitmap? = null
) = withContext(Dispatchers.IO) {
    val client = DbxClientV2(
        DbxRequestConfig.newBuilder("qp/${BuildConfig.VERSION_NAME}").build(),
        PreferenceManager.getDefaultSharedPreferences(context).dropboxToken
    )
    val file =
        if (joinedTrack.track.sourcePath.startsWith("http")) null
        else File(joinedTrack.track.sourcePath)
    val audioFile = file?.let { AudioFileIO.read(it) }
    when {
        newArtistName != null -> {
            val artworkUriString = newArtwork?.toByteArray()?.storeArtwork(context)
            val artwork = artworkUriString?.let { ArtworkFactory.createArtworkFromFile(File(it)) }
            db.trackDao().getAllByArtist(joinedTrack.artist.id)
                .map {
                    if (it.track.sourcePath.startsWith("http")) null
                    else AudioFileIO.read(File(it.track.sourcePath))
                }
                .forEach { existingAudioFile ->
                    existingAudioFile?.tag?.apply {
                        setField(FieldKey.ARTIST, newArtistName)
                        newAlbumName?.let { setField(FieldKey.ALBUM, it) }
                        artwork?.let { setField(it) }
                        newTrackName?.let { setField(FieldKey.TITLE, it) }
                    }

                    existingAudioFile?.let { AudioFileIO.write(it) }
                }
            db.artistDao().update(joinedTrack.artist.copy(title = newArtistName))
        }
        newAlbumName != null || newArtwork != null -> {
            val artworkUriString = newArtwork?.toByteArray()?.storeArtwork(context)
            val artwork = artworkUriString?.let { ArtworkFactory.createArtworkFromFile(File(it)) }
            db.trackDao().getAllByAlbum(joinedTrack.album.id)
                .map {
                    if (it.track.sourcePath.startsWith("http")) null
                    else AudioFileIO.read(File(it.track.sourcePath))
                }
                .forEach { existingAudioFile ->
                    existingAudioFile?.tag?.apply {
                        newAlbumName?.let { setField(FieldKey.ALBUM, it) }
                        artwork?.let { setField(it) }
                        newTrackName?.let { setField(FieldKey.TITLE, it) }
                    }

                    existingAudioFile?.let { AudioFileIO.write(it) }
                }
            db.albumDao().update(
                joinedTrack.album.copy(
                    title = newAlbumName ?: joinedTrack.album.title,
                    artworkUriString = artworkUriString
                        ?: joinedTrack.album.artworkUriString
                )
            )
        }
        else -> {
            audioFile?.tag?.apply {
                newTrackName?.let { setField(FieldKey.TITLE, it) }
                newComposerName?.let { setField(FieldKey.COMPOSER, it) }
            }

            db.trackDao().update(
                joinedTrack.track.copy(
                    title = newTrackName ?: joinedTrack.track.title,
                    composer = newComposerName ?: joinedTrack.track.composer
                )
            )
        }
    }
    audioFile?.let { AudioFileIO.write(it) }
}

private fun Bitmap.toByteArray(): ByteArray =
    ByteArrayOutputStream().apply { compress(Bitmap.CompressFormat.PNG, 100, this) }
        .toByteArray()

private val String.escapeSql: String get() = replace("'", "''")