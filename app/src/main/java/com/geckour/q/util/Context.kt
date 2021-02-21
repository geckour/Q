package com.geckour.q.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.BuildConfig

fun Context.obtainDbxClient(): DbxClientV2 =
    DbxClientV2(
        DbxRequestConfig.newBuilder("qp/${BuildConfig.VERSION_NAME}").build(),
        PreferenceManager.getDefaultSharedPreferences(this).dropboxToken
    )