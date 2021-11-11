package com.geckour.q.util

import android.content.SharedPreferences
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.BuildConfig

fun obtainDbxClient(sharedPreferences: SharedPreferences): DbxClientV2? =
    sharedPreferences.dropboxCredential?.let { credential ->
        DbxClientV2(
            DbxRequestConfig.newBuilder("qp/${BuildConfig.VERSION_NAME}").build(),
            DbxCredential.Reader.readFully(credential)
        )
    }