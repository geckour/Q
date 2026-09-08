package com.geckour.q

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.geckour.q.data.dataModule
import com.geckour.q.data.db.DB
import com.geckour.q.ui.di.viewModelModule
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.getAlreadyRunHiraganized
import com.geckour.q.util.setAlreadyRunHiraganized
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber
import java.io.File

class App : Application() {

    companion object {
        const val REQUEST_CODE_LAUNCH_APP = 184
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannel()

        MainScope().launch {
            if (getAlreadyRunHiraganized().not()) {
                val db = DB.getInstance(this@App)
                db.trackDao().hiraganizeSortAll(db)
                setAlreadyRunHiraganized(true)
            }
        }

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(viewModelModule, dataModule)
        }
    }

    private fun createNotificationChannel() {
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
            createNotificationChannel(channelRetriever)
            createNotificationChannel(channelSleepTimer)
        }
    }
}