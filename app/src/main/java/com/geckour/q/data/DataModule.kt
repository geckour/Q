package com.geckour.q.data

import androidx.preference.PreferenceManager
import com.geckour.q.data.db.DB
import com.geckour.q.spotify.api.SpotifyApiClient
import com.geckour.q.spotify.api.SpotifyContentClient
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val dataModule = module {
    single {
        DB.getInstance(androidApplication())
    }

    single {
        PreferenceManager.getDefaultSharedPreferences(androidApplication())
    }

    single {
        LrcLibApiClient()
    }

    single {
        SpotifyApiClient(DataStoreSpotifyCredentialStore(androidApplication()))
    }

    single {
        SpotifyContentClient(androidApplication())
    }
}
