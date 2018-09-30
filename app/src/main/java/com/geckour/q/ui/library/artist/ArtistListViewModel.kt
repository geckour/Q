package com.geckour.q.ui.library.artist

import com.geckour.q.ui.library.LibraryViewModel
import com.geckour.q.util.SingleLiveEvent

class ArtistListViewModel : LibraryViewModel() {

    internal val artistIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()
}