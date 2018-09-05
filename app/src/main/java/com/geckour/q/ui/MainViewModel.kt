package com.geckour.q.ui

import android.arch.lifecycle.ViewModel
import com.geckour.q.domain.model.*
import com.geckour.q.service.PlayerService
import com.geckour.q.util.SingleLifeEvent

class MainViewModel : ViewModel() {

    internal val resumedFragmentId: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val selectedArtist: SingleLifeEvent<Artist> = SingleLifeEvent()
    internal val selectedAlbum: SingleLifeEvent<Album> = SingleLifeEvent()
    internal var selectedSong: Song? = null
    internal val selectedGenre: SingleLifeEvent<Genre> = SingleLifeEvent()
    internal val selectedPlaylist: SingleLifeEvent<Playlist> = SingleLifeEvent()
    internal val newQueue: SingleLifeEvent<PlayerService.InsertQueue> = SingleLifeEvent()

    private var currentOrientedClassType: PlayerService.OrientedClassType? = null

    val isLoading: SingleLifeEvent<Boolean> = SingleLifeEvent()

    fun onRequestNavigate(artist: Artist) {
        selectedAlbum.value = null
        selectedSong = null
        selectedArtist.value = artist
        selectedGenre.value = null
        selectedPlaylist.value = null
        currentOrientedClassType = PlayerService.OrientedClassType.ARTIST
    }

    fun onRequestNavigate(album: Album) {
        selectedArtist.value = null
        selectedSong = null
        selectedAlbum.value = album
        selectedGenre.value = null
        selectedPlaylist.value = null
        currentOrientedClassType = PlayerService.OrientedClassType.ALBUM
    }

    fun onRequestNavigate(song: Song) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = song
        selectedGenre.value = null
        selectedPlaylist.value = null
        currentOrientedClassType = PlayerService.OrientedClassType.SONG
    }

    fun onRequestNavigate(genre: Genre) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = null
        selectedGenre.value = genre
        selectedPlaylist.value = null
        currentOrientedClassType = PlayerService.OrientedClassType.GENRE
    }

    fun onRequestNavigate(playlist: Playlist) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = null
        selectedGenre.value = null
        selectedPlaylist.value = playlist
        currentOrientedClassType = PlayerService.OrientedClassType.PLAYLIST
    }

    fun onNewQueue(songs: List<Song>, actionType: PlayerService.InsertActionType) {
        newQueue.value = PlayerService.InsertQueue(
                PlayerService.QueueMetadata(actionType, PlayerService.OrientedClassType.SONG),
                songs)
    }
}