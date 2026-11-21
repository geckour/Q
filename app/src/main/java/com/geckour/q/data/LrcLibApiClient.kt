package com.geckour.q.data

import android.os.SystemClock
import com.geckour.q.BuildConfig
import com.geckour.q.core.util.UNKNOWN
import com.geckour.q.core.util.normalizedForMatch
import com.geckour.q.core.util.withoutStoreAlbumSuffix
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
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * The names a track is known by, to look up its lyric with.
 */
data class TrackSignature(
    val title: String,
    val artistName: String,
    val albumName: String,
)

/**
 * Client of the LRCLIB (https://lrclib.net) lyrics API.
 */
class LrcLibApiClient {

    companion object {

        private const val BASE_URL = "https://lrclib.net"

        private const val TIMEOUT_SECONDS = 10L

        /**
         * Allowance of the difference between the duration of the track and the lyric made for it,
         * beyond which the timings of the lyric are not trusted.
         */
        const val DURATION_TOLERANCE_MILLIS = 3_000L

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

        /**
         * The pause LRCLIB asks for between requests, even when they succeed.
         */
        private const val REQUEST_INTERVAL_MILLIS = 300L

        /**
         * The ratio of the random delay added to `Retry-After`, which LRCLIB asks for so that the
         * clients throttled at once do not retry at once.
         */
        private const val RETRY_AFTER_JITTER_RATIO = 0.3
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
     * Returns the lyric lines of the track known by [signatures], or `null` when LRCLIB has no
     * lyric for it. A track can be known by several signatures, as a local track and the Spotify
     * track linked to it may name the same song differently.
     *
     * Looks up the lyric exactly by each signature first, then searches by the title and the
     * artist, and lastly searches by the title only to pick a record whose artist loosely matches.
     * The exact lookup also tries the album name without the suffix such as " - Single", which the
     * iTunes Store adds and most records lack.
     *
     * Throws when the request itself failed, so that the caller can retry it later.
     */
    suspend fun getLyricLines(
        signatures: List<TrackSignature>,
        durationMillis: Long,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val knownSignatures = signatures
            .flatMap { listOf(it, it.copy(albumName = it.albumName.withoutStoreAlbumSuffix())) }
            .filter { it.title != UNKNOWN && it.artistName != UNKNOWN }
            .distinct()

        (knownSignatures.firstNotNullOfOrNull { getExactly(it, durationMillis) }
            ?: knownSignatures
                .distinctBy { it.title to it.artistName }
                .firstNotNullOfOrNull { searchByTitleAndArtist(it, durationMillis) }
            ?: knownSignatures
                .map { it.title }
                .distinct()
                .firstNotNullOfOrNull { searchByTitle(it, knownSignatures, durationMillis) })
            ?.toLyricLines()
    }

    private suspend fun getExactly(
        signature: TrackSignature,
        durationMillis: Long,
    ): LrcLibLyric? {
        val durationSeconds = durationMillis / 1000

        return request(
            "get",
            listOfNotNull(
                "track_name" to signature.title,
                "artist_name" to signature.artistName,
                // An unknown album would only keep the track from matching.
                ("album_name" to signature.albumName)
                    .takeIf { signature.albumName != UNKNOWN && signature.albumName.isNotBlank() },
                // LRCLIB rejects the request with a duration out of this range.
                ("duration" to durationSeconds.toString()).takeIf { durationSeconds in 1..3600 },
            ),
        )?.let { json.decodeFromString<LrcLibLyric>(it) }
    }

    private suspend fun searchByTitleAndArtist(
        signature: TrackSignature,
        durationMillis: Long,
    ): LrcLibLyric? =
        request(
            "search",
            listOf(
                "track_name" to signature.title,
                "artist_name" to signature.artistName,
            ),
        )?.let { json.decodeFromString<List<LrcLibLyric>>(it) }
            .orEmpty()
            .filter { it.lastsAbout(durationMillis) }
            .preferSynced()

    /**
     * Searches by [title] only, which finds the records of an artist named with more or less
     * words, such as "A feat. B" for "A".
     */
    private suspend fun searchByTitle(
        title: String,
        signatures: List<TrackSignature>,
        durationMillis: Long,
    ): LrcLibLyric? =
        request("search", listOf("track_name" to title))
            ?.let { json.decodeFromString<List<LrcLibLyric>>(it) }
            .orEmpty()
            .filter { record ->
                record.lastsAbout(durationMillis) &&
                        signatures.any { record.looselyMatches(it) }
            }
            .preferSynced()

    private fun LrcLibLyric.lastsAbout(durationMillis: Long): Boolean =
        duration == null || abs(duration * 1000 - durationMillis) <= DURATION_TOLERANCE_MILLIS

    /**
     * Whether the record has the same title as [signature] and an artist name that contains the
     * one of [signature] or is contained in it, once both are normalized.
     */
    private fun LrcLibLyric.looselyMatches(signature: TrackSignature): Boolean {
        val recordTitle = trackName?.normalizedForMatch()?.ifEmpty { null } ?: return false
        val recordArtist = artistName?.normalizedForMatch()?.ifEmpty { null } ?: return false
        val artist = signature.artistName.normalizedForMatch().ifEmpty { return false }

        return recordTitle == signature.title.normalizedForMatch() &&
                (recordArtist in artist || artist in recordArtist)
    }

    private fun List<LrcLibLyric>.preferSynced(): LrcLibLyric? =
        firstOrNull { it.syncedLyrics.isNullOrBlank().not() } ?: firstOrNull()

    private suspend fun request(
        path: String,
        queries: List<Pair<String, String>>,
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

            val retryAfterMillis = try {
                client
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
                                response.header("Retry-After").toRetryAfterMillis().withJitter()

                            else -> throw IllegalStateException(
                                "Failed to request lyric to LRCLIB: ${response.code}"
                            )
                        }
                    }
            } finally {
                requestAllowedAtElapsedMillis =
                    SystemClock.elapsedRealtime() + REQUEST_INTERVAL_MILLIS
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

    private fun Long.withJitter(): Long =
        this + Random.nextLong((this * RETRY_AFTER_JITTER_RATIO).toLong() + 1)

    private fun LrcLibLyric.toLyricLines(): List<LyricLine>? {
        syncedLyrics?.parseLrc()?.let { if (it.isNotEmpty()) return it }

        return plainLyrics?.takeIf { it.isNotBlank() }
            ?.lines()
            ?.map { LyricLine(0, it) }
    }

    @Serializable
    private data class LrcLibLyric(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )
}
