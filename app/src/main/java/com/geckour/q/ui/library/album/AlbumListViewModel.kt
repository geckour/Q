package com.geckour.q.ui.library.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist

class AlbumListViewModel(application: Application, artist: Artist?) :
    AndroidViewModel(application) {

    internal val albumIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val albumListFlow = (artist?.let {
        DB.getInstance(application).albumDao().getAllByArtistAsync(it.id)
    } ?: DB.getInstance(application).albumDao().getAllAsync())

    class Factory(private val application: Application, private val artist: Artist?) :
        ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            AlbumListViewModel(application, artist) as T
    }
}