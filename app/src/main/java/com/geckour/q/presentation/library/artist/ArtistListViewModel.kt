package com.geckour.q.presentation.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.util.SingleLiveEvent
import com.geckour.q.util.toDomainModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArtistListViewModel(application: Application) : AndroidViewModel(application) {

    internal val artistIdDeleted: SingleLiveEvent<Long> = SingleLiveEvent()

    private val _albumListData: LiveData<List<Album>> =
        DB.getInstance(application).albumDao().getAllAsync()
    internal val artistListData = MediatorLiveData<List<Artist>>().apply {
        addSource(_albumListData) { flowDataWithArtwork(it) }
    }

    private fun flowDataWithArtwork(data: List<Album>) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DB.getInstance(getApplication())
            artistListData.postValue(data.groupBy { it.artistId }.mapNotNull {
                val thumbString =
                    it.value.sortedBy { it.playbackCount }.map { it.artworkUriString }.firstOrNull()

                db.artistDao().get(it.key)?.toDomainModel()?.copy(thumbUriString = thumbString)
            })
        }
    }
}