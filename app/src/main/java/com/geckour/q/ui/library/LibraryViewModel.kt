package com.geckour.q.ui.library

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

open class LibraryViewModel : ViewModel() {

    val requireScrollTop: SingleLiveEvent<Unit> = SingleLiveEvent()
    val forceLoad: SingleLiveEvent<Unit> = SingleLiveEvent()
}