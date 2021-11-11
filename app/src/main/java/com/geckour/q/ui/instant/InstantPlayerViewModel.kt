package com.geckour.q.ui.instant

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.geckour.q.App
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.service.InstantPlayerService
import com.geckour.q.service.PlayerService
import timber.log.Timber

class InstantPlayerViewModel(private val app: App) : ViewModel() {

    internal val player = MutableLiveData<InstantPlayerService>()

    private var isBoundService = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(app, InstantPlayerService::class.java)) {
                isBoundService = true
                player.value = (service as? InstantPlayerService.PlayerBinder)?.service
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(app, InstantPlayerService::class.java)) {
                onPlayerDestroyed()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(app, InstantPlayerService::class.java)) {
                onPlayerDestroyed()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(app, InstantPlayerService::class.java)) {
                onPlayerDestroyed()
            }
        }
    }

    override fun onCleared() {
        unbindPlayer()

        super.onCleared()
    }

    internal fun bindPlayer() {
        if (isBoundService.not()) {
            app.bindService(
                InstantPlayerService.createIntent(app),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun unbindPlayer() {
        try {
            app.startService(PlayerService.createIntent(app))
        } catch (t: Throwable) {
            Timber.e(t)
        }
        if (isBoundService) app.unbindService(serviceConnection)
    }

    internal fun onPlaybackButtonPressed(playbackButton: PlaybackButton) {
        player.value?.onPlaybackButtonCommitted(playbackButton)
    }

    internal fun onSeekBarProgressChanged(progressRatio: Float) {
        player.value?.seek(progressRatio)
    }

    internal fun onPlayerDestroyed() {
        isBoundService = false
    }
}