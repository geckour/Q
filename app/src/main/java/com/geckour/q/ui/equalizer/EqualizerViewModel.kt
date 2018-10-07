package com.geckour.q.ui.equalizer

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

class EqualizerViewModel : ViewModel() {

    internal val flatten: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val toggleEnabled: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val equalizerState: SingleLiveEvent<Boolean> = SingleLiveEvent()
    var enabled: Boolean = false

    fun onFlatten() {
        flatten.call()
    }

    fun onToggleEnabled() {
        toggleEnabled.call()
    }
}