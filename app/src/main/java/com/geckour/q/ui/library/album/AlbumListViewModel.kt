package com.geckour.q.ui.library.album

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist

class AlbumListViewModel(db: DB, artist: Artist?) : ViewModel() {

    internal val albumIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val albumListFlow = (artist?.let {
        db.albumDao().getAllByArtistAsync(it.id)
    } ?: db.albumDao().getAllAsync())
}