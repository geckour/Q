package com.geckour.q.util

import android.content.Context
import com.geckour.q.BuildConfig
import com.geckour.q.domain.model.UiTrack
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

val SPOTIFY_REDIRECT_URI = "${BuildConfig.APPLICATION_ID}://spotify-auth"

private const val SPOTIFY_SOURCE_PATH_PREFIX = "spotify:"

private const val SPOTIFY_TRACK_URI_PREFIX = "spotify:track:"

const val SPOTIFY_TRAILING_SILENCE_MILLIS = 1_500L

private const val SPOTIFY_CAPABILITIES_TIMEOUT_SECONDS = 5

private val spotifyScopes = arrayOf(
    "app-remote-control",
    "user-library-read",
    "playlist-read-private",
    "playlist-read-collaborative",
)

val isSpotifyConfigured: Boolean get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

val String.isSpotifySourcePath: Boolean get() = startsWith(SPOTIFY_SOURCE_PATH_PREFIX)

val String.isSpotifyTrackUri: Boolean get() = startsWith(SPOTIFY_TRACK_URI_PREFIX)

val String.spotifyWebUrl: String
    get() = split(':')
        .takeIf { it.size >= 3 }
        ?.let { "https://open.spotify.com/${it[it.lastIndex - 1]}/${it.last()}" }
        ?: this

fun isSpotifyInstalled(context: Context): Boolean = SpotifyAppRemote.isSpotifyInstalled(context)

val UiTrack.isSpotify: Boolean get() = sourcePath.isSpotifySourcePath

@Serializable
data class SpotifyCredential(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
)

class SpotifyAuthRequiredException : IllegalStateException("Spotify authorization is required")

class SpotifyPlaybackStartTimeoutException :
    IllegalStateException("Spotify did not start the requested track")

class SpotifyPremiumRequiredException :
    IllegalStateException("Spotify account cannot play tracks on demand")

object SpotifyPlaybackErrorState {

    private val mutableErrors = MutableSharedFlow<Throwable>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val errors: SharedFlow<Throwable> = mutableErrors

    fun emit(error: Throwable) {
        mutableErrors.tryEmit(error)
    }
}

fun createSpotifyAuthorizationRequest(): AuthorizationRequest =
    AuthorizationRequest.Builder(
        BuildConfig.SPOTIFY_CLIENT_ID,
        AuthorizationResponse.Type.TOKEN,
        SPOTIFY_REDIRECT_URI,
    )
        .setScopes(spotifyScopes)
        .build()

fun createSpotifyConnectionParams(showAuthView: Boolean): ConnectionParams =
    ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
        .setRedirectUri(SPOTIFY_REDIRECT_URI)
        .showAuthView(showAuthView)
        .build()

suspend fun authorizeSpotifyAppRemote(context: Context) {
    val appRemote = suspendCancellableCoroutine { continuation ->
        SpotifyAppRemote.connect(
            context,
            createSpotifyConnectionParams(showAuthView = true),
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    if (continuation.isActive) continuation.resume(appRemote)
                    else if (continuation.isCancelled) SpotifyAppRemote.disconnect(appRemote)
                }

                override fun onFailure(throwable: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(throwable)
                }
            }
        )
    }
    try {
        val canPlayOnDemand = withTimeoutOrNull(SPOTIFY_CAPABILITIES_TIMEOUT_SECONDS.seconds) {
            appRemote.getCanPlayOnDemand()
        }
        if (canPlayOnDemand == false) throw SpotifyPremiumRequiredException()
    } finally {
        SpotifyAppRemote.disconnect(appRemote)
    }
}

private suspend fun SpotifyAppRemote.getCanPlayOnDemand(): Boolean? =
    suspendCancellableCoroutine { continuation ->
        userApi.capabilities
            .setResultCallback { capabilities ->
                if (continuation.isActive) continuation.resume(capabilities.canPlayOnDemand)
            }
            .setErrorCallback {
                if (continuation.isActive) continuation.resume(null)
            }
    }
