package com.geckour.q.data

import android.content.Context
import com.geckour.q.domain.model.SpotifyContainer
import com.geckour.q.domain.model.SpotifyContentItem
import com.geckour.q.domain.model.SpotifyContentPage
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.createSpotifyConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.ContentApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.PendingResult
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.types.ListItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SpotifyContentException(message: String) : IllegalStateException(message)

class SpotifyContentClient(private val context: Context) {

    companion object {

        private const val PAGE_SIZE = 50

        private const val CALL_TIMEOUT_SECONDS = 15L
    }

    private val connectionMutex = Mutex()

    private var appRemote: SpotifyAppRemote? = null

    suspend fun getRootItems(): List<SpotifyContentItem> =
        call("getRecommendedContentItems") {
            it.getRecommendedContentItems(ContentApi.ContentType.DEFAULT)
        }.items.map { it.toContentItem() }

    suspend fun getChildren(container: SpotifyContainer, offset: Int): SpotifyContentPage {
        val item = ListItem(
            container.id,
            container.uri,
            container.artworkUrl?.let { ImageUri(it) },
            container.name,
            container.creatorName,
            false,
            true,
        )
        val listItems = call("getChildrenOfItem") {
            it.getChildrenOfItem(item, PAGE_SIZE, offset)
        }
        val items = listItems.items.map { it.toContentItem() }

        return SpotifyContentPage(
            items = items,
            nextOffset = (offset + items.size).takeIf {
                items.isNotEmpty() && it < listItems.total
            },
        )
    }

    fun release() {
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
    }

    private suspend fun call(
        name: String,
        request: (contentApi: ContentApi) -> PendingResult<ListItems>,
    ): ListItems {
        val pending = withContext(Dispatchers.Main) { request(connect().contentApi) }
        val result = withContext(Dispatchers.IO) {
            pending.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        if (result.isSuccessful.not()) {
            throw result.error ?: SpotifyContentException("$name: ${result.errorMessage}")
        }

        return result.data ?: throw SpotifyContentException("$name returned nothing")
    }

    private suspend fun connect(): SpotifyAppRemote = connectionMutex.withLock {
        appRemote?.takeIf { it.isConnected }?.let { return it }

        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null

        return suspendCancellableCoroutine { continuation ->
            SpotifyAppRemote.connect(
                context,
                createSpotifyConnectionParams(showAuthView = false),
                object : Connector.ConnectionListener {
                    override fun onConnected(appRemote: SpotifyAppRemote) {
                        this@SpotifyContentClient.appRemote = appRemote
                        if (continuation.isActive) continuation.resume(appRemote)
                        else SpotifyAppRemote.disconnect(appRemote)
                    }

                    override fun onFailure(throwable: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(throwable)
                    }
                }
            )
        }
    }

    private fun ListItem.toContentItem(): SpotifyContentItem = SpotifyContentItem(
        id = id.orEmpty().ifBlank { uri.orEmpty() },
        uri = uri.orEmpty(),
        title = title.orEmpty().ifBlank { UNKNOWN },
        subtitle = subtitle?.ifBlank { null },
        artworkUrl = imageUri?.raw?.ifBlank { null },
    )
}
