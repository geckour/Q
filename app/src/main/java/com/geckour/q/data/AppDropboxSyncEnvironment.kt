package com.geckour.q.data

import android.app.PendingIntent
import android.content.Context
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.App
import com.geckour.q.dropbox.DropboxSyncEnvironment
import com.geckour.q.ui.LauncherActivity
import com.geckour.q.util.QNotificationChannel
import com.geckour.q.util.obtainDbxClient
import kotlinx.coroutines.flow.firstOrNull

class AppDropboxSyncEnvironment(private val context: Context) : DropboxSyncEnvironment {

    override val notificationChannelId: String =
        QNotificationChannel.NOTIFICATION_CHANNEL_ID_RETRIEVER.name

    override suspend fun obtainClient(): DbxClientV2? = obtainDbxClient(context).firstOrNull()

    override fun createContentIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            App.REQUEST_CODE_LAUNCH_APP,
            LauncherActivity.createIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
