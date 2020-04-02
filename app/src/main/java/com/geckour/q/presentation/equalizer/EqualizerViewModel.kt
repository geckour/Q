package com.geckour.q.presentation.equalizer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EqualizerViewModel : ViewModel() {

    internal val flatten: MutableLiveData<Unit> = MutableLiveData()
    val enabled: MutableLiveData<Boolean> = MutableLiveData()

    fun onFlatten() {
        flatten.value = null
    }

    fun onToggleEnabled() {
        enabled.value = enabled.value?.not() ?: true
    }
}