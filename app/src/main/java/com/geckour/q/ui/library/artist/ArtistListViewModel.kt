package com.geckour.q.ui.library.artist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ArtistListViewModel(private val db: DB) : ViewModel() {

    internal val artistIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val artistListFlow: Flow<List<Artist>> =
        db.artistDao().getAllOrientedAlbumAsync()

    internal fun deleteArtist(artistId: Long) {
        viewModelScope.launch {
            db.artistDao().deleteRecursively(artistId)
        }
    }
}