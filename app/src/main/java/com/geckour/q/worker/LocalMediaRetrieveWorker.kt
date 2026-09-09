package com.geckour.q.worker

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.geckour.q.R
import com.geckour.q.data.db.DB
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class LocalMediaRetrieveWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    companion object {

        const val TAG = "local_media_retrieve_worker"
        const val KEY_ONLY_ADDED = "key_only_added"

        private val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA
        )
        private const val SELECTION = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        private const val ORDER = "${MediaStore.Audio.Media.DATE_MODIFIED} ASC"

        private const val PROGRESS_UPDATE_THRESHOLD_MILLIS = 200
    }

    private var totalFilesCount = -1
    private var currentIndex = 0
    private var skippedFilesCount = 0
    private var currentPath: String? = null
    private var lastProgressUpdatedTime = 0L
    private var speeds = listOf<Float>()
    private val remainingDuration get() = ((totalFilesCount - currentIndex) / speeds.average()).toLong()

    override suspend fun doWork(): Result {
        Timber.d("qgeck media retrieve worker started")
        val db = DB.getInstance(applicationContext)
        val onlyAdded = inputData.getBoolean(KEY_ONLY_ADDED, false)

        if (onlyAdded.not()) deleteMissingTracks(db)

        val latestModifiedEpochTime = (db.trackDao().getLatestModifiedEpochTime() ?: 0) / 1000
        applicationContext.contentResolver
            .query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                "$SELECTION AND ${MediaStore.Audio.Media.DATE_MODIFIED} > $latestModifiedEpochTime",
                null,
                ORDER
            )?.use { cursor ->
                var lastProgressSampledTime = System.currentTimeMillis()
                while (cursor.moveToNext()) {
                    if (isStopped) {
                        return Result.success(
                            Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build()
                        )
                    }

                    currentIndex = cursor.position + 1
                    totalFilesCount = cursor.count
                    val trackPath = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    )
                    val trackMediaId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    )
                    currentPath = trackPath
                    updateProgress()

                    runCatching {
                        db.storeMediaInfo(applicationContext, trackPath, trackMediaId)
                    }.onSuccess {
                        val now = System.currentTimeMillis()
                        speeds =
                            (speeds + (1 / (now - lastProgressSampledTime)).toFloat())
                                .takeLast(10)
                        lastProgressSampledTime = now
                    }.onFailure { Timber.e(it) }
                }
            }

        Timber.d("qgeck track in db count: ${runBlocking { db.trackDao().count() }}")
        delay(200.milliseconds)

        return Result.success(Data.Builder().putBoolean(KEY_PROGRESS_FINISHED, true).build())
    }

    private suspend fun deleteMissingTracks(db: DB) {
        val existingMediaIds = applicationContext.contentResolver
            .query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.Audio.Media._ID),
                SELECTION,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                buildSet<Long> {
                    while (cursor.moveToNext()) add(cursor.getLong(index))
                }
            }
            ?: return

        db.deleteTracks(db.trackDao().getAllLocalMediaIds() - existingMediaIds)
    }

    private suspend fun updateProgress() {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdatedTime < PROGRESS_UPDATE_THRESHOLD_MILLIS) return

        lastProgressUpdatedTime = now
        setProgress(
            createProgressData(
                title = applicationContext.getString(R.string.progress_title_retrieve_media),
                progressFraction = currentIndex.toFloat() / totalFilesCount,
                remainingFiles = totalFilesCount - currentIndex,
                skippedFiles = skippedFilesCount,
                remainingDuration = remainingDuration,
                paths = listOfNotNull(currentPath?.substringAfterLast('/'))
            )
        )
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
            val existingTrack = trackDao().getByMediaId(trackMediaId)
            existingTrack?.let {
                if (it.track.lastModified >= lastModified) {
                    skippedFilesCount++
                    return@runBlocking it.track.id
                }
            }

            file.storeMediaInfo(
                context,
                Uri.fromFile(file).toString(),
                existingTrack?.track?.id,
                trackMediaId,
                null,
                null,
                lastModified
            )
        }
}