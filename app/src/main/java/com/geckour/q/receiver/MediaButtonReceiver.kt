package com.geckour.q.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media.session.MediaButtonReceiver
import com.geckour.q.service.PlayerService
import timber.log.Timber

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MediaButtonReceiver.handleIntent(PlayerService.mediaSession, intent)
    }
}