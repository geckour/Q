package com.geckour.q.ui.setting

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingViewModel : ViewModel() {

    internal val scrollToTop: MutableLiveData<Unit> = MutableLiveData()

    fun onToolbarClick() {
        scrollToTop.value = Unit
    }
}