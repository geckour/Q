package com.geckour.q.data

import android.content.Context
import android.os.LocaleList
import com.geckour.q.BuildConfig
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.domain.model.SpotifyContainerPage
import com.geckour.q.domain.model.SpotifySearchPage
import com.geckour.q.domain.model.SpotifyTrackPage
import com.geckour.q.util.SpotifyAuthRequiredException
import com.geckour.q.util.SpotifyCredential
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getSpotifyCredential
import com.geckour.q.util.setSpotifyCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class SpotifyApiException(val code: Int) : IOException("Spotify responded with $code")

class SpotifyApiClient(private val context: Context) {

    companion object {

        private const val API_BASE_URL = "https://api.spotify.com/v1"

        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"

        private const val TIMEOUT_SECONDS = 10L

        private const val SEARCH_PAGE_SIZE = 10

        private const val PAGE_SIZE = 50

        private const val ARTIST_ALBUMS_PAGE_SIZE = 10

        private const val TOKEN_EXPIRY_MARGIN_MILLIS = 60_000L

        private const val TRACK_URI_PREFIX = "spotify:track:"

        private const val HTTP_BAD_REQUEST = 400

        private const val HTTP_UNAUTHORIZED = 401

        private const val HTTP_NOT_FOUND = 404

        private const val MIN_LANGUAGE_QUALITY = 0.1
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val tokenMutex = Mutex()

    private var currentUserId: String? = null

    suspend fun storeCredential(accessToken: String, refreshToken: String?, expiresInSeconds: Int) {
        context.setSpotifyCredential(
            SpotifyCredential(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L,
            )
        )
    }

    suspend fun clearCredential() {
        currentUserId = null
        context.setSpotifyCredential(null)
    }

    suspend fun search(query: String, offset: Int): SpotifySearchPage {
        val response = json.decodeFromString<SearchResponse>(
            get(
                "search",
                "q" to query,
                "type" to "track,album,artist,playlist",
                "limit" to SEARCH_PAGE_SIZE.toString(),
                "offset" to offset.toString(),
            )
        )
        val ownerId = getCurrentUserId()

        return SpotifySearchPage(
            tracks = response.tracks?.items.orEmpty().mapNotNull { it?.toSpotifyTrack() },
            albums = response.albums?.items.orEmpty().mapNotNull { it?.toContainer() },
            artists = response.artists?.items.orEmpty().mapNotNull { it?.toContainer() },
            playlists = response.playlists?.items.orEmpty().mapNotNull { playlist ->
                playlist?.takeIf { ownerId != null && it.owner?.id == ownerId }?.toContainer()
            },
            nextOffset = listOfNotNull(
                response.tracks,
                response.albums,
                response.artists,
                response.playlists,
            )
                .firstOrNull { it.next != null }
                ?.let { offset + SEARCH_PAGE_SIZE },
        )
    }

    suspend fun getSavedTracks(offset: Int): SpotifyTrackPage =
        get(
            "me/tracks",
            "limit" to PAGE_SIZE.toString(),
            "offset" to offset.toString(),
        ).let { body ->
            json.decodeFromString<Paging<SavedTrack>>(body).toTrackPage { it.track }
        }

    suspend fun getMyPlaylists(offset: Int): SpotifyContainerPage =
        get(
            "me/playlists",
            "limit" to PAGE_SIZE.toString(),
            "offset" to offset.toString(),
        ).let { body ->
            val paging = json.decodeFromString<Paging<ApiPlaylist>>(body)
            SpotifyContainerPage(
                items = paging.items.mapNotNull { it?.toContainer() },
                nextOffset = paging.nextOffset,
            )
        }

    suspend fun getContainerTracks(
        container: SpotifyContainer,
        offset: Int,
    ): SpotifyTrackPage = when (container.kind) {
        SpotifyContainer.Kind.ALBUM -> getAlbumTracks(container, offset)
        SpotifyContainer.Kind.PLAYLIST -> getPlaylistTracks(container.id, offset)
        SpotifyContainer.Kind.SAVED -> getSavedTracks(offset)
        SpotifyContainer.Kind.ARTIST -> getArtistTracks(container, offset)
        SpotifyContainer.Kind.CONTENT -> {
            error("Content items are read through SpotifyContentClient")
        }
    }

    suspend fun getTrack(uri: String): SpotifyTrack? {
        if (uri.startsWith(TRACK_URI_PREFIX).not()) return null

        return json.decodeFromString<ApiTrack>(get("tracks/${uri.removePrefix(TRACK_URI_PREFIX)}"))
            .toSpotifyTrack()
    }

    private suspend fun getArtistTracks(
        artist: SpotifyContainer,
        offset: Int,
    ): SpotifyTrackPage {
        val albumsPaging = json.decodeFromString<Paging<ApiAlbum>>(
            get(
                "artists/${artist.id}/albums",
                "limit" to ARTIST_ALBUMS_PAGE_SIZE.toString(),
                "offset" to offset.toString(),
                "include_groups" to "album,single",
            )
        )
        val albums = albumsPaging.items.mapNotNull { it?.toContainer() }
        Timber.d("qgeck spotify artist albums: ${albums.size}")

        val tracks = albums.flatMap { album -> getAlbumTracks(album, 0).items }
        Timber.d("qgeck spotify artist tracks: ${tracks.size}")

        return SpotifyTrackPage(items = tracks, nextOffset = albumsPaging.nextOffset)
    }

    private suspend fun getAlbumTracks(
        album: SpotifyContainer,
        offset: Int,
    ): SpotifyTrackPage =
        get(
            "albums/${album.id}/tracks",
            "limit" to PAGE_SIZE.toString(),
            "offset" to offset.toString(),
        ).let { body ->
            val paging = json.decodeFromString<Paging<ApiTrack>>(body)
            SpotifyTrackPage(
                items = paging.items.mapNotNull { it?.toSpotifyTrack(album) },
                nextOffset = paging.nextOffset,
            )
        }

    private suspend fun getPlaylistTracks(playlistId: String, offset: Int): SpotifyTrackPage {
        val queries = arrayOf(
            "limit" to PAGE_SIZE.toString(),
            "offset" to offset.toString(),
        )
        val body = try {
            get("playlists/$playlistId/items", *queries)
        } catch (e: SpotifyApiException) {
            if (e.code != HTTP_NOT_FOUND) throw e

            get("playlists/$playlistId/tracks", *queries)
        }

        val paging = json.decodeFromString<Paging<JsonObject>>(body)

        return SpotifyTrackPage(
            items = paging.items.mapNotNull { it?.toApiTrack()?.toSpotifyTrack() },
            nextOffset = paging.nextOffset,
        )
    }

    private fun JsonObject.toApiTrack(): ApiTrack? {
        val trackObject = this["track"] as? JsonObject
            ?: this["item"] as? JsonObject
            ?: takeIf { it.containsKey("uri") }
            ?: return null

        return runCatching { json.decodeFromJsonElement<ApiTrack>(trackObject) }
            .onFailure { Timber.e(it) }
            .getOrNull()
    }

    private suspend fun getCurrentUserId(): String? {
        currentUserId?.let { return it }

        return runCatching { json.decodeFromString<ApiUser>(get("me")).id }
            .getOrNull()
            ?.also { currentUserId = it }
    }

    private suspend fun get(path: String, vararg queries: Pair<String, String>): String =
        withContext(Dispatchers.IO) {
            val url = API_BASE_URL.toHttpUrl()
                .newBuilder()
                .addPathSegments(path)
                .apply { queries.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()

            Timber.d("qgeck spotify request: ${url.encodedPath}")

            request(url, getAccessToken(forceRefresh = false))
                ?: request(url, getAccessToken(forceRefresh = true))
                ?: throw SpotifyApiException(HTTP_UNAUTHORIZED)
        }

    private fun request(url: HttpUrl, accessToken: String): String? =
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept-Language", acceptLanguage())
                .build()
        )
            .execute()
            .use { response ->
                when {
                    response.isSuccessful -> response.body.string()
                    response.code == HTTP_UNAUTHORIZED -> null
                    else -> throw SpotifyApiException(response.code)
                }
            }

    private fun acceptLanguage(): String {
        val locales = LocaleList.getDefault()

        return (0 until locales.size()).joinToString(",") { index ->
            val languageTag = locales[index].toLanguageTag()
            if (index == 0) return@joinToString languageTag

            val quality = (1.0 - index * 0.1).coerceAtLeast(MIN_LANGUAGE_QUALITY)
            "$languageTag;q=${String.format(Locale.US, "%.1f", quality)}"
        }
    }

    private suspend fun getAccessToken(forceRefresh: Boolean): String = tokenMutex.withLock {
        val credential =
            context.getSpotifyCredential().first() ?: throw SpotifyAuthRequiredException()
        if (forceRefresh.not() &&
            credential.expiresAt - TOKEN_EXPIRY_MARGIN_MILLIS > System.currentTimeMillis()
        ) {
            return@withLock credential.accessToken
        }

        val refreshToken = credential.refreshToken ?: run {
            clearCredential()
            throw SpotifyAuthRequiredException()
        }

        refreshAccessToken(refreshToken)
    }

    private suspend fun refreshAccessToken(refreshToken: String): String {
        val token = client.newCall(
            Request.Builder()
                .url(TOKEN_URL)
                .post(
                    FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", refreshToken)
                        .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                        .build()
                )
                .build()
        )
            .execute()
            .use { response ->
                when {
                    response.isSuccessful -> {
                        json.decodeFromString<TokenResponse>(response.body.string())
                    }

                    response.code == HTTP_BAD_REQUEST || response.code == HTTP_UNAUTHORIZED -> {
                        null
                    }

                    else -> throw SpotifyApiException(response.code)
                }
            }
            ?: run {
                clearCredential()
                throw SpotifyAuthRequiredException()
            }

        storeCredential(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: refreshToken,
            expiresInSeconds = token.expiresIn,
        )

        return token.accessToken
    }

    private fun <T> Paging<T>.toTrackPage(trackOf: (T) -> ApiTrack?): SpotifyTrackPage =
        SpotifyTrackPage(
            items = items.mapNotNull { item -> item?.let(trackOf)?.toSpotifyTrack() },
            nextOffset = nextOffset,
        )

    private val Paging<*>.nextOffset: Int?
        get() = if (next == null) null else offset + items.size

    private fun ApiTrack.toSpotifyTrack(album: SpotifyContainer? = null): SpotifyTrack? {
        if (uri.startsWith(TRACK_URI_PREFIX).not()) return null

        return SpotifyTrack(
            uri = uri,
            title = name,
            artistName = artists.joinNames() ?: UNKNOWN,
            albumName = this.album?.name ?: album?.name ?: UNKNOWN,
            albumArtistName = this.album?.artists?.joinNames() ?: album?.creatorName,
            artworkUrl = this.album?.images.largestUrl() ?: album?.artworkUrl,
            duration = durationMs,
            trackNum = trackNumber,
            trackTotal = this.album?.totalTracks ?: album?.totalTracks,
            discNum = discNumber,
            releaseDate = this.album?.releaseDate ?: album?.releaseDate,
            createdAt = 0,
        )
    }

    private fun ApiArtist.toContainer(): SpotifyContainer? {
        val artistId = id ?: return null

        return SpotifyContainer(
            kind = SpotifyContainer.Kind.ARTIST,
            id = artistId,
            uri = uri ?: "spotify:artist:$artistId",
            name = name,
            creatorName = null,
            artworkUrl = images.largestUrl(),
            releaseDate = null,
            totalTracks = null,
        )
    }

    private fun ApiAlbum.toContainer(): SpotifyContainer? {
        val albumId = id ?: return null

        return SpotifyContainer(
            kind = SpotifyContainer.Kind.ALBUM,
            id = albumId,
            uri = uri ?: "spotify:album:$albumId",
            name = name,
            creatorName = artists.joinNames(),
            artworkUrl = images.largestUrl(),
            releaseDate = releaseDate,
            totalTracks = totalTracks,
        )
    }

    private fun ApiPlaylist.toContainer(): SpotifyContainer? {
        val playlistId = id ?: return null

        return SpotifyContainer(
            kind = SpotifyContainer.Kind.PLAYLIST,
            id = playlistId,
            uri = uri ?: "spotify:playlist:$playlistId",
            name = name.orEmpty().ifBlank { UNKNOWN },
            creatorName = owner?.displayName,
            artworkUrl = images.largestUrl(),
            releaseDate = null,
            totalTracks = items?.total ?: tracks?.total,
        )
    }

    private fun List<ApiImage>?.largestUrl(): String? =
        this?.maxByOrNull { it.width ?: 0 }?.url

    private fun List<ApiArtist>.joinNames(): String? =
        joinToString(", ") { it.name }.takeIf { it.isNotBlank() }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    @Serializable
    private data class SearchResponse(
        val tracks: Paging<ApiTrack>? = null,
        val albums: Paging<ApiAlbum>? = null,
        val artists: Paging<ApiArtist>? = null,
        val playlists: Paging<ApiPlaylist>? = null,
    )

    @Serializable
    private data class Paging<T>(
        val items: List<T?> = emptyList(),
        val offset: Int = 0,
        val next: String? = null,
    )

    @Serializable
    private data class SavedTrack(
        val track: ApiTrack? = null,
    )

    @Serializable
    private data class ApiUser(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
    )

    @Serializable
    private data class ApiPlaylist(
        val id: String? = null,
        val uri: String? = null,
        val name: String? = null,
        val owner: ApiUser? = null,
        val images: List<ApiImage> = emptyList(),
        val tracks: ApiPlaylistItems? = null,
        val items: ApiPlaylistItems? = null,
    )

    @Serializable
    private data class ApiPlaylistItems(
        val total: Int? = null,
    )

    @Serializable
    private data class ApiTrack(
        val uri: String,
        val name: String,
        @SerialName("duration_ms") val durationMs: Long,
        @SerialName("track_number") val trackNumber: Int? = null,
        @SerialName("disc_number") val discNumber: Int? = null,
        val artists: List<ApiArtist> = emptyList(),
        val album: ApiAlbum? = null,
    )

    @Serializable
    private data class ApiArtist(
        val id: String? = null,
        val uri: String? = null,
        val name: String = UNKNOWN,
        val images: List<ApiImage> = emptyList(),
    )

    @Serializable
    private data class ApiAlbum(
        val id: String? = null,
        val uri: String? = null,
        val name: String = UNKNOWN,
        val artists: List<ApiArtist> = emptyList(),
        val images: List<ApiImage> = emptyList(),
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("total_tracks") val totalTracks: Int? = null,
    )

    @Serializable
    private data class ApiImage(
        val url: String,
        val width: Int? = null,
    )
}
