package com.geckour.q.service

import android.Manifest
import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.provider.MediaStore
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Track
import com.geckour.q.presentation.LauncherActivity
import com.geckour.q.presentation.main.MainActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.hiraganized
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class LocalMediaRetrieveService : IntentService(NAME) {

    companion object {
        private const val NAME = "LocalMediaRetrieveService"

        private const val KEY_ONLY_ADDED = "key_only_added"

        private val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA
        )
        private const val SELECTION = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        private const val ORDER = "${MediaStore.Audio.Media.TITLE} ASC"

        fun getIntent(context: Context, clear: Boolean, onlyAdded: Boolean): Intent =
            Intent(context, LocalMediaRetrieveService::class.java).apply {
                putExtra(KEY_CLEAR, clear)
                putExtra(KEY_ONLY_ADDED, onlyAdded)
            }

        fun cancel(context: Context) {
            context.sendBroadcast(Intent(ACTION_CANCEL))
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {
                ACTION_CANCEL -> {
                    expired = true
                }
            }
        }
    }

    private var expired = false

    override fun onCreate() {
        super.onCreate()

        registerReceiver(receiver, IntentFilter(ACTION_CANCEL))
    }

    override fun onHandleIntent(intent: Intent?) {
        if (applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
            val seed = System.currentTimeMillis()
            startForeground(NOTIFICATION_ID_RETRIEVE, getNotification(0 to 0, seed, bitmap))
            Timber.d("qgeck media retrieve service started")
            val db = DB.getInstance(applicationContext)
            if (intent?.getBooleanExtra(KEY_CLEAR, false) == true) {
                db.clearAllTables()
            }
            val onlyAdded = intent?.getBooleanExtra(KEY_ONLY_ADDED, false) == true
            val selection =
                if (onlyAdded) {
                    val latest =
                        runBlocking { db.trackDao().getLatestModifiedEpochTime() ?: 0 } / 1000
                    "$SELECTION AND ${MediaStore.Audio.Media.DATE_MODIFIED} > $latest"
                } else SELECTION
            applicationContext.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    ORDER
                )?.use { cursor ->
                    val newTrackMediaIds = mutableListOf<Long>()
                    while (expired.not() && cursor.moveToNext()) {
                        val progress = cursor.position to cursor.count
                        val trackPath = cursor.getString(
                            cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        )
                        val trackMediaId = cursor.getLong(
                            cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                        )
                        runCatching {
                            db.storeMediaInfo(applicationContext, trackPath, trackMediaId)
                        }.onSuccess {
                            newTrackMediaIds.add(trackMediaId)
                        }.onFailure { Timber.e(it) }
                        sendBroadcast(MainActivity.createProgressIntent(progress))
                        startForeground(
                            NOTIFICATION_ID_RETRIEVE,
                            getNotification(progress, seed, bitmap)
                        )
                    }

                    if (expired.not() && onlyAdded.not()) {
                        val diff = runBlocking { db.trackDao().getAllMediaIds() } - newTrackMediaIds
                        db.deleteTracks(diff)
                    }
                }

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            Timber.d("qgeck media retrieve worker with state: ${expired.not()}")
            Thread.sleep(200)
            sendBroadcast(MainActivity.createSyncCompleteIntent(true))
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }

    private fun getNotification(
        progress: Pair<Int, Int>,
        seed: Long,
        bitmap: Bitmap
    ): Notification = getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
        .setSmallIcon(R.drawable.ic_notification_sync)
        .setOngoing(true)
        .setShowWhen(false)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                App.REQUEST_CODE_LAUNCH_APP,
                LauncherActivity.createIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .setDeleteIntent(
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .setLargeIcon(bitmap.drawProgressIcon(progress, seed))
        .setContentTitle(getString(R.string.notification_title_retriever))
        .setContentText(
            getString(
                R.string.notification_text_retriever,
                progress.first,
                progress.second
            )
        )
        .build()

    private fun Bitmap.drawProgressIcon(progress: Pair<Int, Int>, seed: Long): Bitmap {
        if (progress.second < 0) return this

        val maxTileNumber = 24
        val progressRatio = progress.first.toFloat() / progress.second
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

    private fun DB.deleteTracks(mediaIds: List<Long>) = runBlocking {
        trackDao().getByMediaIds(mediaIds).forEach {
            trackDao().deleteIncludingRootIfEmpty(applicationContext, it.track.id)
        }
    }

    private fun DB.storeMediaInfo(
        context: Context,
        trackPath: String,
        trackMediaId: Long
    ): Long =
        runBlocking {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                trackMediaId
            )

            val file = File(trackPath)
            if (file.exists().not()) {
                context.contentResolver.delete(uri, null, null)
                trackDao().deleteIncludingRootIfEmpty(context, trackMediaId)
                throw IllegalStateException("Media file does not exist")
            }

            val lastModified = file.lastModified()
            trackDao().getByMediaId(trackMediaId)?.let {
                if (it.track.lastModified >= lastModified) return@runBlocking it.track.id
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val title = tag.getAll(FieldKey.TITLE).lastOrNull { it.isNotBlank() }
            val titleSort =
                (tag.getAll(FieldKey.TITLE_SORT).lastOrNull { it.isNotBlank() }
                    ?: title)?.hiraganized

            val albumTitle = tag.getAll(FieldKey.ALBUM).lastOrNull { it.isNotBlank() }
            val cachedAlbum = albumTitle?.let { albumDao().getAllByTitle(it).firstOrNull() }
            val albumTitleSort =
                (tag.getAll(FieldKey.ALBUM_SORT).lastOrNull { it.isNotBlank() } ?: albumTitle)
                    ?.hiraganized
                    ?: cachedAlbum?.album?.titleSort

            val artistTitle = tag.getAll(FieldKey.ARTIST).lastOrNull { it.isNotBlank() }
            val cachedArtist = artistTitle?.let { artistDao().getAllByTitle(it).firstOrNull() }
            val artistTitleSort =
                (tag.getAll(FieldKey.ARTIST_SORT).lastOrNull { it.isNotBlank() } ?: artistTitle)
                    ?.hiraganized
                    ?: cachedArtist?.titleSort

            val albumArtistTitle = tag.getAll(FieldKey.ALBUM_ARTIST).firstOrNull { it.isNotBlank() }
            val cachedAlbumArtist =
                albumArtistTitle?.let { artistDao().getAllByTitle(it).firstOrNull() }
            val albumArtistTitleSort =
                (tag.getAll(FieldKey.ALBUM_ARTIST_SORT).firstOrNull { it.isNotBlank() }
                    ?: albumArtistTitle)
                    ?.hiraganized
                    ?: cachedAlbumArtist?.titleSort

            val duration = header.trackLength.toLong() * 1000
            val pastSongDuration = trackDao().getDurationWithMediaId(trackMediaId) ?: 0
            val trackNum = catchAsNull {
                tag.getFirst(FieldKey.TRACK).let { if (it.isNullOrBlank()) null else it }?.toInt()
            }
            val discNum = catchAsNull {
                tag.getFirst(FieldKey.DISC_NO).let { if (it.isNullOrBlank()) null else it }?.toInt()
            }

            val composerTitle = tag.getAll(FieldKey.COMPOSER).lastOrNull { it.isNotBlank() }
            val composerTitleSort =
                (tag.getAll(FieldKey.COMPOSER_SORT).lastOrNull { it.isNotBlank() } ?: composerTitle)
                    ?.hiraganized

            val artworkUriString = cachedAlbum?.album?.artworkUriString
                ?: tag.artworkList.lastOrNull()?.let { artwork ->
                    val hex = String(Hex.encodeHex(DigestUtils.md5(artwork.binaryData)))
                    val dirName = "images"
                    val dir = File(context.externalMediaDirs[0], dirName)
                    if (dir.exists().not()) dir.mkdir()
                    val imgFile = File(dir, hex)
                    FileOutputStream(imgFile).use {
                        it.write(artwork.binaryData)
                        it.flush()
                    }

                    imgFile.path
                }

            val artist = Artist(
                0,
                artistTitle ?: UNKNOWN,
                artistTitleSort ?: UNKNOWN,
                0,
                duration,
                artworkUriString ?: cachedArtist?.artworkUriString
            )
            val artistId = artistDao().upsert(artist, pastSongDuration)
            val albumArtistId =
                if (albumArtistTitle != null && albumArtistTitleSort != null) {
                    val albumArtist = Artist(
                        0,
                        albumArtistTitle,
                        albumArtistTitleSort,
                        0,
                        duration,
                        artworkUriString ?: cachedAlbumArtist?.artworkUriString
                    )
                    artistDao().upsert(albumArtist, pastSongDuration)
                } else null

            val album = Album(
                0,
                albumArtistId ?: artistId,
                albumTitle ?: UNKNOWN,
                albumTitleSort ?: UNKNOWN,
                artworkUriString,
                albumArtistId != null,
                0,
                duration
            )
            val albumId = albumDao().upsert(album, pastSongDuration)

            val track = Track(
                0,
                lastModified,
                albumId,
                artistId,
                albumArtistId,
                trackMediaId,
                trackPath,
                title ?: UNKNOWN,
                titleSort ?: UNKNOWN,
                composerTitle ?: UNKNOWN,
                composerTitleSort ?: UNKNOWN,
                duration,
                trackNum,
                discNum,
                0
            )

            return@runBlocking trackDao().upsert(track, pastSongDuration)
        }
}