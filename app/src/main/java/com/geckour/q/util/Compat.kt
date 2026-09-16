package com.geckour.q.util

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

enum class QNotificationChannel {
    NOTIFICATION_CHANNEL_ID_PLAYER,
    NOTIFICATION_CHANNEL_ID_RETRIEVER
}

fun Context.getNotificationBuilder(channel: QNotificationChannel) =
    NotificationCompat.Builder(this, channel.name)