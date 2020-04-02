package com.geckour.q.presentation.library.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class AlbumListViewModel(application: Application) : AndroidViewModel(application) {

    internal val albumIdDeleted: MutableLiveData<Long> = MutableLiveData()
}