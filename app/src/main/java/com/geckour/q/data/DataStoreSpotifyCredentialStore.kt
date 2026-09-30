package com.geckour.q.data

import android.content.Context
import com.geckour.q.spotify.SpotifyCredential
import com.geckour.q.spotify.SpotifyCredentialStore
import com.geckour.q.util.getSpotifyCredential
import com.geckour.q.util.setSpotifyCredential
import kotlinx.coroutines.flow.first

class DataStoreSpotifyCredentialStore(private val context: Context) : SpotifyCredentialStore {

    override suspend fun get(): SpotifyCredential? = context.getSpotifyCredential().first()

    override suspend fun set(credential: SpotifyCredential?) {
        context.setSpotifyCredential(credential)
    }
}
