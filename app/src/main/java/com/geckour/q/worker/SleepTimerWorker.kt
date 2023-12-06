package com.geckour.q.worker

import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.domain.model.PlayerState
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.getTimeString
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

class SleepTimerWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {

    companion object {

        internal const val NAME = "SleepTimerWorker"

        private const val NOTIFICATION_ID_SLEEP_TIMER = 310

        private const val KEY_TRACK_DURATION = "key_track_duration"
        private const val KEY_PLAYBACK_POSITION = "key_playback_position"
        private const val KEY_EXPIRE_TIME = "key_expire_time"
        private const val KEY_TOLERANCE = "key_tolerance"

        fun createInputData(
            trackDuration: Long,
            playbackPosition: Long,
            expireTime: Long,
            tolerance: Long
        ): Data = Data.Builder()
            .putLong(KEY_TRACK_DURATION, trackDuration)
            .putLong(KEY_PLAYBACK_POSITION, playbackPosition)
            .putLong(KEY_EXPIRE_TIME, expireTime)
            .putLong(KEY_TOLERANCE, tolerance)
            .build()
    }

    private var expireTime = -1L
    private var tolerance = 0L
    private var playbackPosition = 0L
    private var lastPlaybackPosition = playbackPosition
    private var trackDuration = 0L
        set(value) {
            endTimeCurrentTrack =
                System.currentTimeMillis() + (value - playbackPosition)
            field = value
        }
    private var endTimeCurrentTrack = 0L
    private var pauseScheduled = false

    private val sharedPreferences by inject<SharedPreferences>()

    override suspend fun doWork(): Result = checkExpired()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID_SLEEP_TIMER,
            getNotification(expireTime - System.currentTimeMillis())
        )

    private suspend fun checkExpired(): Result {
        setForeground(getForegroundInfo())

        expireTime = inputData.getLong(KEY_EXPIRE_TIME, -1)
        tolerance = inputData.getLong(KEY_TOLERANCE, 0)
        playbackPosition = inputData.getLong(KEY_PLAYBACK_POSITION, 0)
        trackDuration = inputData.getLong(KEY_TRACK_DURATION, 0)

        while (isStopped.not()) {
            if (expireTime < 0) {
                return Result.failure()
            }
            if (pauseScheduled && lastPlaybackPosition != playbackPosition) {
                pausePlayback()
                return Result.failure()
            }

            sharedPreferences.getString(PlayerService.PREF_KEY_PLAYER_STATE, null)
                ?.let { Json.decodeFromString<PlayerState>(it) }
                ?.let {
                    trackDuration = it.duration
                    playbackPosition = it.progress
                }

            val now = System.currentTimeMillis()
            if (tolerance > 0) {
                val diff = abs(endTimeCurrentTrack - expireTime)

                if (diff < tolerance) {
                    pauseScheduled = true
                }
            } else if (expireTime < now) {
                pausePlayback()
                return Result.success()
            }
            lastPlaybackPosition = playbackPosition
            delay(200)
        }

        return Result.failure()
    }

    private fun getNotification(remaining: Long) =
        applicationContext.getNotificationBuilder(
            QNotificationChannel.NOTIFICATION_CHANNEL_ID_SLEEP_TIMER
        )
            .setSmallIcon(R.drawable.ic_hourglass_empty)
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
            .setContentTitle(applicationContext.getString(R.string.notification_title_sleep_timer))
            .setContentText(
                if (tolerance > 0) applicationContext.getString(
                    R.string.notification_text_sleep_timer_with_tolerance,
                    remaining.getTimeString(),
                    tolerance.getTimeString()
                )
                else applicationContext.getString(
                    R.string.notification_text_sleep_timer,
                    remaining.getTimeString()
                )
            )
            .build()

    private fun pausePlayback() {
        MediaButtonReceiver.buildMediaButtonPendingIntent(
            applicationContext,
            PlaybackStateCompat.ACTION_PAUSE
        ).send()
    }
}