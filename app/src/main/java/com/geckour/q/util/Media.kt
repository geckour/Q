package com.geckour.q.util

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.geckour.q.ui.MainActivity
import com.geckour.q.ui.sheet.BottomSheetViewModel
import com.geckour.q.util.MediaRetrieveWorker.Companion.UNKNOWN
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import timber.log.Timber

const val NOTIFICATION_CHANNEL_ID_PLAYER = "notification_channel_id_player"
private const val NOTIFICATION_ID_PLAYER = 320

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

suspend fun getSongListFromTrackId(db: DB,
                                   dbTrackIdList: List<Long>,
                                   genreId: Long? = null,
                                   playlistId: Long? = null,
                                   setTrackNumByIndex: Boolean = false): List<Song> =
        dbTrackIdList.mapIndexedNotNull { i, trackId ->
            getSong(db, trackId, genreId, playlistId, trackNum = if (setTrackNumByIndex) i else null).await()
        }

fun getSong(db: DB, trackId: Long,
            genreId: Long? = null, playlistId: Long? = null, trackNum: Int? = null): Deferred<Song?> =
        async {
            db.trackDao().get(trackId)?.let {
                getSong(db, it, genreId, playlistId, trackNum = trackNum).await()
            }
        }

fun getSong(db: DB, track: Track, genreId: Long? = null, playlistId: Long? = null, trackNum: Int? = null): Deferred<Song?> =
        async {
            val artist = db.artistDao().get(track.artistId) ?: return@async null
            Song(track.id, track.albumId, track.title, artist.title, track.duration,
                    trackNum ?: track.trackNum, track.trackTotal, track.discNum, track.discTotal,
                    genreId, playlistId, track.sourcePath)
        }

fun Genre.getTrackIds(context: Context): List<Long> =
        context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", this.id),
                arrayOf(
                        MediaStore.Audio.Genres.Members._ID),
                null,
                null,
                null)?.use {
            val trackIdList: ArrayList<Long> = ArrayList()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres.Members._ID))
                trackIdList.add(id)
            }

            return@use trackIdList
        } ?: emptyList()

fun Playlist.getTrackIds(context: Context): List<Long> =
        context.contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", this.id.apply { Timber.d("qgeck playlist id: $this") }),
                arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                null,
                null,
                MediaStore.Audio.Playlists.Members.PLAY_ORDER)?.use {
            val trackIdList: ArrayList<Long> = ArrayList()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID))
                trackIdList.add(id)
            }

            return@use trackIdList
        } ?: emptyList()

fun Song.getMediaSource(mediaSourceFactory: AdsMediaSource.MediaSourceFactory): MediaSource =
        mediaSourceFactory.createMediaSource(Uri.parse(sourcePath))

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
            val album = albumTitle
                    ?: DB.getInstance(context)
                            .albumDao()
                            .get(this@getMediaMetadata.albumId)
                            .title

            MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                            this@getMediaMetadata.id.toString())
                    .putString(MediaMetadata.METADATA_KEY_TITLE, this@getMediaMetadata.name)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, this@getMediaMetadata.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                            getArtworkUriFromAlbumId(this@getMediaMetadata.albumId).toString())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, this@getMediaMetadata.duration)
                    .build()
        }

fun fetchGenres(context: Context): List<Genre> =
        context.contentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Genres._ID,
                        MediaStore.Audio.Genres.NAME),
                null,
                null,
                null)?.use {
            val list: ArrayList<Genre> = ArrayList()
            while (it.moveToNext()) {
                val genre = Genre(
                        it.getLong(it.getColumnIndex(MediaStore.Audio.Genres._ID)),
                        null,
                        it.getString(it.getColumnIndex(MediaStore.Audio.Genres.NAME)).let {
                            if (it.isBlank()) UNKNOWN else it
                        })
                list.add(genre)
            }

            return@use list
        } ?: emptyList()

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

fun getNotification(context: Context, sessionToken: MediaSession.Token,
                    song: Song, albumTitle: String,
                    playWhenReady: Boolean): Deferred<Notification> =
        async {
            val artwork = try {
                Glide.with(context)
                        .asBitmap()
                        .load(getArtworkUriFromAlbumId(song.albumId))
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
                                    getCommandPendingIntent(context,
                                            BottomSheetViewModel.PlaybackButton.PREV)
                            ).build(),
                            if (playWhenReady) {
                                Notification.Action.Builder(
                                        Icon.createWithResource(context,
                                                R.drawable.ic_pause),
                                        context.getString(R.string.notification_action_pause),
                                        getCommandPendingIntent(context,
                                                BottomSheetViewModel.PlaybackButton.PLAY_OR_PAUSE)
                                ).build()
                            } else {
                                Notification.Action.Builder(
                                        Icon.createWithResource(context,
                                                R.drawable.ic_play),
                                        context.getString(R.string.notification_action_play),
                                        getCommandPendingIntent(context,
                                                BottomSheetViewModel.PlaybackButton.PLAY_OR_PAUSE)
                                ).build()
                            },
                            Notification.Action.Builder(
                                    Icon.createWithResource(context,
                                            R.drawable.ic_forward),
                                    context.getString(R.string.notification_action_next),
                                    getCommandPendingIntent(context,
                                            BottomSheetViewModel.PlaybackButton.NEXT)
                            ).build())
                    .build()
        }

private fun getCommandPendingIntent(context: Context, command: BottomSheetViewModel.PlaybackButton): PendingIntent =
        PendingIntent.getService(context, 0,
                PlayerService.createIntent(context).apply {
                    action = command.name
                    putExtra(PlayerService.ARGS_KEY_CONTROL_COMMAND, command.ordinal)
                },
                PendingIntent.FLAG_CANCEL_CURRENT)

fun Notification.show(service: Service, playWhenReady: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playWhenReady) {
        service.startForeground(NOTIFICATION_ID_PLAYER, this)
    } else {
        service.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID_PLAYER, this)
    }
}

fun Service.destroyNotification() {
    this.stopForeground(true)
}