package com.geckour.q.ui.equalizer

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

class EqualizerViewModel : ViewModel() {

    internal val flatten: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val requireRebindSelf: SingleLiveEvent<Unit> = SingleLiveEvent()
    var enabled: SingleLiveEvent<Boolean> = SingleLiveEvent()

    fun onToolbarClick() {
    }

    fun onFlatten() {
        flatten.call()
    }

    fun onToggleEnabled() {
        enabled.value = enabled.value?.not()
        requireRebindSelf.call()
    }
}