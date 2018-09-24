package com.geckour.q.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.bumptech.glide.Glide
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.service.PlayerService.Companion.NOTIFICATION_CHANNEL_ID_PLAYER
import com.geckour.q.ui.MainActivity
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import timber.log.Timber


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

enum class PlaybackButton {
    PLAY_OR_PAUSE,
    NEXT,
    PREV,
    FF,
    REWIND,
    UNDEFINED
}

enum class NotificationCommand {
    PLAY_OR_PAUSE,
    NEXT,
    PREV,
    DESTROY
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
        async {
            db.trackDao().getByMediaId(trackMediaId)?.let {
                getSong(db, it, genreId, playlistId, trackNum = trackNum).await()
            }
        }

fun getSong(db: DB, track: Track,
            genreId: Long? = null, playlistId: Long? = null,
            trackNum: Int? = null): Deferred<Song?> =
        async {
            val artistName = db.artistDao().get(track.artistId)?.title ?: UNKNOWN
            val artwork = db.albumDao().get(track.albumId)?.artworkUriString
            Song(track.id, track.mediaId, track.albumId, track.title,
                    artistName, artwork, track.duration,
                    trackNum ?: track.trackNum, track.trackTotal, track.discNum, track.discTotal,
                    genreId, playlistId, track.sourcePath)
        }

fun fetchPlaylists(context: Context): List<Playlist> =
        context.contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Playlists._ID,
                        MediaStore.Audio.Playlists.NAME),
                null,
                null,
                MediaStore.Audio.Playlists.DATE_MODIFIED)?.use {
            val list: ArrayList<Playlist> = ArrayList()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists._ID))
                val name = it.getString(it.getColumnIndex(MediaStore.Audio.Playlists.NAME)).let {
                    if (it.isBlank()) UNKNOWN else it
                }
                val count = context.contentResolver
                        .query(MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                                null, null, null, null)
                        ?.use { it.count } ?: 0
                val playlist = Playlist(id, null, name, count)
                list.add(playlist)
            }

            return@use list
        } ?: emptyList()

fun Genre.getTrackMediaIds(context: Context): List<Long> =
        context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", this.id),
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
        context.contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", this.id),
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
        mediaSourceFactory.createMediaSource(Uri.parse(sourcePath))

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

fun Song.getMediaMetadata(context: Context, albumTitle: String? = null): Deferred<MediaMetadata> =
        async {
            val db = DB.getInstance(context)
            val album = albumTitle
                    ?: db.albumDao().get(this@getMediaMetadata.albumId)?.title
                    ?: UNKNOWN

            MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                            this@getMediaMetadata.mediaId.toString())
                    .putString(MediaMetadata.METADATA_KEY_TITLE, this@getMediaMetadata.name)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, this@getMediaMetadata.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                            db.getArtworkUriStringFromId(this@getMediaMetadata.albumId).await().toString())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, this@getMediaMetadata.duration)
                    .build()
        }

fun getNotification(context: Context, sessionToken: MediaSession.Token,
                    song: Song, albumTitle: String,
                    playWhenReady: Boolean): Deferred<Notification> =
        async {
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
                        Notification.Builder(context, NOTIFICATION_CHANNEL_ID_PLAYER)
                    else Notification.Builder(context)
            builder.setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(artwork)
                    .setContentTitle(song.name)
                    .setContentText(song.artist)
                    .setSubText(albumTitle)
                    .setOngoing(playWhenReady)
                    .setStyle(Notification.MediaStyle()
                            .setShowActionsInCompactView(0, 1, 2)
                            .setMediaSession(sessionToken))
                    .setContentIntent(PendingIntent.getActivity(context,
                            App.REQUEST_CODE_OPEN_DEFAULT_ACTIVITY,
                            MainActivity.createIntent(context),
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .setActions(
                            Notification.Action.Builder(
                                    Icon.createWithResource(context,
                                            R.drawable.ic_backward),
                                    context.getString(R.string.notification_action_prev),
                                    getCommandPendingIntent(context, NotificationCommand.PREV)
                            ).build(),
                            if (playWhenReady) {
                                Notification.Action.Builder(
                                        Icon.createWithResource(context,
                                                R.drawable.ic_pause),
                                        context.getString(R.string.notification_action_pause),
                                        getCommandPendingIntent(context,
                                                NotificationCommand.PLAY_OR_PAUSE)
                                ).build()
                            } else {
                                Notification.Action.Builder(
                                        Icon.createWithResource(context,
                                                R.drawable.ic_play),
                                        context.getString(R.string.notification_action_play),
                                        getCommandPendingIntent(context,
                                                NotificationCommand.PLAY_OR_PAUSE)
                                ).build()
                            },
                            Notification.Action.Builder(
                                    Icon.createWithResource(context,
                                            R.drawable.ic_forward),
                                    context.getString(R.string.notification_action_next),
                                    getCommandPendingIntent(context, NotificationCommand.NEXT)
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