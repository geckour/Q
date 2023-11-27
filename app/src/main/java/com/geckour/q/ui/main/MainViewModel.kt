package com.geckour.q.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.work.WorkManager
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
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(
    private val app: App,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val db = DB.getInstance(app)
    internal val workManager = WorkManager.getInstance(app)
    internal val mediaRetrieveWorkInfoList =
        workManager.getWorkInfosForUniqueWorkLiveData(MEDIA_RETRIEVE_WORKER_NAME)

    private var isBoundService = false

    internal var isDropboxAuthOngoing = false

    internal val currentSourcePathsFlow = MutableStateFlow(emptyList<String>())
    internal val currentIndexFlow = MutableStateFlow(-1)
    internal val currentSourcePath get() = currentSourcePathsFlow.value.getOrNull(currentIndexFlow.value)
    internal val currentPlaybackPositionFlow = MutableStateFlow(0L)
    internal val currentPlaybackInfoFlow = MutableStateFlow(false to Player.STATE_IDLE)
    internal val currentRepeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    internal val equalizerStateFlow = MutableStateFlow(false)

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

    internal val loading = MutableStateFlow<Pair<Boolean, (() -> Unit)?>>(false to null)
    internal var isSearchViewOpened = false

    private var searchJob: Job = Job()

    internal lateinit var searchQueryListener: SearchQueryListener

    private var onSubmitQueue: ((queueInfo: QueueInfo) -> Unit)? = null
    private var onMoveQueuePosition: ((from: Int, to: Int) -> Unit)? = null
    private var onRemoveQueueByIndex: ((index: Int) -> Unit)? = null
    internal var onRemoveQueueByTrack: ((track: DomainTrack) -> Unit)? = null
    private var onShuffleQueue: ((type: ShuffleActionType?) -> Unit)? = null
    private var onResetQueueOrder: (() -> Unit)? = null
    private var onClearQueue: ((keepCurrentIfPlaying: Boolean) -> Unit)? = null
    private var onRotateRepeatMode: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onResetQueuePosition: ((position: Int) -> Unit)? = null
    private var onSeek: ((progress: Long) -> Unit)? = null
    private var onNewMediaButton: ((event: KeyEvent) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        private var loadStateJob: Job? = null
        private var sourcePathsJob: Job? = null
        private var currentIndexJob: Job? = null
        private var playbackPositionJob: Job? = null
        private var playbackInfoJob: Job? = null
        private var currentRepeatModeJob: Job? = null
        private var equalizerStateJob: Job? = null
        private var onDestroyJob: Job? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(app, PlayerService::class.java)) {
                isBoundService = true

                val playerService = (service as? PlayerService.PlayerBinder)?.service ?: return

                loadStateJob = viewModelScope.launch {
                    playerService.loadStateFlow.collect { (loading, onAbort) ->
                        onLoadStateChanged(loading, onAbort)
                    }
                }
                sourcePathsJob = viewModelScope.launch {
                    playerService.sourcePathsFlow.collect {
                        currentSourcePathsFlow.value = it
                    }
                }
                currentIndexJob = viewModelScope.launch {
                    playerService.currentIndexFlow.collect {
                        currentIndexFlow.value = it
                    }
                }
                playbackPositionJob = viewModelScope.launch {
                    playerService.playbackPositionFLow.collect {
                        currentPlaybackPositionFlow.value = it
                    }
                }
                playbackInfoJob = viewModelScope.launch {
                    playerService.playbackInfoFlow.collect {
                        currentPlaybackInfoFlow.value = it
                    }
                }
                currentRepeatModeJob = viewModelScope.launch {
                    playerService.repeatModeFlow.collect {
                        currentRepeatModeFlow.value = it
                    }
                }
                equalizerStateJob = viewModelScope.launch {
                    playerService.equalizerStateFlow.collect {
                        equalizerStateFlow.value = it
                    }
                }
                onDestroyJob = viewModelScope.launch {
                    playerService.onDestroyFlow.collect { onPlayerDestroyed() }
                }

                onSubmitQueue = {
                    viewModelScope.launch {
                        playerService.submitQueue(it)
                    }
                }
                onMoveQueuePosition = { from, to ->
                    playerService.moveQueuePosition(from, to)
                }
                onRemoveQueueByIndex = {
                    playerService.removeQueue(it)
                }
                onRemoveQueueByTrack = {
                    playerService.removeQueue(it)
                }
                onShuffleQueue = {
                    playerService.shuffle(it)
                }
                onResetQueueOrder = {
                    playerService.resetQueueOrder()
                }
                onClearQueue = {
                    playerService.clear(it)
                }
                onRotateRepeatMode = {
                    playerService.rotateRepeatMode()
                }
                onPause = {
                    playerService.pause()
                }
                onResetQueuePosition = { position ->
                    playerService.resetQueuePosition(position)
                }
                onSeek = {
                    playerService.seek(it)
                }
                onNewMediaButton = {
                    playerService.onMediaButtonEvent(it)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(app, PlayerService::class.java)) {
                cancelJobs()
                onPlayerDestroyed()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(app, PlayerService::class.java)) {
                cancelJobs()
                onPlayerDestroyed()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(app, PlayerService::class.java)) {
                cancelJobs()
                onPlayerDestroyed()
            }
        }

        private fun cancelJobs() {
            loadStateJob?.cancel()
            sourcePathsJob?.cancel()
            currentIndexJob?.cancel()
            playbackPositionJob?.cancel()
            playbackInfoJob?.cancel()
            currentRepeatModeJob?.cancel()
            equalizerStateJob?.cancel()
            onDestroyJob?.cancel()
        }
    }

    init {
        bindPlayer()
    }

    override fun onCleared() {
        unbindPlayer()
        PlayerService.destroy(app)

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
            onSubmitQueue?.invoke(QueueInfo(QueueMetadata(actionType, classType), domainTracks))
        }
    }

    fun onQueueMove(from: Int, to: Int) {
        onMoveQueuePosition?.invoke(from, to)
    }

    fun onQueueRemove(index: Int) {
        onRemoveQueueByIndex?.invoke(index)
    }

    fun onToolbarClick() {
        scrollToTop.postValue(Unit)
    }

    fun onClickShuffleButton() {
        onShuffleQueue?.invoke(null)
    }

    fun onLongClickShuffleButton(): Boolean {
        val binding = DialogShuffleMenuBinding.inflate(LayoutInflater.from(app))
        val dialog = AlertDialog.Builder(app)
            .setView(binding.root)
            .setCancelable(true)
            .show()

        binding.apply {
            choiceReset.setOnClickListener {
                onResetQueueOrder?.invoke()
                dialog.dismiss()
            }
            choiceShuffleOrientedAlbum.setOnClickListener {
                onShuffleQueue?.invoke(ShuffleActionType.SHUFFLE_ALBUM_ORIENTED)
                dialog.dismiss()
            }
            choiceShuffleOrientedArtist.setOnClickListener {
                onShuffleQueue?.invoke(ShuffleActionType.SHUFFLE_ARTIST_ORIENTED)
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
        onClearQueue?.invoke(true)
    }

    fun onClickRepeatButton() {
        onRotateRepeatMode?.invoke()
    }

    internal fun initSearchQueryListener(searchView: SearchView) {
        searchQueryListener = SearchQueryListener(searchView)
    }

    private fun bindPlayer() {
        if (isBoundService.not()) {
            app.bindService(
                PlayerService.createIntent(app),
                serviceConnection,
                Context.BIND_AUTO_CREATE
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

    internal fun onPlayerDestroyed() {
        isBoundService = false
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
            onRemoveQueueByTrack?.invoke(domainTrack)

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
        onResetQueuePosition?.invoke(position)
    }

    internal fun onNewSeekBarProgress(progress: Long) {
        onSeek?.invoke(progress)
    }

    internal fun onNewPlaybackButton(playbackButton: PlaybackButton) {
        onNewMediaButton?.invoke(
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
            isSearchViewOpened = newText.isNullOrBlank()
            return true
        }

        fun reset() {
            searchView.setQuery(null, false)
            searchView.onActionViewCollapsed()
            isSearchViewOpened = false
        }
    }
}