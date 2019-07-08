package com.geckour.q.ui.library

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

open class LibraryViewModel : ViewModel() {

    internal val scrollToTop: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val forceLoad: SingleLiveEvent<Unit> = SingleLiveEvent()
}