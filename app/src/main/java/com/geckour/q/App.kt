package com.geckour.q

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.crashlytics.android.Crashlytics
import com.facebook.stetho.Stetho
import com.geckour.q.data.db.DB
import com.geckour.q.service.MediaRetrieveService
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.pushMedia
import io.fabric.sdk.android.Fabric
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

        contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                object : ContentObserver(Handler()) {
                    override fun deliverSelfNotifications(): Boolean = true

                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        super.onChange(selfChange, uri)

                        uri ?: return

                        val id = try {
                            ContentUris.parseId(uri)
                        } catch (t: Throwable) {
                            return
                        }
                        Timber.d("qgeck media id: $id")
                        try {
                            this@App.contentResolver
                                    .query(
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            MediaRetrieveService.projection,
                                            "${MediaStore.Audio.Media._ID}=$id",
                                            null, null
                                    )?.use {
                                        if (it.moveToFirst()) {
                                            Timber.d("qgeck push start")
                                            MediaMetadataRetriever().pushMedia(this@App,
                                                    DB.getInstance(this@App), it)
                                        } else deleteFromDB(id)
                                    }
                        } catch (t: Throwable) {
                            Timber.e(t)
                        }
                    }
                })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelPlayer =
                NotificationChannel(
                        QNotificationChannel.NOTIFICATION_CHANNEL_ID_PLAYER.name,
                        getString(R.string.notification_channel_player),
                        NotificationManager.IMPORTANCE_LOW
                ).apply { this.description = getString(R.string.notification_channel_description_player) }

        val channelRetriever =
                NotificationChannel(
                        QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER.name,
                        getString(R.string.notification_channel_retriever),
                        NotificationManager.IMPORTANCE_LOW
                ).apply { this.description = getString(R.string.notification_channel_description_retriever) }

        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(channelPlayer)
            createNotificationChannel(channelRetriever)
        }
    }

    private fun deleteFromDB(mediaId: Long) {
        Timber.d("qgeck delete start")
        DB.getInstance(applicationContext).apply {
            trackDao().getByMediaId(mediaId)?.apply {
                trackDao().delete(this.id)
                if (trackDao().findByAlbum(this.albumId).isEmpty())
                    albumDao().delete(this.albumId)
                if (trackDao().findByArtist(this.artistId).isEmpty())
                    artistDao().delete(this.artistId)
            }
        }
    }
}

fun AppCompatActivity.setCrashlytics() {
    if (BuildConfig.DEBUG.not()) Fabric.with(this, Crashlytics())
}