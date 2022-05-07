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
import android.net.Uri
import android.provider.MediaStore
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getNotificationBuilder
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
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
                    startForeground(
                        NOTIFICATION_ID_RETRIEVE,
                        getNotification(null, 0, cursor.count, seed, bitmap)
                    )
                    val newTrackMediaIds = mutableListOf<Long>()
                    while (expired.not() && cursor.moveToNext()) {
                        val numerator = cursor.position + 1
                        val denominator = cursor.count
                        val trackPath = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        )
                        val trackMediaId = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        )
                        sendBroadcast(
                            MainActivity.createProgressIntent(
                                numerator,
                                denominator,
                                trackPath
                            )
                        )
                        startForeground(
                            NOTIFICATION_ID_RETRIEVE,
                            getNotification(trackPath, numerator, denominator, seed, bitmap)
                        )

                        runCatching {
                            db.storeMediaInfo(applicationContext, trackPath, trackMediaId)
                        }.onSuccess {
                            newTrackMediaIds.add(trackMediaId)
                        }.onFailure { Timber.e(it) }
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
        trackPath: String?,
        progressNumerator: Int,
        progressDenominator: Int,
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
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setDeleteIntent(
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setLargeIcon(bitmap.drawProgressIcon(progressNumerator, progressDenominator, seed))
        .setContentTitle(getString(R.string.notification_title_retriever))
        .setContentText(
            trackPath?.let {
                getString(
                    R.string.notification_text_retriever_with_path,
                    progressNumerator,
                    progressDenominator,
                    it
                )
            } ?: getString(
                R.string.notification_text_retriever,
                progressNumerator,
                progressDenominator
            )
        )
        .build()

    private fun Bitmap.drawProgressIcon(
        progressNumerator: Int,
        progressDenominator: Int,
        seed: Long
    ): Bitmap {
        val maxTileNumber = 24
        val progressRatio = progressNumerator.toFloat() / progressDenominator
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
        trackDao().getAllByMediaIds(mediaIds).forEach {
            trackDao().deleteIncludingRootIfEmpty(this@deleteTracks, it.track.id)
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
                throw IllegalStateException("Media file does not exist")
            }

            val lastModified = file.lastModified()
            trackDao().getByMediaId(trackMediaId)?.let {
                if (it.track.lastModified >= lastModified) return@runBlocking it.track.id
            }

            file.storeMediaInfo(
                context,
                Uri.fromFile(file).toString(),
                trackMediaId,
                null,
                null,
                lastModified
            )
        }
}