package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.geckour.q.BuildConfig
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal fun obtainDbxClient(context: Context): Flow<DbxClientV2?> =
    context.getDropboxCredential().map { credential ->
        credential?.let {
            DbxClientV2(
                dbxRequestConfig,
                DbxCredential.Reader.readFully(it)
            )
        }
    }

internal val dbxRequestConfig =
    DbxRequestConfig.newBuilder("qp/${BuildConfig.VERSION_NAME}")
        .withAutoRetryEnabled()
        .build()

const val DROPBOX_EXPIRES_IN = 14400000L

val dropboxUrlPattern = Regex("^https://.+\\.dl\\.dropboxusercontent\\.com/.+$")

suspend fun JoinedTrack.verifiedWithDropbox(
    context: Context,
    client: DbxClientV2,
    force: Boolean = false
): JoinedTrack? =
    withContext(Dispatchers.IO) {
        track.dropboxPath ?: return@withContext null

        if (force
            || track.sourcePath.isBlank()
            || (track.sourcePath.matches(dropboxUrlPattern)
                    && (track.dropboxExpiredAt ?: 0) <= System.currentTimeMillis())
            || (track.sourcePath.matches(dropboxUrlPattern).not()
                    && track.sourcePath.toUri().toFile().exists().not())
        ) {
            val url = client.files().getTemporaryLink(track.dropboxPath).link
            val expiredAt = System.currentTimeMillis() + DROPBOX_EXPIRES_IN

            val trackDao = DB.getInstance(context).trackDao()
            trackDao.get(track.id)?.let { joinedTrack ->
                trackDao.update(
                    joinedTrack.track.copy(
                        sourcePath = url,
                        dropboxExpiredAt = expiredAt
                    )
                )
            }

            return@withContext copy(
                track = track.copy(
                    sourcePath = url,
                    dropboxExpiredAt = expiredAt
                )
            )
        }

        return@withContext null
    }
