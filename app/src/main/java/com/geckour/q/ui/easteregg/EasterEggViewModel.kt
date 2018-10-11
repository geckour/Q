package com.geckour.q.ui.easteregg

import androidx.lifecycle.ViewModel
import com.geckour.q.domain.model.Song
import com.geckour.q.util.SingleLiveEvent

class EasterEggViewModel : ViewModel() {

    var song: Song? = null
    internal val tap: SingleLiveEvent<Unit> = SingleLiveEvent()

    fun onTapped() {
        tap.call()
    }
}