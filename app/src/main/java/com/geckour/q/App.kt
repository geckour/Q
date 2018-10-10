package com.geckour.q

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.facebook.stetho.Stetho
import com.geckour.q.service.PlayerService.Companion.NOTIFICATION_CHANNEL_ID_PLAYER
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

        val name = getString(R.string.notification_channel_player)
        val description = getString(R.string.notification_channel_description_player)

        val channel =
                NotificationChannel(
                        NOTIFICATION_CHANNEL_ID_PLAYER,
                        name,
                        NotificationManager.IMPORTANCE_LOW
                ).apply { this.description = description }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}