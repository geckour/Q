package com.geckour.q.presentation.main

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.SearchView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.Song
import com.geckour.q.service.MediaRetrieveService
import com.geckour.q.service.PlayerService
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.getSong
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchGenreByFuzzyTitle
import com.geckour.q.util.searchPlaylistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainViewModel(application: Application) : AndroidViewModel(application) {

    internal var player: MutableLiveData<PlayerService> = MutableLiveData()

    private var isBoundService = false

    internal val currentFragmentId = MutableLiveData<Int>()
    internal var selectedSong: Song? = null
    internal val selectedAlbum = MutableLiveData<Album>()
    internal val selectedArtist = MutableLiveData<Artist>()
    internal val selectedGenre = MutableLiveData<Genre>()
    internal val selectedPlaylist = MutableLiveData<Playlist>()

    internal val newQueueInfo = MutableLiveData<QueueInfo>()
    internal val requestedPositionInQueue = MutableLiveData<Int>()
    internal val swappedQueuePositions = MutableLiveData<Pair<Int, Int>>()
    internal val removedQueueIndex = MutableLiveData<Int>()

    internal val toRemovePlayOrderOfPlaylist = MutableLiveData<Int>()
    internal val songToDelete = MutableLiveData<Song>()

    internal val deletedSongId = MutableLiveData<Long>()

    internal val searchItems = MutableLiveData<List<SearchItem>>()

    internal val scrollToTop = MutableLiveData<Unit>()
    internal val forceLoad = MutableLiveData<Unit>()

    private var currentOrientedClassType: OrientedClassType? = null

    internal var syncing = false
    internal val loading = MutableLiveData<Boolean>()
    internal var isSearchViewOpened = false

    private var searchJob: Job = Job()

    internal lateinit var searchQueryListener: SearchQueryListener

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                isBoundService = true
                player.value = (service as? PlayerService.PlayerBinder)?.service
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                onDestroyedPlayer()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                onDestroyedPlayer()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                onDestroyedPlayer()
            }
        }
    }

    init {
        bindPlayer()
    }

    override fun onCleared() {
        super.onCleared()

        unbindPlayer()
    }

    internal fun initSearchQueryListener(searchView: SearchView) {
        searchQueryListener = SearchQueryListener(searchView)
    }

    private fun bindPlayer() {
        if (isBoundService.not()) {
            val app = getApplication<App>()
            app.bindService(
                PlayerService.createIntent(app), serviceConnection, Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun unbindPlayer() {
        try {
            getApplication<App>().startService(PlayerService.createIntent(getApplication()))
        } catch (t: Throwable) {
            Timber.e(t)
        }
        if (isBoundService) {
            getApplication<App>().unbindService(serviceConnection)
        }
    }

    internal fun onDestroyedPlayer() {
        isBoundService = false
        player.value = null
    }

    internal fun rebootPlayer() {
        player.value?.pause()
        unbindPlayer()
        bindPlayer()
    }

    internal fun search(context: Context, query: String?) {
        if (query.isNullOrBlank()) {
            searchItems.value = null
            return
        }

        val db = DB.getInstance(getApplication())
        searchJob.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<SearchItem>()

            val tracks = db.searchTrackByFuzzyTitle(query).take(3).mapNotNull {
                SearchItem(
                    it.title,
                    getSong(db, it) ?: return@mapNotNull null,
                    SearchItem.SearchItemType.TRACK
                )
            }
            if (tracks.isNotEmpty()) {
                items.add(
                    SearchItem(
                        context.getString(R.string.search_category_song),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(tracks)
            }

            val albums = db.searchAlbumByFuzzyTitle(query).take(3).map {
                SearchItem(it.title, it.toDomainModel(), SearchItem.SearchItemType.ALBUM)
            }
            if (albums.isNotEmpty()) {
                items.add(
                    SearchItem(
                        context.getString(R.string.search_category_album),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(albums)
            }

            val artists = db.searchArtistByFuzzyTitle(query).take(3).map {
                SearchItem(it.title, it.toDomainModel(), SearchItem.SearchItemType.ARTIST)
            }
            if (artists.isNotEmpty()) {
                items.add(
                    SearchItem(
                        context.getString(R.string.search_category_artist),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(artists)
            }

            val playlists = getApplication<App>().searchPlaylistByFuzzyTitle(query)
                .take(3)
                .map { SearchItem(it.name, it, SearchItem.SearchItemType.PLAYLIST) }
            if (playlists.isNotEmpty()) {
                items.add(
                    SearchItem(
                        context.getString(R.string.search_category_playlist),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(playlists)
            }

            val genres = getApplication<App>().searchGenreByFuzzyTitle(query)
                .take(3)
                .map { SearchItem(it.name, it, SearchItem.SearchItemType.GENRE) }
            if (genres.isNotEmpty()) {
                items.add(
                    SearchItem(
                        context.getString(R.string.search_category_genre),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(genres)
            }

            searchItems.postValue(items)
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

    internal fun checkDBIsEmpty(onEmpty: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val trackCount = DB.getInstance(this@MainViewModel.getApplication()).trackDao().count()
            if (trackCount == 0) {
                withContext(Dispatchers.Main) { onEmpty() }
            }
        }
    }

    internal fun deleteSongFromDB(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                player.value?.removeQueue(song.id)
            }

            DB.getInstance(getApplication()).trackDao()
                .deleteIncludingRootIfEmpty(getApplication(), song.id)
        }
    }

    internal fun onSongMenuAction(
        actionType: InsertActionType,
        album: Album,
        sortByTrackOrder: Boolean
    ) {
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) {
                DB.getInstance(getApplication()).let { db ->
                    val sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(getApplication())
                    loading.postValue(true)
                    db.trackDao()
                        .getAllByAlbum(
                            album.id,
                            BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                        )
                        .mapNotNull { getSong(db, it) }
                        .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                        .apply { loading.postValue(false) }
                }
            }

            onNewQueue(songs, actionType, OrientedClassType.SONG)
        }
    }

    internal fun onLoadStateChanged(state: Boolean) {
        loading.postValue(state)
    }

    internal fun onRequestDeleteSong(song: Song) {
        songToDelete.postValue(song)
    }

    internal fun onChangeRequestedPositionInQueue(position: Int) {
        requestedPositionInQueue.postValue(position)
    }

    fun onNewQueue(songs: List<Song>, actionType: InsertActionType, classType: OrientedClassType) {
        newQueueInfo.value = QueueInfo(QueueMetadata(actionType, classType), songs)
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
        scrollToTop.postValue(Unit)
    }

    inner class SearchQueryListener(private val searchView: SearchView) :
        SearchView.OnQueryTextListener {

        override fun onQueryTextSubmit(query: String?): Boolean {
            search(searchView.context, query)
            searchView.clearFocus()
            isSearchViewOpened = true
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            search(searchView.context, newText)
            isSearchViewOpened = true
            return true
        }

        fun reset() {
            searchView.setQuery(null, false)
            searchView.onActionViewCollapsed()
        }
    }
}