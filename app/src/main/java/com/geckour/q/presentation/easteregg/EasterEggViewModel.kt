package com.geckour.q.presentation.easteregg

import androidx.lifecycle.ViewModel
import com.geckour.q.domain.model.Song
import com.geckour.q.util.SingleLiveEvent

class EasterEggViewModel : ViewModel() {

    var song: Song? = null
    internal val tap: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val longTap: SingleLiveEvent<Unit> = SingleLiveEvent()

    fun onTapped() {
        tap.call()
    }

    fun onLongTapped(): Boolean {
        longTap.call()
        return true
    }
}