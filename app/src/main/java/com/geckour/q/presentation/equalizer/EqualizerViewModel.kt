package com.geckour.q.presentation.equalizer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EqualizerViewModel : ViewModel() {

    internal val flatten: MutableLiveData<Unit> = MutableLiveData()
    val enabled: MutableLiveData<Boolean> = MutableLiveData(false)

    fun onFlatten() {
        flatten.value = Unit
    }

    fun onToggleEnabled() {
        enabled.value = enabled.value?.not() ?: true
    }
}