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
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.pushMedia
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

        internal val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATA
        )

        fun getIntent(context: Context): Intent = Intent(context, MediaRetrieveService::class.java)

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
            db.clearAllTables()
            applicationContext.contentResolver
                    .query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                            null,
                            "${MediaStore.Audio.Media.TITLE} ASC"
                    )?.use { cursor ->
                        val retriever = MediaMetadataRetriever()
                        while (expired.not() && cursor.moveToNext()) {
                            val progress = retriever.pushMedia(applicationContext, db, cursor)
                            sendBroadcast(MainActivity.createProgressIntent(progress))
                            startForeground(
                                    NOTIFICATION_ID_RETRIEVE,
                                    getNotification(progress, seed, bitmap)
                            )
                        }
                    }

            Timber.d("qgeck track in db count: ${db.trackDao().count()}")
            Timber.d("qgeck media retrieve worker with state: ${expired.not()}")
            sendBroadcast(MainActivity.createSyncCompleteIntent(true))
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }

    private fun getNotification(progress: Pair<Int, Int>, seed: Long, bitmap: Bitmap): Notification =
            this.getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER)
                    .setSmallIcon(R.drawable.ic_notification_sync)
                    .setLargeIcon(bitmap.drawProgressIcon(progress, seed))
                    .setContentTitle(getString(R.string.notification_title_retriever))
                    .setContentText(getString(R.string.notification_text_retriever, progress.first, progress.second))
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
                    .build()

    private fun Bitmap.drawProgressIcon(progress: Pair<Int, Int>, seed: Long): Bitmap {
        if (progress.second < 0) return this

        val canvas = Canvas(this)
        val paint = Paint().apply {
            isAntiAlias = true
        }
        val offset = PointF(canvas.width * 0.5f, canvas.height * 0.5f)
        val innerR = canvas.width * 0.35f
        val outerR = canvas.width * 0.45f
        val random = Random(seed)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        (0 until (24 * progress.first.toFloat() / progress.second).toInt()).forEach {
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
            paint.color = Color.argb(150, random.nextInt(255), random.nextInt(255), random.nextInt(255))
            canvas.drawPath(path, paint)
        }
        val triPath = Path().apply {
            val start = PointF(canvas.width * 0.6f, canvas.height * 0.6f)
            moveTo(start.x, start.y)
            lineTo(canvas.width * 0.95f, canvas.height - (canvas.height - start.y) * 0.5f)
            lineTo(start.x, canvas.height.toFloat())
            close()
        }
        paint.color = Color.WHITE
        canvas.drawPath(triPath, paint)
        paint.apply {
            color = Color.argb(10, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = canvas.width * 0.01f
        }
        canvas.drawPath(triPath, paint)

        return this
    }
}