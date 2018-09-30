package com.geckour.q.ui.setting

import androidx.lifecycle.ViewModel

class SettingItemViewModel(val title: String,
                           val desc: String,
                           var summary: String?,
                           val hasSwitch: Boolean,
                           private val onClick: SettingItemViewModel.() -> Unit = {}) : ViewModel() {
    var switchState: Boolean = false

    fun onClick() {
        onClick.invoke(this)
    }

    fun onSwitchClick() {
        switchState = switchState.not()
    }
}