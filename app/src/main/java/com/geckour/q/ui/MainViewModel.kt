package com.geckour.q.ui

import android.arch.lifecycle.ViewModel
import com.geckour.q.util.SingleLifeEvent

class MainViewModel : ViewModel() {

    internal val selectedNavId: SingleLifeEvent<Int> = SingleLifeEvent()

    fun onFragmentInflated(navId: Int) {
        selectedNavId.value = navId
    }
}