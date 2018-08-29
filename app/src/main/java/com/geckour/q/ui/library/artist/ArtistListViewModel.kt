package com.geckour.q.ui.library.artist

import android.arch.lifecycle.ViewModel
import com.geckour.q.domain.model.Artist
import com.geckour.q.util.SingleLifeEvent

class ArtistListViewModel : ViewModel() {

    internal val clickedArtist: SingleLifeEvent<Artist> = SingleLifeEvent()

    fun onClickRoot(artist: Artist) {
        clickedArtist.value = artist
    }
}