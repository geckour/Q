package com.geckour.q.dropbox

import android.app.PendingIntent
import com.dropbox.core.v2.DbxClientV2

interface DropboxSyncEnvironment {

    val notificationChannelId: String

    suspend fun obtainClient(): DbxClientV2?

    fun createContentIntent(): PendingIntent
}
