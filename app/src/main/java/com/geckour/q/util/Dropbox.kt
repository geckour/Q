package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun obtainDbxClient(context: Context): Flow<DbxClientV2?> =
    context.getDropboxCredential().map { credential ->
        DbxClientV2(
            dbxRequestConfig,
            DbxCredential.Reader.readFully(credential)
        )
    }

internal val dbxRequestConfig =
    DbxRequestConfig.newBuilder("qp/${BuildConfig.VERSION_NAME}")
        .withAutoRetryEnabled()
        .build()