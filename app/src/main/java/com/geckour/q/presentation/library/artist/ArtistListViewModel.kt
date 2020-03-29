package com.geckour.q.presentation.library.artist

import com.geckour.q.presentation.library.LibraryViewModel
import com.geckour.q.util.SingleLiveEvent

class ArtistListViewModel : LibraryViewModel() {

    internal val artistIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()
}