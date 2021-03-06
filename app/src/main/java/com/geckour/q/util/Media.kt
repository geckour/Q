package com.geckour.q.util

import android.app.Notification
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.dao.upsert
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Track
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.service.PlayerService.Companion.NOTIFICATION_CHANNEL_ID_PLAYER
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.ui.main.MainActivity
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import timber.log.Timber
import java.io.File


const val UNKNOWN: String = "UNKNOWN"

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

enum class OrientedClassType {
    ARTIST,
    ALBUM,
    SONG,
    GENRE,
    PLAYLIST
}

enum class NotificationCommand {
    DESTROY
}

enum class SettingCommand {
    SET_EQUALIZER,
    UNSET_EQUALIZER,
    REFLECT_EQUALIZER_SETTING
}

data class QueueMetadata(
        val actionType: InsertActionType,
        val classType: OrientedClassType
)

data class InsertQueue(
        val metadata: QueueMetadata,
        val queue: List<Song>
)

suspend fun getSongListFromTrackList(db: DB, dbTrackList: List<Track>): List<Song> =
        dbTrackList.mapNotNull { getSong(db, it).await() }

suspend fun getSongListFromTrackMediaId(db: DB,
                                        dbTrackIdList: List<Long>,
                                        genreId: Long? = null,
                                        playlistId: Long? = null): List<Song> =
        dbTrackIdList.mapNotNull {
            getSong(db, it, genreId, playlistId).await()
        }

suspend fun getSongListFromTrackMediaIdWithTrackNum(db: DB,
                                                    dbTrackMediaIdWithTrackNumList: List<Pair<Long, Int>>,
                                                    genreId: Long? = null,
                                                    playlistId: Long? = null): List<Song> =
        dbTrackMediaIdWithTrackNumList.mapNotNull {
            getSong(db, it.first, genreId, playlistId, trackNum = it.second).await()
        }

fun getSong(db: DB, trackMediaId: Long,
            genreId: Long? = null, playlistId: Long? = null,
            trackNum: Int? = null): Deferred<Song?> =
        GlobalScope.async {
            db.trackDao().getByMediaId(trackMediaId)?.let {
                getSong(db, it, genreId, playlistId, trackNum = trackNum).await()
            }
        }

fun getSong(db: DB, track: Track,
            genreId: Long? = null, playlistId: Long? = null,
            trackNum: Int? = null): Deferred<Song?> =
        GlobalScope.async {
            val artistName = db.artistDao().get(track.artistId)?.title ?: UNKNOWN
            val artwork = db.albumDao().get(track.albumId)?.artworkUriString
            Song(track.id, track.mediaId, track.albumId, track.title,
                    artistName, artwork, track.duration, trackNum ?: track.trackNum, track.discNum,
                    genreId, playlistId, track.sourcePath)
        }

fun fetchPlaylists(context: Context): Deferred<List<Playlist>> = GlobalScope.async {
    context.contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            arrayOf(
                    MediaStore.Audio.Playlists._ID,
                    MediaStore.Audio.Playlists.NAME),
            null,
            null,
            MediaStore.Audio.Playlists.DATE_MODIFIED)?.use {
        val db = DB.getInstance(context)
        val list: ArrayList<Playlist> = ArrayList()
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists._ID))
            val tracks = getTrackMediaIdByPlaylistId(context, id)
                    .mapNotNull { db.trackDao().getByMediaId(it.first) }
            val totalDuration = tracks.map { it.duration }.sum()
            val name = it.getString(it.getColumnIndex(MediaStore.Audio.Playlists.NAME)).let {
                if (it.isBlank()) UNKNOWN else it
            }
            val count = context.contentResolver
                    .query(MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                            null, null, null, null)
                    ?.use { it.count } ?: 0
            val playlist = Playlist(id, tracks.getPlaylistThumb(context).await(), name, count, totalDuration)
            list.add(playlist)
        }

        return@use list.toList().sortedBy { it.name }
    } ?: emptyList()
}

private fun List<Track>.getPlaylistThumb(context: Context): Deferred<Bitmap?> = GlobalScope.async {
    val db = DB.getInstance(context)
    this@getPlaylistThumb.takeOrFillNull(10)
            .map {
                it?.let { db.getArtworkUriStringFromId(it.albumId).await()?.let { Uri.parse(it) } }
            }
            .getThumb(context)
            .await()
}

fun DB.searchArtistByFuzzyTitle(title: String): Deferred<List<Artist>> =
        GlobalScope.async { this@searchArtistByFuzzyTitle.artistDao().findByTitle("%$title%") }

fun DB.searchAlbumByFuzzyTitle(title: String): Deferred<List<Album>> =
        GlobalScope.async { this@searchAlbumByFuzzyTitle.albumDao().findByTitle("%$title%") }

fun DB.searchTrackByFuzzyTitle(title: String): Deferred<List<Track>> =
        GlobalScope.async { this@searchTrackByFuzzyTitle.trackDao().findByTitle("%$title%") }

fun Context.searchPlaylistByFuzzyTitle(title: String): List<Playlist> =
        contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID,
                        MediaStore.Audio.Playlists.NAME),
                "${MediaStore.Audio.Playlists.NAME} like '%$title%'",
                null,
                MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER).use {
            it ?: return@use emptyList()
            val result: MutableList<Playlist> = mutableListOf()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists._ID))
                val name = it.getString(it.getColumnIndex(MediaStore.Audio.Playlists.NAME))
                        ?: UNKNOWN
                result.add(Playlist(id, null, name, 0, 0))
            }

            return@use result
        }

fun Context.searchGenreByFuzzyTitle(title: String): List<Genre> =
        contentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID,
                        MediaStore.Audio.Genres.NAME),
                "${MediaStore.Audio.Genres.NAME} like '%$title%'",
                null,
                MediaStore.Audio.Genres.DEFAULT_SORT_ORDER).use {
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

fun List<Uri?>.getThumb(context: Context): Deferred<Bitmap?> = GlobalScope.async {
    if (this@getThumb.isEmpty()) return@async null
    val unit = 100
    val bitmap = Bitmap.createBitmap(((this@getThumb.size * 0.9 - 0.1) * unit).toInt(), unit, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    this@getThumb.reversed().forEachIndexed { i, uri ->
        val b = Glide.with(context).asBitmap()
                .load(uri ?: return@forEachIndexed)
                .submit().get()?.let {
                    Bitmap.createScaledBitmap(
                            it, unit, (it.height * unit.toFloat() / it.width).toInt(), false)
                } ?: return@forEachIndexed
        canvas.drawBitmap(b, bitmap.width - (i + 1) * unit * 0.9f, (unit - b.height) / 2f, Paint())
    }
    bitmap
}

fun Genre.getTrackMediaIds(context: Context): List<Long> =
        getTrackMediaIdsByGenreId(context, this.id)

fun getTrackMediaIdsByGenreId(context: Context, genreId: Long): List<Long> =
        context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                arrayOf(MediaStore.Audio.Genres.Members._ID),
                null, null, null)?.use {
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
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID, MediaStore.Audio.Playlists.Members.PLAY_ORDER),
                null,
                null,
                MediaStore.Audio.Playlists.Members.PLAY_ORDER)?.use {
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

fun List<Song>.sortedByTrackOrder(): List<Song> = this.asSequence()
        .groupBy { it.discNum }
        .map { it.key to it.value.sortedBy { it.trackNum } }
        .sortedBy { it.first }.toList()
        .flatMap { it.second }

fun List<Song>.shuffleByClassType(classType: OrientedClassType): List<Song> =
        when (classType) {
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

fun Song.getMediaMetadata(context: Context, albumTitle: String? = null): Deferred<MediaMetadataCompat> =
        GlobalScope.async {
            val db = DB.getInstance(context)
            val album = albumTitle
                    ?: db.albumDao().get(this@getMediaMetadata.albumId)?.title
                    ?: UNKNOWN
            val uriString = db.getArtworkUriStringFromId(this@getMediaMetadata.albumId).await()

            MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                            this@getMediaMetadata.mediaId.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, this@getMediaMetadata.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, this@getMediaMetadata.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, uriString)
                    .apply {
                        if (uriString != null
                                && PreferenceManager.getDefaultSharedPreferences(context)
                                        .showArtworkOnLockScreen) {
                            val bitmap = try {
                                Glide.with(context).asBitmap()
                                        .load(uriString)
                                        .submit().get()
                            } catch (t: Throwable) {
                                Timber.e(t)
                                null
                            }
                            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        }
                    }
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, this@getMediaMetadata.duration)
                    .build()
        }

fun getNotification(context: Context, sessionToken: MediaSessionCompat.Token?,
                    song: Song, albumTitle: String,
                    playing: Boolean): Deferred<Notification?> =
        GlobalScope.async {
            if (sessionToken == null) return@async null

            val artwork = try {
                Glide.with(context)
                        .asBitmap()
                        .load(DB.getInstance(context)
                                .getArtworkUriStringFromId(song.albumId).await()
                                ?: R.drawable.ic_empty)
                        .submit()
                        .get()
            } catch (t: Throwable) {
                Timber.e(t)
                null
            }
            val builder =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_PLAYER)
                    else NotificationCompat.Builder(context)
            builder.setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(artwork)
                    .setContentTitle(song.name)
                    .setContentText(song.artist)
                    .setSubText(albumTitle)
                    .setOngoing(playing)
                    .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                            .setShowActionsInCompactView(0, 1, 2)
                            .setMediaSession(sessionToken))
                    .setContentIntent(PendingIntent.getActivity(context,
                            App.REQUEST_CODE_LAUNCH_APP,
                            LauncherActivity.createIntent(context),
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .addAction(NotificationCompat.Action.Builder(
                            R.drawable.ic_backward,
                            context.getString(R.string.notification_action_prev),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                    ).build())
                    .addAction(if (playing) {
                        NotificationCompat.Action.Builder(
                                R.drawable.ic_pause,
                                context.getString(R.string.notification_action_pause),
                                MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                        ).build()
                    } else {
                        NotificationCompat.Action.Builder(
                                R.drawable.ic_play,
                                context.getString(R.string.notification_action_play),
                                MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                        ).build()
                    })
                    .addAction(NotificationCompat.Action.Builder(
                            R.drawable.ic_forward,
                            context.getString(R.string.notification_action_next),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
                    ).build())
                    .setDeleteIntent(getCommandPendingIntent(context, NotificationCommand.DESTROY))
                    .build()
        }

private fun getCommandPendingIntent(context: Context, command: NotificationCommand): PendingIntent =
        PendingIntent.getService(context, 0,
                PlayerService.createIntent(context).apply {
                    action = command.name
                    putExtra(PlayerService.ARGS_KEY_CONTROL_COMMAND, command.ordinal)
                },
                PendingIntent.FLAG_CANCEL_CURRENT)

fun Long.getTimeString(): String {
    val hour = this / 3600000
    val minute = (this % 3600000) / 60000
    val second = (this % 60000) / 1000
    return (if (hour > 0) String.format("%d:", hour) else "") + String.format("%02d:%02d", minute, second)
}

fun pushMedia(context: Context, db: DB, cursor: Cursor) {
    val trackMediaId = cursor.getLong(
            cursor.getColumnIndex(MediaStore.Audio.Media._ID))

    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackMediaId)

    val trackPath = cursor.getString(
            cursor.getColumnIndex(MediaStore.Audio.Media.DATA))

    if (File(trackPath).exists().not()) {
        context.contentResolver.delete(uri, null, null)
        return
    }

    MediaMetadataRetriever().also { retriever ->
        try {
            retriever.setDataSource(context, uri)

            val current = cursor.position
            val total = cursor.count

            val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
            val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
            val trackNum = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK))
            val albumMediaId = cursor.getLong(
                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
            val albumTitle = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
            val artistMediaId = cursor.getLong(
                    cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID))
            val artistTitle = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
            val artworkUriString = albumMediaId.getArtworkUriIfExist(context)?.toString()

            val discNum = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.split("/")
                    ?.first()?.toInt()
            val albumArtistTitle = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)

            val artistId = Artist(0, artistMediaId, artistTitle, 0).upsert(db) ?: return@also
            val albumArtistId =
                    if (albumArtistTitle == null) null
                    else Artist(0, null, albumArtistTitle, 0).upsert(db)

            val albumId = db.albumDao().getByMediaId(albumMediaId).let {
                if (it == null || it.hasAlbumArtist.not()) {
                    val album = Album(0, albumMediaId, albumTitle,
                            albumArtistId ?: artistId,
                            artworkUriString, albumArtistId != null, 0)
                    album.upsert(db)
                } else it.id
            }

            val track = Track(0, trackMediaId, title, albumId, artistId, albumArtistId, duration,
                    trackNum, discNum, trackPath, 0)
            track.upsert(db)
            context.sendBroadcast(MainActivity.createProgressIntent(current to total))
        } catch (t: Throwable) {
            Timber.e(t)
        }
    }
}

private fun Long.getArtworkUriIfExist(context: Context): Uri? =
        this.getArtworkUriFromMediaId().let { uri ->
            context.contentResolver.query(uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null, null, null)?.use {
                if (it.moveToFirst()
                        && File(it.getString(it.getColumnIndex(MediaStore.MediaColumns.DATA))).exists())
                    uri
                else null
            }
        }