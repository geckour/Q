package com.geckour.q.presentation.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Artist
import com.geckour.q.util.toDomainModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArtistListViewModel(application: Application) : AndroidViewModel(application) {

    internal val artistIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val artistListData: Flow<List<Artist>> =
        DB.getInstance(application).artistDao().getAllOrientedAlbumAsync().map { artists ->
            artists.map {
                val thumbnailString =
                    DB.getInstance(application).artistDao().getThumbnailUriString(it.id)
                it.toDomainModel().copy(thumbUriString = thumbnailString)
            }
        }
}