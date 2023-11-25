package com.geckour.q.worker

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getNotificationBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File

class LocalMediaRetrieveWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    companion object {

        const val KEY_ONLY_ADDED = "key_only_added"

        private val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA
        )
        private const val SELECTION = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        private const val ORDER = "${MediaStore.Audio.Media.TITLE} ASC"
    }

    private var currentTrackPath: String? = null
    private var currentProgressNumerator = 0
    private var currentProgressDenominator = -1
    private val notificationBitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
    private var seed: Long = -1

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33
            || applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                setForeground(getForegroundInfo())
            } catch (t: Throwable) {
                return Result.failure(Data.Builder().putBoolean(KEY_SYNCING_FINISHED, true).build())
            }

            seed = System.currentTimeMillis()
            Timber.d("qgeck media retrieve service started")
            val db = DB.getInstance(applicationContext)
            val onlyAdded = inputData.getBoolean(KEY_ONLY_ADDED, false)
            val selection =
                if (onlyAdded) {
                    val latest =
                        (db.trackDao().getLatestModifiedEpochTime() ?: 0) / 1000
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
                    while (cursor.moveToNext()) {
                        if (isStopped) {
                            return Result.success(
                                Data.Builder().putBoolean(KEY_SYNCING_FINISHED, true).build()
                            )
                        }

                        currentProgressNumerator = cursor.position + 1
                        currentProgressDenominator = cursor.count
                        val trackPath = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        )
                        val trackMediaId = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        )
                        setProgress(
                            createProgressData(
                                numerator = currentProgressNumerator,
                                denominator = currentProgressDenominator,
                                path = trackPath
                            )
                        )

                        runCatching {
                            db.storeMediaInfo(applicationContext, trackPath, trackMediaId)
                        }.onSuccess {
                            newTrackMediaIds.add(trackMediaId)
                        }.onFailure { Timber.e(it) }
                    }

                    if (onlyAdded.not()) {
                        val diff = db.trackDao().getAllLocalMediaIds() - newTrackMediaIds.toSet()
                        db.deleteTracks(diff)
                    }
                }

            Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
            delay(200)

            return Result.success(Data.Builder().putBoolean(KEY_SYNCING_FINISHED, true).build())
        }

        return Result.failure(Data.Builder().putBoolean(KEY_SYNCING_FINISHED, true).build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID_RETRIEVE,
            getNotification(
                currentTrackPath,
                currentProgressNumerator,
                currentProgressDenominator,
                seed,
                notificationBitmap
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    private fun getNotification(
        trackPath: String?,
        progressNumerator: Int,
        progressDenominator: Int,
        seed: Long,
        bitmap: Bitmap
    ): Notification =
        applicationContext.getNotificationBuilder(
            QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER
        )
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    App.REQUEST_CODE_LAUNCH_APP,
                    LauncherActivity.createIntent(applicationContext),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setLargeIcon(bitmap.drawProgressIcon(progressNumerator, progressDenominator, seed))
            .setContentTitle(applicationContext.getString(R.string.notification_title_retriever))
            .setContentText(
                trackPath?.let {
                    applicationContext.getString(
                        R.string.notification_text_retriever_with_path,
                        progressNumerator,
                        progressDenominator,
                        it
                    )
                } ?: applicationContext.getString(
                    R.string.notification_text_retriever,
                    progressNumerator,
                    progressDenominator
                )
            )
            .build()

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