package com.geckour.q.presentation.library.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.geckour.q.util.SingleLiveEvent

class AlbumListViewModel(application: Application) : AndroidViewModel(application) {

    internal val albumIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()
}