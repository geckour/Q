package com.geckour.q.spotify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SPOTIFY_PACKAGE_NAME = "com.spotify.music"

private const val SPOTIFY_RADIO_ACTION_KEYWORD = "radio"

private const val SPOTIFY_RADIO_TIMEOUT_SECONDS = 10

private const val SPOTIFY_SESSION_POLL_INTERVAL_MILLIS = 200

class SpotifyRadioUnavailableException :
    IllegalStateException("Spotify did not offer to start radio")

val Context.hasSpotifyNotificationAccess: Boolean
    get() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

fun createSpotifyNotificationAccessSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, SpotifyNotificationListenerService::class.java)
                .flattenToString()
        )

fun createSpotifyLaunchIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE_NAME)

suspend fun startSpotifyTrackRadio(context: Context, trackUri: String) {
    val appRemote = connectSpotifyAppRemote(context, showAuthView = false)
    try {
        withTimeoutOrNull(SPOTIFY_RADIO_TIMEOUT_SECONDS.seconds) {
            appRemote.playerApi.play(trackUri)
            appRemote.awaitTrack(trackUri)
        } ?: throw SpotifyPlaybackStartTimeoutException()
    } finally {
        SpotifyAppRemote.disconnect(appRemote)
    }

    val (controller, radioAction) = withTimeoutOrNull(SPOTIFY_RADIO_TIMEOUT_SECONDS.seconds) {
        context.awaitSpotifyRadioAction()
    } ?: throw SpotifyRadioUnavailableException()
    controller.transportControls.sendCustomAction(radioAction, radioAction.extras)
}

private suspend fun SpotifyAppRemote.awaitTrack(trackUri: String) {
    suspendCancellableCoroutine { continuation ->
        var subscription: Subscription<PlayerState>? = null
        subscription = playerApi.subscribeToPlayerState()
            .setEventCallback { playerState ->
                if (playerState.track?.uri == trackUri && continuation.isActive) {
                    subscription?.cancel()
                    continuation.resume(Unit)
                }
            }
        continuation.invokeOnCancellation { subscription.cancel() }
    }
}

private suspend fun Context.awaitSpotifyRadioAction(): Pair<MediaController, PlaybackState.CustomAction> {
    val sessionManager = requireNotNull(getSystemService(MediaSessionManager::class.java))
    val listener = ComponentName(this, SpotifyNotificationListenerService::class.java)
    while (true) {
        val controller = sessionManager.getActiveSessions(listener)
            .firstOrNull { it.packageName == SPOTIFY_PACKAGE_NAME }
        val radioAction = controller?.playbackState?.customActions?.firstOrNull {
            it.action.contains(SPOTIFY_RADIO_ACTION_KEYWORD, ignoreCase = true)
        }
        if (controller != null && radioAction != null) return controller to radioAction

        delay(SPOTIFY_SESSION_POLL_INTERVAL_MILLIS.milliseconds)
    }
}
