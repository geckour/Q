package com.geckour.q.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.domain.model.Song
import com.geckour.q.presentation.LauncherActivity
import com.geckour.q.util.PlayerControlCommand
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getCommandIntent
import com.geckour.q.util.getNotificationBuilder
import com.geckour.q.util.getTimeString
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class SleepTimerService : Service() {

    inner class SleepTimerBinder : Binder() {
        val service: SleepTimerService get() = this@SleepTimerService
    }

    companion object {

        private const val NOTIFICATION_ID_SLEEP_TIMER = 310

        private const val ACTION_START = "com.geckour.q.service.sleep_timer.start"
        private const val ACTION_NOTIFY = "com.geckour.q.service.sleep_timer.notify"
        private const val ACTION_CANCEL = "com.geckour.q.service.sleep_timer.cancel"

        private const val KEY_SONG = "key song"
        private const val KEY_PLAYBACK_RATIO = "key playback ratio"
        private const val KEY_EXPIRE_TIME = "key expire time"
        private const val KEY_TOLERANCE = "key tolerance"

        fun start(
            context: Context,
            song: Song,
            playbackRatio: Float,
            expireTime: Long,
            tolerance: Long
        ) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_START
                putExtra(KEY_SONG, song)
                putExtra(KEY_PLAYBACK_RATIO, playbackRatio)
                putExtra(KEY_EXPIRE_TIME, expireTime)
                putExtra(KEY_TOLERANCE, tolerance)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun notifyTrackChanged(context: Context, song: Song, playbackRatio: Float) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_NOTIFY
                putExtra(KEY_SONG, song)
                putExtra(KEY_PLAYBACK_RATIO, playbackRatio)
            }
            context.startService(intent)
        }

        fun getCancelIntent(context: Context): Intent =
            Intent(context, SleepTimerService::class.java).apply { action = ACTION_CANCEL }
    }

    private var expireTime = -1L
    private var tolerance = 0L
    private var ratio = 0f
    private var song: Song? = null
        set(value) {
            value?.let {
                endTimeCurrentSong =
                    System.currentTimeMillis() + (it.duration * (1 - ratio)).toLong()
            }
            field = value
        }
    private var endTimeCurrentSong = 0L
    private var pauseScheduled = false

    private var checkExpiredJob: Job = Job()

    private val binder = SleepTimerBinder()

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_STICKY

        when (intent.action) {
            ACTION_START -> {
                expireTime = intent.getLongExtra(KEY_EXPIRE_TIME, -1)
                tolerance = intent.getLongExtra(KEY_TOLERANCE, 0)
                ratio = intent.getFloatExtra(KEY_PLAYBACK_RATIO, 0f)
                song = intent.getParcelableExtra(KEY_SONG)

                showNotification(expireTime - System.currentTimeMillis())
                checkExpiredJob.cancel()
                checkExpiredJob = checkExpired()
            }
            ACTION_NOTIFY -> {
                if (expireTime < 0) {
                    stopSelf()
                    return START_STICKY
                }
                if (pauseScheduled) {
                    expire()
                    return START_STICKY
                }
                ratio = intent.getFloatExtra(KEY_PLAYBACK_RATIO, 0f)
                song = intent.getParcelableExtra(KEY_SONG)

                checkExpiredJob.cancel()
                checkExpiredJob = checkExpired()
            }
            ACTION_CANCEL -> expire(false)
        }

        return START_STICKY
    }

    private fun checkExpired() = GlobalScope.launch {
        song?.let {
            while (isActive) {
                if (expireTime > -1) {
                    val now = System.currentTimeMillis()
                    if (tolerance > 0) {
                        val diff = abs(endTimeCurrentSong - expireTime)

                        if (diff < tolerance) {
                            pauseScheduled = true
                        }
                    } else if (expireTime < now) {
                        expire()
                    }
                    showNotification(expireTime - now)
                }
                delay(200)
            }
        }
    }

    private fun showNotification(remaining: Long) {
        val notification =
            getNotificationBuilder(QNotificationChannel.NOTIFICATION_CHANNEL_ID_SLEEP_TIMER)
                .setSmallIcon(R.drawable.ic_hourglass_empty)
                .setContentTitle(getString(R.string.notification_title_sleep_timer))
                .setContentText(
                    if (tolerance > 0) getString(
                        R.string.notification_text_sleep_timer_with_tolerance,
                        remaining.getTimeString(),
                        tolerance.getTimeString()
                    )
                    else getString(
                        R.string.notification_text_sleep_timer,
                        remaining.getTimeString()
                    )
                )
                .setOngoing(true)
                .addAction(
                    R.drawable.ic_remove,
                    getString(R.string.dialog_ng),
                    PendingIntent.getService(
                        this,
                        0,
                        getCancelIntent(this),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
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
                    PendingIntent.getService(
                        this,
                        0,
                        getCancelIntent(this),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .build()
        startForeground(NOTIFICATION_ID_SLEEP_TIMER, notification)
    }

    private fun destroyNotification() {
        stopForeground(true)
    }

    private fun expire(pause: Boolean = true) {
        if (pause) {
            startService(getCommandIntent(this@SleepTimerService, PlayerControlCommand.PAUSE))
        }
        checkExpiredJob.cancel()
        destroyNotification()
        stopSelf()
    }
}