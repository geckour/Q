package com.geckour.q.data

import android.os.SystemClock
import com.geckour.q.BuildConfig
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.util.parseLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Client of the LRCLIB (https://lrclib.net) lyrics API.
 */
class LrcLibApiClient {

    companion object {

        private const val BASE_URL = "https://lrclib.net"

        private const val TIMEOUT_SECONDS = 10L

        /**
         * Allowance of the difference between the duration of the track and the fetched lyric.
         */
        private const val DURATION_TOLERANCE_SECONDS = 3.0

        private const val USER_AGENT =
            "Q/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"

        private const val MAX_RETRY_COUNT = 2

        /**
         * Fallback of the interval until retrying, for when the `Retry-After` header is absent
         * or unparsable.
         */
        private const val DEFAULT_RETRY_AFTER_MILLIS = 5_000L

        /**
         * Gives up instead of waiting when LRCLIB requires to wait longer than this,
         * since the caller retries on the next playback anyway.
         */
        private const val MAX_RETRY_AFTER_MILLIS = 30_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Serializes the requests so that a rate limit told one of them is respected by the others,
     * which are kicked off on every switch of the track.
     */
    private val requestMutex = Mutex()

    private var requestAllowedAtElapsedMillis = 0L

    /**
     * Returns the lyric lines of the given track, or `null` when LRCLIB has no lyric for it.
     *
     * Throws when the request itself failed, so that the caller can retry it later.
     */
    suspend fun getLyricLines(track: JoinedTrack): List<LyricLine>? = withContext(Dispatchers.IO) {
        (getExactly(track) ?: searchLoosely(track))?.toLyricLines()
    }

    private suspend fun getExactly(track: JoinedTrack): LrcLibLyric? =
        request(
            "get",
            "track_name" to track.track.title,
            "artist_name" to track.artist.title,
            "album_name" to track.album.title,
            "duration" to track.durationSeconds.toInt().toString(),
        )?.let { json.decodeFromString<LrcLibLyric>(it) }

    private suspend fun searchLoosely(track: JoinedTrack): LrcLibLyric? =
        request(
            "search",
            "track_name" to track.track.title,
            "artist_name" to track.artist.title,
        )?.let { json.decodeFromString<List<LrcLibLyric>>(it) }
            .orEmpty()
            .filter {
                it.duration == null ||
                        abs(it.duration - track.durationSeconds) <= DURATION_TOLERANCE_SECONDS
            }
            .let { candidates ->
                candidates.firstOrNull { it.syncedLyrics.isNullOrBlank().not() }
                    ?: candidates.firstOrNull()
            }

    private suspend fun request(
        path: String,
        vararg queries: Pair<String, String>,
    ): String? {
        val url = BASE_URL.toHttpUrl()
            .newBuilder()
            .addPathSegments("api/$path")
            .apply { queries.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()

        return requestMutex.withLock { requestWithRetry(url) }
    }

    private suspend fun requestWithRetry(url: HttpUrl): String? {
        var retryCount = 0

        while (true) {
            awaitRequestAllowed()

            Timber.d("qgeck request lyric to LRCLIB: $url")

            val retryAfterMillis = client
                .newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
                .execute()
                .use { response ->
                    when {
                        response.isSuccessful -> return response.body.string()

                        response.code == 404 -> return null

                        response.code == 429 ->
                            response.header("Retry-After").toRetryAfterMillis()

                        else -> throw IllegalStateException(
                            "Failed to request lyric to LRCLIB: ${response.code}"
                        )
                    }
                }

            requestAllowedAtElapsedMillis = SystemClock.elapsedRealtime() + retryAfterMillis

            if (retryCount++ >= MAX_RETRY_COUNT) {
                throw IllegalStateException(
                    "Rate limited by LRCLIB: gave up after $retryCount requests"
                )
            }

            Timber.d("qgeck rate limited by LRCLIB, retry after $retryAfterMillis ms")
        }
    }

    /**
     * Suspends until the rate limit told by the last `429` response is lifted,
     * or gives up when it lasts too long.
     */
    private suspend fun awaitRequestAllowed() {
        val waitMillis = requestAllowedAtElapsedMillis - SystemClock.elapsedRealtime()

        if (waitMillis <= 0) return
        if (waitMillis > MAX_RETRY_AFTER_MILLIS) {
            throw IllegalStateException(
                "Rate limited by LRCLIB: required to wait $waitMillis ms"
            )
        }

        Timber.d("qgeck wait $waitMillis ms for the rate limit of LRCLIB")

        delay(waitMillis.milliseconds)
    }

    /**
     * Interprets the `Retry-After` header, which holds either delay seconds or an HTTP date.
     */
    private fun String?.toRetryAfterMillis(): Long {
        this ?: return DEFAULT_RETRY_AFTER_MILLIS

        val seconds = trim().let { value ->
            value.toLongOrNull()
                ?: runCatching {
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toEpochSecond() - System.currentTimeMillis() / 1000
                }.getOrNull()
        } ?: return DEFAULT_RETRY_AFTER_MILLIS

        return seconds.coerceAtLeast(0) * 1000
    }

    private val JoinedTrack.durationSeconds: Double get() = track.duration / 1000.0

    private fun LrcLibLyric.toLyricLines(): List<LyricLine>? {
        syncedLyrics?.parseLrc()?.let { if (it.isNotEmpty()) return it }

        return plainLyrics?.takeIf { it.isNotBlank() }
            ?.lines()
            ?.map { LyricLine(0, it) }
    }

    @Serializable
    private data class LrcLibLyric(
        val duration: Double? = null,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )
}
