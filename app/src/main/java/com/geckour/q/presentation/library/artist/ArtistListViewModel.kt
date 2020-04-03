package com.geckour.q.presentation.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Artist
import com.geckour.q.util.toDomainModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.geckour.q.data.db.model.Artist as DBArtist

class ArtistListViewModel(application: Application) : AndroidViewModel(application) {

    internal val artistIdDeleted: MutableLiveData<Long> = MutableLiveData()

    private val _artistListData: LiveData<List<DBArtist>> =
        DB.getInstance(application).artistDao().getAllOrientedAlbumAsync()
    internal val artistListData = MediatorLiveData<List<Artist>>().apply {
        addSource(_artistListData) { flowDataWithArtwork(it) }
    }

    private fun flowDataWithArtwork(data: List<DBArtist>) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DB.getInstance(getApplication())
            artistListData.postValue(data.map {
                val thumbString = db.albumDao()
                    .findByArtistId(it.id)
                    .sortedBy { it.playbackCount }
                    .map { it.artworkUriString }
                    .firstOrNull()

                it.toDomainModel().copy(thumbUriString = thumbString)
            })
        }
    }
}