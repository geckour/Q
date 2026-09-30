package com.geckour.q.util

import androidx.core.net.toFile
import androidx.core.net.toUri
import com.geckour.q.data.db.dao.ArtistDao
import com.geckour.q.data.db.model.Track
import com.geckour.q.domain.model.UiTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

object DownloadState {

    private val mutableChangedCount = MutableStateFlow(0)

    val changedCount: StateFlow<Int> = mutableChangedCount

    fun notifyChanged() {
        mutableChangedCount.value++
    }
}

val UiTrack.isDownloaded get() = dropboxPath != null && sourcePath.existsAsFile()

val Track.isDownloaded get() = dropboxPath != null && sourcePath.existsAsFile()

fun ArtistDao.isAllIncludingTracksDownloadedAsFlow(artistId: Long): Flow<Boolean> =
    combine(
        getIncludingDropboxSourcePathsAsFlow(artistId),
        DownloadState.changedCount
    ) { sourcePaths, _ ->
        sourcePaths.all { it.existsAsFile() }
    }.flowOn(Dispatchers.IO)

private fun String.existsAsFile(): Boolean =
    isNotBlank() &&
            matches(dropboxUrlPattern).not() &&
            runCatching { toUri().toFile().exists() }.getOrDefault(false)
