package com.geckour.q.ui.library.album

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import kotlinx.coroutines.launch

class AlbumListViewModel(private val db: DB, private val artist: Artist?) : ViewModel() {

    internal val albumIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val albumListFlow = (artist?.let {
        db.albumDao().getAllByArtistIdAsync(it.id)
    } ?: db.albumDao().getAllAsync())

    internal fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            db.albumDao().deleteRecursively(db, albumId)
        }
    }

    internal fun deleteArtist() {
        artist?.let {
            viewModelScope.launch {
                db.artistDao().deleteRecursively(artist.id)
            }
        }
    }
}