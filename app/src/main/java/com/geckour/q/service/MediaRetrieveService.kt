package com.geckour.q.service

import android.Manifest
import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import androidx.core.app.NotificationCompat
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.presentation.LauncherActivity
import com.geckour.q.presentation.main.MainActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.storeMediaInfo
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MediaRetrieveService : IntentService(NAME) {

    companion object {
        private const val NAME = "MediaRetrieveService"
        private const val NOTIFICATION_ID_RETRIEVE = 300

        private const val ACTION_CANCEL = "com.geckour.q.service.retrieve.cancel"
        private const val KEY_CLEAR = "key_clear"

        internal val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA
        )

        fun getIntent(context: Context, clear: Boolean): Intent =
            Intent(context, MediaRetrieveService::class.java).apply {
                putExtra(KEY_CLEAR, clear)
            }

        fun cancel(context: Context) {
            context.sendBroadcast(Intent(ACTION_CANCEL))
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.apply {
                when (action) {
                    ACTION_CANCEL -> {
                        expired = true
                    }
                }
            }
        }
    }

    private lateinit var notificationBuilder: NotificationCompat.Builder

    private var expired = false

    override fun onCreate() {
        super.onCreate()

        notificationBuilder =
            getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
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
            applicationContext.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
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
                            runBlocking {
                                db.storeMediaInfo(applicationContext, trackPath, trackMediaId)
                            }
                        }.onSuccess {
                            newTrackMediaIds.add(trackMediaId)
                        }.onFailure { Timber.e(it) }
                        sendBroadcast(MainActivity.createProgressIntent(progress))
                        startForeground(
                            NOTIFICATION_ID_RETRIEVE,
                            getNotification(progress, seed, bitmap)
                        )
                    }

                    if (expired.not()) {
                        val diff = db.trackDao().getAll().map { it.mediaId } - newTrackMediaIds
                        diff.forEach { db.deleteTrack(it) }
                    }
                }

            Timber.d("qgeck track in db count: ${db.trackDao().count()}")
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
    ): Notification = notificationBuilder
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

    private fun DB.deleteTrack(mediaId: Long) {
        trackDao().getByMediaId(mediaId)?.apply {
            trackDao().delete(this.id)
            if (trackDao().getAllByAlbum(this.albumId).isEmpty())
                albumDao().delete(this.albumId)
            if (trackDao().getAllByArtist(this.artistId).isEmpty())
                artistDao().delete(this.artistId)
        }
    }
}