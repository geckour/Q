package com.geckour.q.ui.main

import androidx.lifecycle.ViewModel
import com.geckour.q.domain.model.*
import com.geckour.q.util.*

class MainViewModel : ViewModel() {

    internal val resumedFragmentId: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val selectedArtist: SingleLiveEvent<Artist> = SingleLiveEvent()
    internal val selectedAlbum: SingleLiveEvent<Album> = SingleLiveEvent()
    internal var selectedSong: Song? = null
    internal val selectedGenre: SingleLiveEvent<Genre> = SingleLiveEvent()
    internal val selectedPlaylist: SingleLiveEvent<Playlist> = SingleLiveEvent()
    internal val newQueue: SingleLiveEvent<InsertQueue> = SingleLiveEvent()
    internal val requestedPositionInQueue: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val swappedQueuePositions: SingleLiveEvent<Pair<Int, Int>> = SingleLiveEvent()
    internal val removedQueueIndex: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val removePlayOrderOfPlaylist: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val songToDelete: SingleLiveEvent<Song> = SingleLiveEvent()
    internal val cancelSync: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val requireScrollTop: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val searchQuery: SingleLiveEvent<String> = SingleLiveEvent()
    internal val songToEdit: SingleLiveEvent<Song> = SingleLiveEvent()
    internal val albumToEdit: SingleLiveEvent<Album> = SingleLiveEvent()
    internal val artistToEdit: SingleLiveEvent<Artist> = SingleLiveEvent()

    private var currentOrientedClassType: OrientedClassType? = null

    val syncing: SingleLiveEvent<Boolean> = SingleLiveEvent()
    val loading: SingleLiveEvent<Boolean> = SingleLiveEvent()

    fun onRequestNavigate(artist: Artist) {
        selectedAlbum.value = null
        selectedSong = null
        selectedArtist.value = artist
        selectedGenre.value = null
        selectedPlaylist.value = null
        currentOrientedClassType = OrientedClassType.ARTIST
    }

    fun onRequestNavigate(album: Album) {
        selectedArtist.value = null
        selectedSong = null
        selectedAlbum.value = album
        selectedGenre.value = null
        selectedPlaylist.value = null
        currentOrientedClassType = OrientedClassType.ALBUM
    }

    fun onRequestNavigate(song: Song) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = song
        selectedGenre.value = null
        selectedPlaylist.value = null
        currentOrientedClassType = OrientedClassType.SONG
    }

    fun onRequestNavigate(genre: Genre) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = null
        selectedGenre.value = genre
        selectedPlaylist.value = null
        currentOrientedClassType = OrientedClassType.GENRE
    }

    fun onRequestNavigate(playlist: Playlist) {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = null
        selectedGenre.value = null
        selectedPlaylist.value = playlist
        currentOrientedClassType = OrientedClassType.PLAYLIST
    }

    fun onNewQueue(songs: List<Song>,
                   actionType: InsertActionType,
                   classType: OrientedClassType) {
        newQueue.value = InsertQueue(
                QueueMetadata(actionType, classType),
                songs)
    }

    fun onQueueSwap(from: Int, to: Int) {
        swappedQueuePositions.value = Pair(from, to)
    }

    fun onQueueRemove(index: Int) {
        removedQueueIndex.value = index
    }

    fun onRequestRemoveSongFromPlaylist(playOrder: Int) {
        removePlayOrderOfPlaylist.value = playOrder
    }

    fun onCancelSync() {
        cancelSync.call()
    }

    fun onToolbarClick() {
        requireScrollTop.call()
    }
}