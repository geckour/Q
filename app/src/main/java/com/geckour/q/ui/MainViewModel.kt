package com.geckour.q.ui

import android.arch.lifecycle.ViewModel
import com.geckour.q.domain.model.*
import com.geckour.q.util.SingleLifeEvent

class MainViewModel : ViewModel() {

    internal val selectedNavId: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val selectedArtist: SingleLifeEvent<Artist> = SingleLifeEvent()
    internal val selectedAlbum: SingleLifeEvent<Album> = SingleLifeEvent()
    internal val selectedSong: SingleLifeEvent<Song> = SingleLifeEvent()
    internal val selectedGenre: SingleLifeEvent<Genre> = SingleLifeEvent()
    internal val selectedPlaylist: SingleLifeEvent<Playlist> = SingleLifeEvent()

    val isLoading: SingleLifeEvent<Boolean> = SingleLifeEvent()

    fun onFragmentInflated(navId: Int) {
        selectedNavId.value = navId
    }

    fun onRequestNavigate(artist: Artist) {
        selectedAlbum.value = null
        selectedSong.value = null
        selectedArtist.value = artist
    }

    fun onRequestNavigate(album: Album) {
        selectedArtist.value = null
        selectedSong.value = null
        selectedAlbum.value = album
    }

    fun onRequestNavigate(song: Song) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong.value = song
    }

    fun onRequestNavigate(genre: Genre) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedGenre.value = genre
    }

    fun onRequestNavigate(playlist: Playlist) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedPlaylist.value = playlist
    }
}