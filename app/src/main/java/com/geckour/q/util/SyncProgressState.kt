package com.geckour.q.util

import com.geckour.q.domain.model.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SyncProgressState {

    private val mutableProgress = MutableStateFlow<SyncProgress?>(null)

    val progress: StateFlow<SyncProgress?> = mutableProgress

    fun update(progress: SyncProgress?) {
        mutableProgress.value = progress
    }
}
