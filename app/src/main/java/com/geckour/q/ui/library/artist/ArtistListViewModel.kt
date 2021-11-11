package com.geckour.q.ui.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import kotlinx.coroutines.flow.Flow

class ArtistListViewModel(db: DB) : ViewModel() {

    internal val artistIdDeleted: MutableLiveData<Long> = MutableLiveData()

    internal val artistListFlow: Flow<List<Artist>> =
        db.artistDao().getAllOrientedAlbumAsync()
}