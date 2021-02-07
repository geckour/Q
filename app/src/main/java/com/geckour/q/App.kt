package com.geckour.q

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.facebook.stetho.Stetho
import com.geckour.q.util.QNotificationChannel
import timber.log.Timber

class App : Application() {

    companion object {
        const val REQUEST_CODE_LAUNCH_APP = 184
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Stetho.initializeWithDefaults(this)
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelPlayer =
            NotificationChannel(
                QNotificationChannel.NOTIFICATION_CHANNEL_ID_PLAYER.name,
                getString(R.string.notification_channel_player),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                this.description = getString(R.string.notification_channel_description_player)
            }

        val channelRetriever =
            NotificationChannel(
                QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER.name,
                getString(R.string.notification_channel_retriever),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                this.description = getString(R.string.notification_channel_description_retriever)
            }

        val channelSleepTimer =
            NotificationChannel(
                QNotificationChannel.NOTIFICATION_CHANNEL_ID_SLEEP_TIMER.name,
                getString(R.string.notification_channel_sleep_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                this.description = getString(R.string.notification_channel_description_sleep_timer)
            }

        getSystemService(NotificationManager::class.java)?.apply {
            createNotificationChannel(channelPlayer)
            createNotificationChannel(channelRetriever)
            createNotificationChannel(channelSleepTimer)
        }
    }
}