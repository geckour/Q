package com.geckour.q.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DownloadState {

    private val mutableChangedCount = MutableStateFlow(0)

    val changedCount: StateFlow<Int> = mutableChangedCount

    fun notifyChanged() {
        mutableChangedCount.value++
    }
}
