package com.geckour.q.ui

import android.arch.lifecycle.ViewModel
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.util.SingleLifeEvent

class MainViewModel : ViewModel() {

    internal val selectedNavId: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val selectedArtist: SingleLifeEvent<Artist> = SingleLifeEvent()
    internal val selectedAlbum: SingleLifeEvent<Album> = SingleLifeEvent()

    fun onFragmentInflated(navId: Int) {
        selectedNavId.value = navId
    }

    fun onRequestNavigate(artist: Artist) {
        selectedArtist.value = artist
    }

    fun onRequestNavigate(album: Album) {
        selectedAlbum.value = album
    }
}