package com.geckour.q.ui.setting

import androidx.lifecycle.ViewModel

class SettingItemViewModel(val title: String,
                           val desc: String,
                           var summary: String?,
                           val hasSwitch: Boolean,
                           private val onClick: SettingItemViewModel.() -> Unit = {},
                           private val onSwitchClick: SettingItemViewModel.(Boolean) -> Unit = {})
    : ViewModel() {
    var switchState: Boolean = false

    fun onClick() {
        onClick.invoke(this)
        if (hasSwitch) {
            switchState = switchState.not()
            onSwitchClick.invoke(this, switchState)
        }
    }

    fun onSwitchClick() {
        switchState = switchState.not()
        onSwitchClick.invoke(this, switchState)
    }
}