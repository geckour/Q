package com.geckour.q.ui.main

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Bool
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.Song
import com.geckour.q.service.MediaRetrieveService
import com.geckour.q.service.PlayerService
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.SingleLiveEvent
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getSong
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchGenreByFuzzyTitle
import com.geckour.q.util.searchPlaylistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.toDomainModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainViewModel(
        application: Application
) : AndroidViewModel(application) {

    internal var player: MutableLiveData<PlayerService> = MutableLiveData()

    private var isBoundService = false

    internal val currentFragmentId: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val selectedArtist: SingleLiveEvent<Artist> = SingleLiveEvent()
    internal val selectedAlbum: SingleLiveEvent<Album> = SingleLiveEvent()
    internal var selectedSong: Song? = null
    internal val selectedGenre: SingleLiveEvent<Genre> = SingleLiveEvent()
    internal val selectedPlaylist: SingleLiveEvent<Playlist> = SingleLiveEvent()

    internal val dbEmpty: SingleLiveEvent<Unit> = SingleLiveEvent()

    internal val newQueueInfo: SingleLiveEvent<QueueInfo> = SingleLiveEvent()

    internal val requestedPositionInQueue: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val swappedQueuePositions: SingleLiveEvent<Pair<Int, Int>> = SingleLiveEvent()
    internal val removedQueueIndex: SingleLiveEvent<Int> = SingleLiveEvent()

    internal val toRemovePlayOrderOfPlaylist: SingleLiveEvent<Int> = SingleLiveEvent()
    internal val songToDelete: SingleLiveEvent<Song> = SingleLiveEvent()

    internal val deletedSongId: SingleLiveEvent<Long> = SingleLiveEvent()

    internal val searchItems: SingleLiveEvent<List<SearchItem>> = SingleLiveEvent()

    internal val scrollToTop: SingleLiveEvent<Unit> = SingleLiveEvent()

    private var currentOrientedClassType: OrientedClassType? = null

    internal var syncing = false
    val loading: SingleLiveEvent<Boolean> = SingleLiveEvent()

    private var searchJob = Job()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                isBoundService = true
                player.value = (service as? PlayerService.PlayerBinder)?.service
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }
    }

    init {
        bindPlayer()
    }

    override fun onCleared() {
        super.onCleared()

        try {
            getApplication<App>().startService(PlayerService.createIntent(getApplication()))
        } catch (t: Throwable) {
            Timber.e(t)
        }
        player.value?.onRequestedStopService()
        unbindPlayer()
    }

    private fun bindPlayer() {
        if (isBoundService.not()) {
            val app = getApplication<App>()
            app.bindService(
                    PlayerService.createIntent(app), serviceConnection, Context.BIND_AUTO_CREATE
            )
            isBoundService = true
        }
    }

    private fun unbindPlayer() {
        if (isBoundService) {
            getApplication<App>().unbindService(serviceConnection)
            isBoundService = false
        }
    }

    internal fun onDestroyPlayer() {
        player.value = null
    }

    internal fun rebootPlayer() {
        player.value?.pause()
        player.value?.onRequestedStopService()
        unbindPlayer()
        bindPlayer()
    }

    internal fun search(context: Context, query: String?) {
        if (query.isNullOrBlank()) {
            searchItems.call()
            return
        }

        val db = DB.getInstance(getApplication())
        searchJob.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<SearchItem>()

            val tracks = db.searchTrackByFuzzyTitle(query)
                    .take(3)
                    .mapNotNull {
                        SearchItem(
                                it.title ?: UNKNOWN,
                                getSong(db, it) ?: return@mapNotNull null,
                                SearchItem.SearchItemType.TRACK
                        )
                    }
            if (tracks.isNotEmpty()) {
                items.add(SearchItem(
                        context.getString(R.string.search_category_song),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                ))
                items.addAll(tracks)
            }

            val albums = db.searchAlbumByFuzzyTitle(query)
                    .take(3)
                    .map {
                        SearchItem(
                                it.title ?: UNKNOWN,
                                it.toDomainModel(),
                                SearchItem.SearchItemType.ALBUM
                        )
                    }
            if (albums.isNotEmpty()) {
                items.add(SearchItem(
                        context.getString(R.string.search_category_album),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                ))
                items.addAll(albums)
            }

            val artists = db.searchArtistByFuzzyTitle(query)
                    .take(3)
                    .map {
                        SearchItem(
                                it.title ?: UNKNOWN,
                                it.toDomainModel(),
                                SearchItem.SearchItemType.ARTIST
                        )
                    }
            if (artists.isNotEmpty()) {
                items.add(SearchItem(
                        context.getString(R.string.search_category_artist),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                ))
                items.addAll(artists)
            }

            val playlists = getApplication<App>().searchPlaylistByFuzzyTitle(query)
                    .take(3)
                    .map { SearchItem(it.name, it, SearchItem.SearchItemType.PLAYLIST) }
            if (playlists.isNotEmpty()) {
                items.add(SearchItem(
                        context.getString(R.string.search_category_playlist),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                ))
                items.addAll(playlists)
            }

            val genres = getApplication<App>().searchGenreByFuzzyTitle(query)
                    .take(3)
                    .map { SearchItem(it.name, it, SearchItem.SearchItemType.GENRE) }
            if (genres.isNotEmpty()) {
                items.add(SearchItem(
                        context.getString(R.string.search_category_genre),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                ))
                items.addAll(genres)
            }

            withContext(Dispatchers.Main) { searchItems.value = items }
        }
    }

    fun onRequestNavigate(artist: Artist) {
        clearSelections()
        selectedArtist.value = artist
        currentOrientedClassType = OrientedClassType.ARTIST
    }

    fun onRequestNavigate(album: Album) {
        clearSelections()
        selectedAlbum.value = album
        currentOrientedClassType = OrientedClassType.ALBUM
    }

    fun onRequestNavigate(song: Song) {
        clearSelections()
        selectedSong = song
        currentOrientedClassType = OrientedClassType.SONG
    }

    fun onRequestNavigate(genre: Genre) {
        clearSelections()
        selectedGenre.value = genre
        currentOrientedClassType = OrientedClassType.GENRE
    }

    fun onRequestNavigate(playlist: Playlist) {
        clearSelections()
        selectedPlaylist.value = playlist
        currentOrientedClassType = OrientedClassType.PLAYLIST
    }

    private fun clearSelections() {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedSong = null
        selectedGenre.value = null
        selectedPlaylist.value = null
    }

    internal fun checkDBIsEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            if (DB.getInstance(this@MainViewModel.getApplication()).trackDao().count() == 0) withContext(
                    Dispatchers.Main
            ) { dbEmpty.call() }
        }
    }

    internal fun deleteSongFromDB(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DB.getInstance(this@MainViewModel.getApplication())
            val track = db.trackDao().get(song.id) ?: return@launch

            player.value?.removeQueue(track.id)

            val deleted = db.trackDao().delete(track.id) > 0
            if (deleted) {
                withContext(Dispatchers.Main) {
                    deletedSongId.value = track.id
                }
            }
            if (db.trackDao().findByAlbum(track.albumId, Bool.UNDEFINED).isEmpty()) {
                db.albumDao().delete(track.albumId)
            }
            if (db.trackDao().findByArtist(track.artistId, Bool.UNDEFINED).isEmpty()) {
                db.artistDao().delete(track.artistId)
            }
        }
    }

    fun onNewQueue(songs: List<Song>, actionType: InsertActionType, classType: OrientedClassType) {
        newQueueInfo.value = QueueInfo(
                QueueMetadata(actionType, classType), songs
        )
    }

    fun onQueueSwap(from: Int, to: Int) {
        swappedQueuePositions.value = Pair(from, to)
    }

    fun onQueueRemove(index: Int) {
        removedQueueIndex.value = index
    }

    fun onRequestRemoveSongFromPlaylist(playOrder: Int) {
        toRemovePlayOrderOfPlaylist.value = playOrder
    }

    fun onCancelSync(context: Context) {
        MediaRetrieveService.cancel(context)
    }

    fun onToolbarClick() {
        scrollToTop.call()
    }
}