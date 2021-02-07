package com.geckour.q.presentation.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import kotlinx.coroutines.flow.Flow

class ArtistListViewModel(application: Application) : AndroidViewModel(application) {

    internal val artistIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val artistListFlow: Flow<List<Artist>> =
        DB.getInstance(application).artistDao().getAllOrientedAlbumAsync()
}