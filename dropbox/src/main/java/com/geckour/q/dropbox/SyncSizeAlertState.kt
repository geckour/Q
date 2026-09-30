package com.geckour.q.dropbox

import com.geckour.q.dropbox.model.SyncSizeAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SyncSizeAlertState {

    private val mutableAlert = MutableStateFlow<SyncSizeAlert?>(null)

    val alert: StateFlow<SyncSizeAlert?> = mutableAlert

    fun update(alert: SyncSizeAlert?) {
        mutableAlert.value = alert
    }
}
