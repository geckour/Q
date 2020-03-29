package com.geckour.q.presentation.library.album

import com.geckour.q.presentation.library.LibraryViewModel
import com.geckour.q.util.SingleLiveEvent

class AlbumListViewModel : LibraryViewModel() {

    internal val albumIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()
}