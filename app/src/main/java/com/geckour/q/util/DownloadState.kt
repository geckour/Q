package com.geckour.q.util

import com.geckour.q.data.db.dao.ArtistDao
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.dropbox.existsAsLocalFile
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

val UiTrack.isDownloaded get() = dropboxPath != null && sourcePath.existsAsLocalFile()

fun ArtistDao.isAllIncludingTracksDownloadedAsFlow(artistId: Long): Flow<Boolean> =
    combine(
        getIncludingDropboxSourcePathsAsFlow(artistId),
        DownloadState.changedCount
    ) { sourcePaths, _ ->
        sourcePaths.all { it.existsAsLocalFile() }
    }.flowOn(Dispatchers.IO)
