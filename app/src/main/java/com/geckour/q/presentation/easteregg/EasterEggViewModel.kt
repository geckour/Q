package com.geckour.q.presentation.easteregg

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.geckour.q.domain.model.Song

class EasterEggViewModel : ViewModel() {

    var song: Song? = null
    internal val tap: MutableLiveData<Unit> = MutableLiveData()
    internal val longTap: MutableLiveData<Unit> = MutableLiveData()

    fun onTapped() {
        tap.value = Unit
    }

    fun onLongTapped(): Boolean {
        longTap.value = Unit
        return true
    }
}