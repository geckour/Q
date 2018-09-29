package com.geckour.q.ui.setting

import androidx.lifecycle.ViewModel

class SettingItemViewModel(val title: String,
                           val desc: String,
                           val hasSwitch: Boolean,
                           private val onClick: () -> Unit = {}) : ViewModel() {
    var switchState: Boolean = false

    fun onClick() {
        onClick.invoke()
    }

    fun onSwitchClick() {
        switchState = switchState.not()
    }
}