package com.geckour.q.ui.setting

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

class SettingViewModel : ViewModel() {

    internal val scrollToTop: SingleLiveEvent<Unit> = SingleLiveEvent()

    fun onToolbarClick() {
        scrollToTop.call()
    }
}