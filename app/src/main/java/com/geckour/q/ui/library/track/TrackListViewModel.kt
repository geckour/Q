package com.geckour.q.ui.library.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geckour.q.data.db.DB
import kotlinx.coroutines.launch

class TrackListViewModel(private val db: DB) : ViewModel() {

    internal fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            db.albumDao().deleteRecursively(db, albumId)
        }
    }
}