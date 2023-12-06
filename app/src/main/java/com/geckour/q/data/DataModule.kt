package com.geckour.q.data

import androidx.preference.PreferenceManager
import com.geckour.q.data.db.DB
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val dataModule = module {
    single {
        DB.getInstance(androidApplication())
    }

    single {
        PreferenceManager.getDefaultSharedPreferences(androidApplication())
    }
}