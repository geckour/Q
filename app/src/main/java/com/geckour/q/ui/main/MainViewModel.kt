package com.geckour.q.ui.main

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.SearchView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.geckour.q.App
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.databinding.DialogShuffleMenuBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.service.LocalMediaRetrieveService
import com.geckour.q.service.PlayerService
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.dropboxToken
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchGenreByFuzzyTitle
import com.geckour.q.util.searchPlaylistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainTrack
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(application: Application) : AndroidViewModel(application) {

    internal var player: MutableLiveData<PlayerService> = MutableLiveData()

    private var isBoundService = false

    internal var isDropboxAuthOngoing = false

    internal val currentFragmentId = MutableLiveData<Int>()
    internal var selectedDomainTrack: DomainTrack? = null
    internal val selectedAlbum = MutableLiveData<Album>()
    internal val selectedArtist = MutableLiveData<Artist>()
    internal val selectedGenre = MutableLiveData<Genre>()
    internal val selectedPlaylist = MutableLiveData<Playlist>()

    internal val playOrderToRemoveFromPlaylist = MutableLiveData<Int>()
    internal val trackToDelete = MutableLiveData<DomainTrack>()

    internal val searchItems = MutableLiveData<List<SearchItem>>()

    internal val scrollToTop = MutableLiveData<Unit>()
    internal val forceLoad = MutableLiveData<Unit>()

    internal val dropboxItemList = MutableLiveData<Pair<String, List<FolderMetadata>>>()

    private var currentOrientedClassType: OrientedClassType? = null

    internal var syncing = false
    internal val loading = MutableStateFlow(false)
    internal var isSearchViewOpened = false

    private var searchJob: Job = Job()

    internal lateinit var searchQueryListener: SearchQueryListener

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(getApplication(), PlayerService::class.java)) {
                isBoundService = true

                val playerService = (service as? PlayerService.PlayerBinder)?.service

                viewModelScope.launch {
                    playerService?.loadStateFlow?.collectLatest { onLoadStateChanged(it) }
                }
                viewModelScope.launch {
                    playerService?.onDestroyFlow?.collectLatest { onDestroyPlayer() }
                }

                player.value = playerService
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
        unbindPlayer()

        super.onCleared()
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

    fun onRequestNavigate(domainTrack: DomainTrack) {
        clearSelections()
        selectedDomainTrack = domainTrack
        currentOrientedClassType = OrientedClassType.TRACK
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

    fun onNewQueue(
        domainTracks: List<DomainTrack>,
        actionType: InsertActionType,
        classType: OrientedClassType
    ) {
        viewModelScope.launch {
            player.value?.submitQueue(QueueInfo(QueueMetadata(actionType, classType), domainTracks))
        }
    }

    fun onQueueMove(from: Int, to: Int) {
        player.value?.moveQueuePosition(from, to)
    }

    fun onQueueRemove(index: Int) {
        player.value?.removeQueue(index)
    }

    fun onRequestRemoveTrackFromPlaylist(playOrder: Int) {
        playOrderToRemoveFromPlaylist.value = playOrder
    }

    fun onCancelSync(context: Context) {
        LocalMediaRetrieveService.cancel(context)
    }

    fun onToolbarClick() {
        scrollToTop.postValue(Unit)
    }

    fun onClickShuffleButton() {
        player.value?.shuffle()
    }

    fun onLongClickShuffleButton(): Boolean {
        val binding = DialogShuffleMenuBinding.inflate(LayoutInflater.from(getApplication()))
        val dialog = androidx.appcompat.app.AlertDialog.Builder(getApplication())
            .setView(binding.root)
            .setCancelable(true)
            .show()

        binding.apply {
            choiceReset.setOnClickListener {
                player.value?.resetQueueOrder()
                dialog.dismiss()
            }
            choiceShuffleOrientedAlbum.setOnClickListener {
                player.value?.shuffle(ShuffleActionType.SHUFFLE_ALBUM_ORIENTED)
                dialog.dismiss()
            }
            choiceShuffleOrientedArtist.setOnClickListener {
                player.value?.shuffle(ShuffleActionType.SHUFFLE_ARTIST_ORIENTED)
                dialog.dismiss()
            }
        }
        return true
    }

    fun onPlayOrPause(playing: Boolean?) {
        onNewPlaybackButton(if (playing == true) PlaybackButton.PAUSE else PlaybackButton.PLAY)
    }

    fun onNext() {
        onNewPlaybackButton(PlaybackButton.NEXT)
    }

    fun onPrev() {
        onNewPlaybackButton(PlaybackButton.PREV)
    }

    fun onFF(): Boolean {
        onNewPlaybackButton(PlaybackButton.FF)
        return true
    }

    fun onRewind(): Boolean {
        onNewPlaybackButton(PlaybackButton.REWIND)
        return true
    }

    fun onClickClearQueueButton() {
        player.value?.clear(true)
    }

    fun onClickRepeatButton() {
        player.value?.rotateRepeatMode()
    }

    fun onEasterTapped(domainTrack: DomainTrack?) {
        FirebaseAnalytics.getInstance(getApplication())
            .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped today's track")
            })

        domainTrack?.let { onNewQueue(listOf(it), InsertActionType.NEXT, OrientedClassType.TRACK) }
    }

    fun onEasterLongTapped(domainTrack: DomainTrack?, anchorView: View): Boolean {
        PopupMenu(getApplication(), anchorView, Gravity.BOTTOM).apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_transition_to_artist -> {
                        selectedArtist.value = domainTrack?.artist
                        return@setOnMenuItemClickListener true
                    }
                    R.id.menu_transition_to_album -> {
                        selectedAlbum.value = domainTrack?.album
                        return@setOnMenuItemClickListener true
                    }
                }
                return@setOnMenuItemClickListener false
            }
            inflate(R.menu.track_transition)
        }.show()
        return true
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
        val app = getApplication<App>()
        try {
            app.startService(PlayerService.createIntent(getApplication()))
        } catch (t: Throwable) {
            Timber.e(t)
        }
        if (isBoundService) app.unbindService(serviceConnection)
    }

    internal fun onDestroyPlayer() {
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
        searchJob = viewModelScope.launch {
            val items = mutableListOf<SearchItem>()

            val tracks = db.searchTrackByFuzzyTitle(query).take(3).map {
                SearchItem(
                    it.track.title,
                    it.toDomainTrack(),
                    SearchItem.SearchItemType.TRACK
                )
            }
            if (tracks.isNotEmpty()) {
                items.add(
                    SearchItem(
                        context.getString(R.string.search_category_track),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(tracks)
            }

            val albums = db.searchAlbumByFuzzyTitle(query).take(3).map {
                SearchItem(it.album.title, it.album, SearchItem.SearchItemType.ALBUM)
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
                SearchItem(it.title, it, SearchItem.SearchItemType.ARTIST)
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

    private fun clearSelections() {
        selectedArtist.value = null
        selectedAlbum.value = null
        selectedDomainTrack = null
        selectedGenre.value = null
        selectedPlaylist.value = null
    }

    internal fun checkDBIsEmpty(onEmpty: () -> Unit) {
        viewModelScope.launch {
            val trackCount = DB.getInstance(this@MainViewModel.getApplication()).trackDao().count()
            if (trackCount == 0) onEmpty()
        }
    }

    internal fun deleteTrack(domainTrack: DomainTrack) {
        viewModelScope.launch {
            trackToDelete.value = domainTrack
            player.value?.removeQueue(domainTrack.id)

            DB.getInstance(getApplication())
                .trackDao()
                .deleteIncludingRootIfEmpty(getApplication(), domainTrack.id)
        }
    }

    internal fun onTrackMenuAction(
        actionType: InsertActionType, album: Album, sortByTrackOrder: Boolean
    ) {
        viewModelScope.launch {
            val tracks = DB.getInstance(getApplication()).let { db ->
                val sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getApplication())
                loading.emit(true)
                db.trackDao()
                    .getAllByAlbum(
                        album.id, BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                    )
                    .map { it.toDomainTrack() }
                    .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                    .apply { loading.emit(false) }
            }

            onNewQueue(tracks, actionType, OrientedClassType.TRACK)
        }
    }

    internal fun onLoadStateChanged(state: Boolean) {
        viewModelScope.launch { loading.emit(state) }
    }

    internal fun onChangeRequestedPositionInQueue(position: Int) {
        player.value?.play(position)
    }

    internal fun onNewSeekBarProgress(progress: Float) {
        player.value?.seek(progress)
    }

    internal fun onNewPlaybackButton(playbackButton: PlaybackButton) {
        player.value?.onMediaButtonEvent(
            KeyEvent(
                if (playbackButton == PlaybackButton.UNDEFINED) KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN,
                when (playbackButton) {
                    PlaybackButton.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                    PlaybackButton.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    PlaybackButton.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    PlaybackButton.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    PlaybackButton.FF -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    PlaybackButton.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
                    PlaybackButton.UNDEFINED -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
            )
        )
    }

    internal fun storeDropboxApiToken() {
        val token = Auth.getOAuth2Token() ?: return
        PreferenceManager.getDefaultSharedPreferences(getApplication()).dropboxToken = token
        showDropboxFolderChooser()
    }

    internal fun showDropboxFolderChooser(dropboxMetadata: Metadata? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            val client = getApplication<App>().obtainDbxClient()
            var result = client.files().listFolder(dropboxMetadata?.pathLower.orEmpty())
            while (true) {
                if (result.hasMore.not()) break

                result = client.files().listFolderContinue(result.cursor)
            }
            val currentDirTitle = (dropboxMetadata?.name ?: "Root")
            dropboxItemList.postValue(
                currentDirTitle to result.entries.filterIsInstance<FolderMetadata>()
            )
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