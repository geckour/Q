package com.geckour.q.ui.library.album

import com.geckour.q.ui.library.LibraryViewModel
import com.geckour.q.util.SingleLiveEvent

class AlbumListViewModel : LibraryViewModel() {

    internal val albumIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()
}