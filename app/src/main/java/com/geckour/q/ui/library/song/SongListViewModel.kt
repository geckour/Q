package com.geckour.q.ui.library.song

import com.geckour.q.ui.library.LibraryViewModel
import com.geckour.q.util.SingleLiveEvent

class SongListViewModel : LibraryViewModel() {

    val songIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()
}