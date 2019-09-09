package com.geckour.q.util

import android.app.Notification
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.dao.upsert
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Bool
import com.geckour.q.data.db.model.Track
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.LauncherActivity
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.min


const val UNKNOWN: String = "UNKNOWN"

enum class InsertActionType {
    NEXT, LAST, OVERRIDE, SHUFFLE_NEXT, SHUFFLE_LAST, SHUFFLE_OVERRIDE, SHUFFLE_SIMPLE_NEXT, SHUFFLE_SIMPLE_LAST, SHUFFLE_SIMPLE_OVERRIDE
}

enum class OrientedClassType {
    ARTIST, ALBUM, SONG, GENRE, PLAYLIST
}

enum class NotificationCommand {
    PLAY_PAUSE, NEXT, PREV, DESTROY
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

suspend fun getSongListFromTrackList(db: DB, dbTrackList: List<Track>): List<Song> =
    dbTrackList.mapNotNull { getSong(db, it) }

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
): Song? = withContext(Dispatchers.IO) {
    db.trackDao().getByMediaId(trackMediaId)?.let {
        getSong(db, it, genreId, playlistId, trackNum = trackNum)
    }
}

suspend fun getSong(
    db: DB, track: Track, genreId: Long? = null, playlistId: Long? = null, trackNum: Int? = null
): Song? = withContext(Dispatchers.IO) {
    val artistName = db.artistDao().get(track.artistId)?.title ?: UNKNOWN
    val albumName = db.albumDao().get(track.albumId)?.title ?: UNKNOWN
    val artwork = db.albumDao().get(track.albumId)?.artworkUriString
    Song(
        track.id,
        track.mediaId,
        track.albumId,
        track.title,
        artistName,
        albumName,
        track.composer,
        artwork,
        track.duration,
        trackNum ?: track.trackNum,
        track.discNum,
        genreId,
        playlistId,
        track.sourcePath,
        BoolConverter().toBoolean(track.ignored)
    )
}

suspend fun fetchPlaylists(context: Context): List<Playlist> = withContext(Dispatchers.IO) {
    context.contentResolver.query(
        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, arrayOf(
            MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME
        ), null, null, MediaStore.Audio.Playlists.DATE_MODIFIED
    )?.use {
        val db = DB.getInstance(context)
        val list: ArrayList<Playlist> = ArrayList()
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists._ID))
            val tracks = getTrackMediaIdByPlaylistId(context, id).mapNotNull {
                db.trackDao().getByMediaId(it.first)
            }
            val totalDuration = tracks.map { it.duration }.sum()
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
}

private suspend fun List<Track>.getPlaylistThumb(context: Context): Bitmap? =
    withContext(Dispatchers.IO) {
        val db = DB.getInstance(context)
        this@getPlaylistThumb.takeOrFillNull(10).map {
            it?.let {
                db.getArtworkUriStringFromId(it.albumId)?.let { Uri.parse(it) }
            }
        }.getThumb(context)
    }

suspend fun DB.searchArtistByFuzzyTitle(title: String): List<Artist> = withContext(Dispatchers.IO) {
    this@searchArtistByFuzzyTitle.artistDao().findLikeTitle("%$title%")
}

suspend fun DB.searchAlbumByFuzzyTitle(title: String): List<Album> = withContext(Dispatchers.IO) {
    this@searchAlbumByFuzzyTitle.albumDao().findByTitle("%$title%")
}

suspend fun DB.searchTrackByFuzzyTitle(title: String): List<Track> = withContext(Dispatchers.IO) {
    this@searchTrackByFuzzyTitle.trackDao().findByTitle("%$title%", Bool.UNDEFINED)
}

fun Context.searchPlaylistByFuzzyTitle(title: String): List<Playlist> = contentResolver.query(
    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
    arrayOf(
        MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME
    ),
    "${MediaStore.Audio.Playlists.NAME} like '%$title%'",
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
    "${MediaStore.Audio.Genres.NAME} like '%$title%'",
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

fun List<Uri?>.getThumb(context: Context): Bitmap? {
    if (this@getThumb.isEmpty()) return null
    val unit = 100
    val bitmap = Bitmap.createBitmap(
        ((this@getThumb.size * 0.9 - 0.1) * unit).toInt(), unit, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    this@getThumb.reversed().forEachIndexed { i, uri ->
        val b =
            Glide.with(context)
                .asBitmap()
                .load(uri ?: return@forEachIndexed)
                .applyDefaultSettings()
                .submit()
                .get()?.let {
                    Bitmap.createScaledBitmap(
                        it,
                        unit,
                        (it.height * unit.toFloat() / it.width).toInt(),
                        false
                    )
                } ?: return@forEachIndexed
        canvas.drawBitmap(b, bitmap.width - (i + 1) * unit * 0.9f, (unit - b.height) / 2f, Paint())
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

fun Song.getMediaSource(mediaSourceFactory: AdsMediaSource.MediaSourceFactory): MediaSource =
    mediaSourceFactory.createMediaSource(Uri.fromFile(File(sourcePath)))

fun List<Song>.sortedByTrackOrder(): List<Song> =
    this.asSequence().groupBy { it.discNum }.map { it.key to it.value.sortedBy { it.trackNum } }.sortedBy { it.first }.toList().flatMap { it.second }

fun List<Song>.shuffleByClassType(classType: OrientedClassType): List<Song> = when (classType) {
    OrientedClassType.ARTIST -> {
        val artists = this.map { it.artist }.distinct().shuffled()
        artists.map { artist ->
            this.filter { it.artist == artist }
        }.flatten()
    }
    OrientedClassType.ALBUM -> {
        val albumIds = this.map { it.albumId }.distinct().shuffled()
        albumIds.map { id ->
            this.filter { it.albumId == id }
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
}

suspend fun Song.getMediaMetadata(context: Context): MediaMetadataCompat =
    withContext(Dispatchers.IO) {
        val db = DB.getInstance(context)
        val uriString = db.getArtworkUriStringFromId(albumId)

        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, uriString)
            .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, composer)
            .apply {
                if (uriString != null
                    && PreferenceManager.getDefaultSharedPreferences(context)
                        .showArtworkOnLockScreen
                ) {
                    val bitmap = try {
                        Glide.with(context)
                            .asDrawable()
                            .load(uriString)
                            .applyDefaultSettings()
                            .submit()
                            .get()
                            .bitmap()
                    } catch (t: Throwable) {
                        Timber.e(t)
                        null
                    }
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                }
            }
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build()
    }

suspend fun getPlayerNotification(
    context: Context,
    sessionToken: MediaSessionCompat.Token?,
    song: Song,
    playing: Boolean
): Notification? =
    withContext(Dispatchers.IO) {
        if (sessionToken == null) return@withContext null

        val artwork = try {
            Glide.with(context)
                .asDrawable()
                .load(
                    DB.getInstance(context)
                        .getArtworkUriStringFromId(song.albumId)
                        .orDefaultForModel
                )
                .applyDefaultSettings()
                .submit()
                .get()
                .bitmap()
        } catch (t: Throwable) {
            Timber.e(t)
            null
        }
        context.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_PLAYER)
            .setSmallIcon(R.drawable.ic_notification_player)
            .setLargeIcon(artwork)
            .setContentTitle(song.name)
            .setContentText(song.artist)
            .setSubText(song.album)
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
                    getCommandPendingIntent(context, NotificationCommand.PREV)
                ).build()
            )
            .addAction(
                if (playing) {
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_pause,
                        context.getString(R.string.notification_action_pause),
                        getCommandPendingIntent(context, NotificationCommand.PLAY_PAUSE)
                    ).build()
                } else {
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_play,
                        context.getString(R.string.notification_action_play),
                        getCommandPendingIntent(context, NotificationCommand.PLAY_PAUSE)
                    ).build()
                }
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_forward,
                    context.getString(R.string.notification_action_next),
                    getCommandPendingIntent(context, NotificationCommand.NEXT)
                ).build()
            )
            .setDeleteIntent(getCommandPendingIntent(context, NotificationCommand.DESTROY))
            .build()
    }

private fun getCommandPendingIntent(context: Context, command: NotificationCommand): PendingIntent =
    PendingIntent.getService(context, 343, PlayerService.createIntent(context).apply {
        action = command.name
        putExtra(PlayerService.ARGS_KEY_CONTROL_COMMAND, command.ordinal)
    }, PendingIntent.FLAG_CANCEL_CURRENT)

fun Long.getTimeString(): String {
    val hour = this / 3600000
    val minute = (this % 3600000) / 60000
    val second = (this % 60000) / 1000
    return (if (hour > 0) String.format("%d:", hour) else "") + String.format(
        "%02d:%02d", minute, second
    )
}

fun MediaMetadataRetriever.storeMediaInfo(
    context: Context,
    db: DB,
    trackPath: String,
    trackMediaId: Long,
    albumMediaId: Long,
    artistMediaId: Long
): Boolean {
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackMediaId)

    if (File(trackPath).exists().not()) {
        context.contentResolver.delete(uri, null, null)
        return false
    }

    try {
        setDataSource(context, uri)

        val title = extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        val trackNum = extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            ?.split("/")?.first()?.toInt()
        val discNum = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            ?.split("/")?.first()?.toInt()
        val albumTitle = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val artistTitle = extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val composerTitle = extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
        val artworkUriString = albumMediaId.getArtworkUriIfExist(context)?.toString()
        val albumArtistTitle =
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)

        val artistId = Artist(0, artistMediaId, artistTitle, 0).upsert(db) ?: return false
        val albumArtistId =
            if (albumArtistTitle == null) null
            else Artist(0, null, albumArtistTitle, 0).upsert(db)

        val albumId = db.albumDao().getByMediaId(albumMediaId).let {
            if (it == null || it.hasAlbumArtist.not()) {
                val album = Album(
                    0,
                    albumMediaId,
                    albumTitle,
                    albumArtistId ?: artistId,
                    artworkUriString,
                    albumArtistId != null,
                    0
                )
                album.upsert(db)
            } else it.id
        }

        val track = Track(
            0,
            trackMediaId,
            title,
            albumId,
            artistId,
            albumArtistId,
            composerTitle,
            duration,
            trackNum,
            discNum,
            trackPath,
            0
        )
        track.upsert(db)
    } catch (t: Throwable) {
        Timber.e(t)
    }

    return true
}

private fun Long.getArtworkUriIfExist(context: Context): Uri? =
    this.getArtworkUriFromMediaId().let { uri ->
        try {
            context.contentResolver.openInputStream(uri)?.close()
            uri
        } catch(t: Throwable) {
            Timber.e(t, "Could not open the uri: $uri")
            null
        }
    }

val String?.orDefaultForModel get() = this ?: R.drawable.ic_empty
val Bitmap?.orDefaultForModel get() = this ?: R.drawable.ic_empty

inline fun <reified T> RequestBuilder<T>.applyDefaultSettings() =
    this.diskCacheStrategy(DiskCacheStrategy.NONE)

private fun Drawable.bitmap(minimumSideLength: Int = 1000, supportAlpha: Boolean = false): Bitmap {
    val min = min(intrinsicWidth, intrinsicHeight)
    val scale = if (min < minimumSideLength) minimumSideLength.toFloat() / min else 1f
    return Bitmap.createBitmap(
        (intrinsicWidth * scale).toInt(),
        (intrinsicHeight * scale).toInt(),
        Bitmap.Config.ARGB_8888
    ).apply {
        bounds = Rect(0, 0, width, height)
        draw(Canvas(this).apply {
            if (supportAlpha.not()) drawColor(Color.WHITE)
        })
    }
}