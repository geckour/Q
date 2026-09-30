package com.geckour.q.dropbox

import androidx.core.net.toFile
import androidx.core.net.toUri
import com.geckour.q.data.db.model.Track

const val DROPBOX_EXPIRES_IN = 14400000L

val dropboxUrlPattern = Regex("^https://.+\\.dl\\.dropboxusercontent\\.com/.+$")

val Track.isDownloaded get() = dropboxPath != null && sourcePath.existsAsLocalFile()

fun String.existsAsLocalFile(): Boolean =
    isNotBlank() &&
            matches(dropboxUrlPattern).not() &&
            runCatching { toUri().toFile().exists() }.getOrDefault(false)
