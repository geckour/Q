package com.geckour.q.ui

import android.arch.lifecycle.ViewModel
import com.geckour.q.domain.model.*
import com.geckour.q.util.*

class MainViewModel : ViewModel() {

    internal val resumedFragmentId: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val selectedArtist: SingleLifeEvent<Artist> = SingleLifeEvent()
    internal val selectedAlbum: SingleLifeEvent<Album> = SingleLifeEvent()
    internal var selectedSong: Song? = null
    internal val selectedGenre: SingleLifeEvent<Genre> = SingleLifeEvent()
    internal val selectedPlaylist: SingleLifeEvent<Playlist> = SingleLifeEvent()
    internal val newQueue: SingleLifeEvent<InsertQueue> = SingleLifeEvent()
    internal val requestedPositionInQueue: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val swappedQueuePositions: SingleLifeEvent<Pair<Int, Int>> = SingleLifeEvent()
    internal val removedQueueIndex: SingleLifeEvent<Int> = SingleLifeEvent()
    internal val removeFromPlaylistPlayOrder: SingleLifeEvent<Int> = SingleLifeEvent()

    private var currentOrientedClassType: OrientedClassType? = null

    val isLoading: SingleLifeEvent<Boolean> = SingleLifeEvent()

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
        removeFromPlaylistPlayOrder.value = playOrder
    }
}