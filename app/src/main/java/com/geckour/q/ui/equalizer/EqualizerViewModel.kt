package com.geckour.q.ui.equalizer

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

class EqualizerViewModel : ViewModel() {

    internal val flatten: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val toggleEnabled: SingleLiveEvent<Unit> = SingleLiveEvent()
    var enabled: Boolean = false

    fun onToolbarClick() {
    }

    fun onFlatten() {
        flatten.call()
    }

    fun onToggleEnabled() {
        toggleEnabled.call()
    }
}