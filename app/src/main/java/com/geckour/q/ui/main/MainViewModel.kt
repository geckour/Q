package com.geckour.q.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.geckour.q.domain.model.SearchCategory
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.service.PlayerService
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.QueueInfo
import com.geckour.q.util.QueueMetadata
import com.geckour.q.util.ShuffleActionType
import com.geckour.q.util.dropboxCredential
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.obtainDbxClient
import com.geckour.q.util.searchAlbumByFuzzyTitle
import com.geckour.q.util.searchArtistByFuzzyTitle
import com.geckour.q.util.searchTrackByFuzzyTitle
import com.geckour.q.util.toDomainTrack
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(
    private val app: App,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val db = DB.getInstance(app)

    internal val player: MutableLiveData<PlayerService> = MutableLiveData()

    private var isBoundService = false

    internal var isDropboxAuthOngoing = false

    internal val currentFragmentId = MutableLiveData<Int>()
    internal val selectedAlbum = MutableLiveData<Album?>()
    internal val selectedArtist = MutableLiveData<Artist?>()
    internal val selectedGenre = MutableLiveData<Genre?>()

    internal val trackToDelete = MutableLiveData<DomainTrack>()

    internal val searchItems = MutableLiveData<List<SearchItem>>()

    internal val scrollToTop = MutableLiveData<Unit>()
    internal val forceLoad = MutableLiveData<Unit>()

    private val dropboxItemListChannel =
        Channel<Pair<String, List<FolderMetadata>>>(capacity = Channel.CONFLATED)
    internal val dropboxItemList = dropboxItemListChannel.receiveAsFlow().distinctUntilChanged()

    internal var syncing = false
    internal val loading = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)
    internal var isSearchViewOpened = false

    private var searchJob: Job = Job()

    internal lateinit var searchQueryListener: SearchQueryListener

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(app, PlayerService::class.java)) {
                isBoundService = true

                val playerService = (service as? PlayerService.PlayerBinder)?.service ?: return

                viewModelScope.launch {
                    playerService.loadStateFlow.collectLatest { (loading, onAbort) ->
                        onLoadStateChanged(loading, onAbort)
                    }
                }
                viewModelScope.launch {
                    playerService.onDestroyFlow.collectLatest { onDestroyPlayer() }
                }

                player.value = playerService
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(app, PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(app, PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(app, PlayerService::class.java)) {
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
    }

    fun onRequestNavigate(album: Album) {
        clearSelections()
        selectedAlbum.value = album
    }

    fun onRequestNavigate() {
        clearSelections()
    }

    fun onRequestNavigate(genre: Genre) {
        clearSelections()
        selectedGenre.value = genre
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

    fun onToolbarClick() {
        scrollToTop.postValue(Unit)
    }

    fun onClickShuffleButton() {
        player.value?.shuffle()
    }

    fun onLongClickShuffleButton(): Boolean {
        val binding = DialogShuffleMenuBinding.inflate(LayoutInflater.from(app))
        val dialog = AlertDialog.Builder(app)
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
        FirebaseAnalytics.getInstance(app)
            .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped today's track")
            })

        domainTrack?.let { onNewQueue(listOf(it), InsertActionType.NEXT, OrientedClassType.TRACK) }
    }

    fun onEasterLongTapped(domainTrack: DomainTrack?, anchorView: View): Boolean {
        PopupMenu(app, anchorView, Gravity.BOTTOM).apply {
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
            app.bindService(
                PlayerService.createIntent(app), serviceConnection, Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun unbindPlayer() {
        try {
            app.startService(PlayerService.createIntent(app))
        } catch (t: Throwable) {
            Timber.e(t)
        }
        if (isBoundService) app.unbindService(serviceConnection)
    }

    internal fun onDestroyPlayer() {
        isBoundService = false
    }

    internal fun rebootPlayer() {
        player.value?.pause()
        unbindPlayer()
        bindPlayer()
    }

    internal fun search(query: String?) {
        if (query.isNullOrBlank()) {
            searchItems.value = emptyList()
            return
        }

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
                        app.getString(R.string.search_category_track),
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
                        app.getString(R.string.search_category_album),
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
                        app.getString(R.string.search_category_artist),
                        SearchCategory(),
                        SearchItem.SearchItemType.CATEGORY
                    )
                )
                items.addAll(artists)
            }

            val genres = db.trackDao().getAllGenreByName(query)
                .take(3)
                .map {
                    val totalDuration =
                        db.trackDao().getAllByGenreName(it).sumOf { it.track.duration }
                    SearchItem(it, Genre(null, it, totalDuration), SearchItem.SearchItemType.GENRE)
                }
            if (genres.isNotEmpty()) {
                items.add(
                    SearchItem(
                        app.getString(R.string.search_category_genre),
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
        selectedGenre.value = null
    }

    internal fun checkDBIsEmpty(onEmpty: () -> Unit) {
        viewModelScope.launch {
            val trackCount = db.trackDao().count()
            if (trackCount == 0) onEmpty()
        }
    }

    internal fun deleteTrack(domainTrack: DomainTrack) {
        viewModelScope.launch {
            trackToDelete.value = domainTrack
            player.value?.removeQueue(domainTrack)

            db.trackDao().deleteIncludingRootIfEmpty(db, domainTrack.id)
        }
    }

    internal fun onTrackMenuAction(
        actionType: InsertActionType, album: Album
    ) {
        viewModelScope.launch {
            var enabled = true
            loading.emit(true to { enabled = false })
            val tracks = db.trackDao()
                .getAllByAlbum(
                    album.id, BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                )
                .map {
                    if (enabled.not()) return@launch
                    it.toDomainTrack()
                }
                .apply { loading.emit(false to null) }

            onNewQueue(tracks, actionType, OrientedClassType.TRACK)
        }
    }

    internal fun onLoadStateChanged(state: Boolean, onAbort: (() -> Unit)? = null) {
        viewModelScope.launch { loading.emit(state to onAbort) }
    }

    internal fun onChangeRequestedPositionInQueue(position: Int) {
        player.value?.resetQueuePosition(position)
    }

    internal fun onNewSeekBarProgress(progress: Long) {
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
        val credential = Auth.getDbxCredential() ?: return
        sharedPreferences.dropboxCredential = credential.toString()
        showDropboxFolderChooser()
    }

    internal fun showDropboxFolderChooser(dropboxMetadata: Metadata? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            val client = obtainDbxClient(sharedPreferences) ?: return@launch
            var result = client.files().listFolder(dropboxMetadata?.pathLower.orEmpty())
            while (true) {
                if (result.hasMore.not()) break

                result = client.files().listFolderContinue(result.cursor)
            }
            val currentDirTitle = (dropboxMetadata?.name ?: "Root")
            dropboxItemListChannel.send(currentDirTitle to result.entries.filterIsInstance<FolderMetadata>())
        }

    inner class SearchQueryListener(private val searchView: SearchView) :
        SearchView.OnQueryTextListener {

        override fun onQueryTextSubmit(query: String?): Boolean {
            search(query)
            searchView.clearFocus()
            isSearchViewOpened = true
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            search(newText)
            isSearchViewOpened = true
            return true
        }

        fun reset() {
            searchView.setQuery(null, false)
            searchView.onActionViewCollapsed()
        }
    }
}