package com.geckour.q.util

import com.geckour.q.domain.model.SyncSizeAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SyncSizeAlertState {

    private val mutableAlert = MutableStateFlow<SyncSizeAlert?>(null)

    val alert: StateFlow<SyncSizeAlert?> = mutableAlert

    fun update(alert: SyncSizeAlert?) {
        mutableAlert.value = alert
    }
}
