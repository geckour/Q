package com.geckour.q.ui.main

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.media.session.MediaButtonReceiver
import androidx.work.*
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.databinding.DialogAddQueuePlaylistBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.RequestedTransaction
import com.geckour.q.domain.model.SearchItem
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.setCrashlytics
import com.geckour.q.ui.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.ui.easteregg.EasterEggFragment
import com.geckour.q.ui.equalizer.EqualizerFragment
import com.geckour.q.ui.equalizer.EqualizerViewModel
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.album.AlbumListViewModel
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.artist.ArtistListViewModel
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.playlist.PlaylistListFragment
import com.geckour.q.ui.library.song.SongListFragment
import com.geckour.q.ui.library.song.SongListViewModel
import com.geckour.q.ui.pay.PaymentFragment
import com.geckour.q.ui.pay.PaymentViewModel
import com.geckour.q.ui.setting.SettingActivity
import com.geckour.q.ui.sheet.BottomSheetViewModel
import com.geckour.q.util.*
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber
import java.io.File
import kotlin.coroutines.experimental.CoroutineContext

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    enum class RequestCode(val code: Int) {
        RESULT_SETTING(333)
    }

    companion object {
        private const val ACTION_PROGRESS_SYNCING = "action_progress_syncing"
        private const val EXTRA_PROGRESS_SYNCING = "extra_progress_syncing"
        private const val STATE_KEY_REQUESTED_TRANSACTION = "state_key_requested_transaction"

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

        fun createProgressIntent(progress: Pair<Int, Int>) = Intent(ACTION_PROGRESS_SYNCING).apply {
            putExtra(EXTRA_PROGRESS_SYNCING, progress)
        }
    }

    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(this)[MainViewModel::class.java]
    }
    private val bottomSheetViewModel: BottomSheetViewModel by lazy {
        ViewModelProviders.of(this)[BottomSheetViewModel::class.java]
    }
    private val artistListViewModel: ArtistListViewModel by lazy {
        ViewModelProviders.of(this)[ArtistListViewModel::class.java]
    }
    private val albumListViewModel: AlbumListViewModel by lazy {
        ViewModelProviders.of(this)[AlbumListViewModel::class.java]
    }
    private val songListViewModel: SongListViewModel by lazy {
        ViewModelProviders.of(this)[SongListViewModel::class.java]
    }
    private val genreListViewModel: SongListViewModel by lazy {
        ViewModelProviders.of(this)[SongListViewModel::class.java]
    }
    private val playlistListViewModel: SongListViewModel by lazy {
        ViewModelProviders.of(this)[SongListViewModel::class.java]
    }
    private val equalizerViewModel: EqualizerViewModel by lazy {
        ViewModelProviders.of(this)[EqualizerViewModel::class.java]
    }
    private val paymentViewModel: PaymentViewModel by lazy {
        ViewModelProviders.of(this)[PaymentViewModel::class.java]
    }
    internal lateinit var binding: ActivityMainBinding
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private val searchListAdapter: SearchListAdapter by lazy { SearchListAdapter(viewModel) }
    private var parentJob = Job()
    private val bgScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = parentJob
    }
    private val uiScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = Dispatchers.Main + parentJob
    }
    private var searchJob = Job()

    private var requestedTransaction: RequestedTransaction? = null
    private var paused = true

    private var player: PlayerService? = null

    private var isBoundService = false

    private val syncingProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            (intent?.extras?.get(EXTRA_PROGRESS_SYNCING) as? Pair<Int, Int>)?.apply {
                binding.coordinatorMain.indicatorLocking.progressSync.text =
                        getString(R.string.progress_sync, this.first, this.second)
            }
        }
    }
    private val equalizerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val enabled = intent.getBooleanExtra(EqualizerFragment.EXTRA_KEY_EQUALIZER_ENABLED, false)
            onReceiveEnabled(enabled)
        }
    }

    private val onNavigationItemSelected: (MenuItem) -> Boolean = {
        val fragment = when (it.itemId) {
            R.id.nav_artist -> ArtistListFragment.newInstance()
            R.id.nav_album -> AlbumListFragment.newInstance()
            R.id.nav_song -> SongListFragment.newInstance()
            R.id.nav_genre -> GenreListFragment.newInstance()
            R.id.nav_playlist -> PlaylistListFragment.newInstance()
            R.id.nav_setting -> {
                startActivityForResult(SettingActivity.createIntent(this),
                        RequestCode.RESULT_SETTING.code)
                null
            }
            R.id.nav_equalizer -> EqualizerFragment.newInstance()
            R.id.nav_sync -> {
                FirebaseAnalytics.getInstance(this)
                        .logEvent(
                                FirebaseAnalytics.Event.SELECT_CONTENT,
                                Bundle().apply {
                                    putString(FirebaseAnalytics.Param.ITEM_NAME, "Invoked force sync")
                                }
                        )

                retrieveMediaWithPermissionCheck()
                null
            }
            R.id.nav_pay -> PaymentFragment.newInstance()
            else -> null
        }
        if (fragment != null) {
            supportFragmentManager.beginTransaction()
                    .apply {
                        if ((binding.coordinatorMain.contentMain.root as FrameLayout).childCount == 0)
                            add(R.id.content_main, fragment)
                        else {
                            replace(R.id.content_main, fragment)
                            addToBackStack(null)
                        }
                    }
                    .commit()
        }
        uiScope.launch { binding.drawerLayout.closeDrawers() }
        true
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name == ComponentName(applicationContext, PlayerService::class.java)) {
                isBoundService = true
                player = (service as? PlayerService.PlayerBinder)?.service?.apply {
                    setOnQueueChangedListener {
                        bottomSheetViewModel.currentQueue.value = it
                    }
                    setOnCurrentPositionChangedListener {
                        bottomSheetViewModel.currentPosition.value = it
                    }
                    setOnPlaybackStateChangeListener { playbackState, playWhenReady ->
                        bottomSheetViewModel.playing.value = when (playbackState) {
                            Player.STATE_READY -> {
                                playWhenReady
                            }
                            else -> false
                        }
                    }
                    setOnPlaybackRatioChangedListener {
                        bottomSheetViewModel.playbackRatio.value = it
                    }
                    setOnRepeatModeChangedListener {
                        bottomSheetViewModel.repeatMode.value = it
                    }
                    setOnDestroyedListener { onDestroyPlayer() }

                    publishStatus()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (name == ComponentName(applicationContext, PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            if (name == ComponentName(applicationContext, PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            if (name == ComponentName(applicationContext, PlayerService::class.java)) {
                onDestroyPlayer()
            }
        }
    }

    private lateinit var currentAppTheme: AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCrashlytics()

        currentAppTheme = sharedPreferences.appTheme
        setTheme(when (currentAppTheme) {
            AppTheme.LIGHT -> R.style.AppTheme
            AppTheme.DARK -> R.style.AppTheme_Dark
        })

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.coordinatorMain.viewModel = viewModel
        binding.coordinatorMain.contentSearch.recyclerView.adapter = searchListAdapter

        observeEvents()
        registerReceiver(syncingProgressReceiver, IntentFilter(ACTION_PROGRESS_SYNCING))
        registerReceiver(equalizerStateReceiver,
                IntentFilter(EqualizerFragment.ACTION_EQUALIZER_STATE))

        setSupportActionBar(binding.coordinatorMain.toolbar)
        setupDrawer()

        if (savedInstanceState == null) {
            retrieveMediaIfEmpty()
            WorkManager.getInstance().observeMediaChange()

            val navId = when (PreferenceManager.getDefaultSharedPreferences(this).preferScreen) {
                Screen.ARTIST -> R.id.nav_artist
                Screen.ALBUM -> R.id.nav_album
                Screen.SONG -> R.id.nav_song
                Screen.GENRE -> R.id.nav_genre
                Screen.PLAYLIST -> R.id.nav_playlist
            }
            onNavigationItemSelected(binding.navigationView.menu.findItem(navId))
        } else if (savedInstanceState.containsKey(STATE_KEY_REQUESTED_TRANSACTION)) {
            requestedTransaction =
                    savedInstanceState.getParcelable(STATE_KEY_REQUESTED_TRANSACTION)
                            as RequestedTransaction
        }
    }

    override fun onResume() {
        super.onResume()

        paused = false

        if (player == null) {
            bottomSheetViewModel.currentQueue.value = emptyList()
            bindPlayer()
        }

        tryTransaction()
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
        startService(PlayerService.createIntent(this))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        requestedTransaction?.apply {
            outState.putParcelable(STATE_KEY_REQUESTED_TRANSACTION, this)
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.onRequestedStopService()
        unbindPlayer()
        unregisterReceiver(syncingProgressReceiver)
        unregisterReceiver(equalizerStateReceiver)
    }

    override fun onBackPressed() {
        when {
            binding.drawerLayout.isDrawerOpen(binding.navigationView) ->
                binding.drawerLayout.closeDrawer(binding.navigationView)
            bottomSheetViewModel.sheetState == BottomSheetBehavior.STATE_EXPANDED ->
                bottomSheetViewModel.toggleSheetState.call()
            else -> super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RequestCode.RESULT_SETTING.code -> {
                if (player?.getDuking() != sharedPreferences.ducking)
                    rebootPlayer()
                if (currentAppTheme != sharedPreferences.appTheme) {
                    finish()
                    startActivity(MainActivity.createIntent(this))
                }
            }
        }
    }

    private fun onReceiveEnabled(enabled: Boolean) {
        equalizerViewModel.equalizerState.value = enabled
    }

    private fun bindPlayer() {
        if (isBoundService.not()) {
            bindService(PlayerService.createIntent(this),
                    serviceConnection, Context.BIND_AUTO_CREATE)
            isBoundService = true
        }
    }

    private fun unbindPlayer() {
        if (isBoundService) {
            unbindService(serviceConnection)
            isBoundService = false
        }
    }

    private fun onDestroyPlayer() {
        player = null
    }

    private fun rebootPlayer() {
        player?.storeState()
        player?.pause()
        unbindPlayer()
        player?.onRequestedStopService()
        startService(PlayerService.createIntent(this))
        bindPlayer()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
    internal fun retrieveMedia() {
        WorkManager.getInstance().invokeRetrieveMediaWorker()
    }

    private fun retrieveMediaIfEmpty() {
        bgScope.launch {
            if (DB.getInstance(this@MainActivity).trackDao().count() == 0)
                uiScope.launch { retrieveMediaWithPermissionCheck() }
        }
    }

    private fun WorkManager.invokeRetrieveMediaWorker() {
        beginUniqueWork(MediaRetrieveWorker.WORK_NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MediaRetrieveWorker>().build()).enqueue()
        viewModel.syncing.value = true
    }

    private fun WorkManager.observeMediaChange() {
        enqueue(OneTimeWorkRequestBuilder<MediaObserveWorker>().setConstraints(Constraints().apply {
            requiredNetworkType = NetworkType.NOT_REQUIRED
            contentUriTriggers = ContentUriTriggers().apply {
                add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
            }
        }).build())
    }

    private fun WorkManager.monitorSyncState() {
        getStatusesForUniqueWork(MediaRetrieveWorker.WORK_NAME)
                .observe(this@MainActivity) {
                    viewModel.syncing.value =
                            it?.firstOrNull { it.state == State.RUNNING } != null
                    if (it?.any { status -> status.state == State.SUCCEEDED } == true) {
                        Timber.d("qgeck sync succeeded")
                        val fragment = supportFragmentManager.fragments.lastOrNull { it.isVisible }
                        when (fragment) {
                            is ArtistListFragment -> artistListViewModel.forceLoad.call()
                            is AlbumListFragment -> albumListViewModel.forceLoad.call()
                            is SongListFragment -> songListViewModel.forceLoad.call()
                            is GenreListFragment -> genreListViewModel.forceLoad.call()
                            is PlaylistListFragment -> playlistListViewModel.forceLoad.call()
                        }
                    }
                }
    }

    private fun WorkManager.cancelSync() {
        cancelUniqueWork(MediaRetrieveWorker.WORK_NAME)
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        retrieveMediaWithPermissionCheck()
    }

    private fun observeEvents() {
        WorkManager.getInstance().monitorSyncState()

        viewModel.resumedFragmentId.observe(this) { navId ->
            if (navId == null) return@observe
            binding.navigationView.setCheckedItem(navId)
            val title = supportFragmentManager.fragments.firstOrNull {
                when (navId) {
                    R.id.nav_artist -> it is ArtistListFragment
                    R.id.nav_album -> it is AlbumListFragment
                    R.id.nav_song -> it is SongListFragment
                    R.id.nav_genre -> it is GenreListFragment
                    R.id.nav_playlist -> it is PlaylistListFragment
                    else -> false
                }
            }?.tag ?: getString(when (navId) {
                R.id.nav_artist -> R.string.nav_artist
                R.id.nav_album -> R.string.nav_album
                R.id.nav_song -> R.string.nav_song
                R.id.nav_genre -> R.string.nav_genre
                R.id.nav_playlist -> R.string.nav_playlist
                R.id.nav_equalizer -> R.string.nav_equalizer
                R.id.nav_pay -> R.string.nav_pay
                R.layout.fragment_easter_egg -> R.string.nav_fortune
                else -> return@observe
            })
            supportActionBar?.title = title
        }

        viewModel.syncing.observe(this) {
            if (it == null) return@observe
            setLockingIndicator(it, viewModel.loading.value == true)
        }

        viewModel.loading.observe(this) {
            if (it == null) return@observe
            setLockingIndicator(viewModel.syncing.value == true, it)
        }

        viewModel.requireScrollTop.observe(this) {
            artistListViewModel.requireScrollTop.call()
            albumListViewModel.requireScrollTop.call()
            songListViewModel.requireScrollTop.call()
            genreListViewModel.requireScrollTop.call()
            playlistListViewModel.requireScrollTop.call()
        }

        viewModel.selectedArtist.observe(this) {
            if (it == null) return@observe
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.ARTIST, artist = it)
            tryTransaction()
        }

        viewModel.selectedAlbum.observe(this) {
            if (it == null) return@observe
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.ALBUM, album = it)
            tryTransaction()
        }

        viewModel.selectedGenre.observe(this) {
            if (it == null) return@observe
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.GENRE, genre = it)
            tryTransaction()
        }

        viewModel.selectedPlaylist.observe(this) {
            if (it == null) return@observe
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.PLAYLIST, playlist = it)
            tryTransaction()
        }

        viewModel.newQueue.observe(this) {
            if (it == null) return@observe
            player?.submitQueue(it)
        }

        viewModel.requestedPositionInQueue.observe(this) {
            if (it == null) return@observe
            player?.play(it)
        }

        viewModel.swappedQueuePositions.observe(this) {
            if (it == null) return@observe
            player?.swapQueuePosition(it.first, it.second)
        }

        viewModel.removedQueueIndex.observe(this) {
            if (it == null) return@observe
            player?.removeQueue(it)
        }

        viewModel.songToDelete.observe(this) {
            if (it == null) return@observe

            deleteFromDeviceWithPermissionCheck(it)
        }

        viewModel.cancelSync.observe(this) {
            WorkManager.getInstance().cancelSync()
        }

        viewModel.searchQuery.observe(this) {
            if (it == null || it.isBlank() || it.filterNot { it.isWhitespace() }.length < 2) {
                binding.coordinatorMain.contentSearch.root.visibility = View.GONE
                return@observe
            } else binding.coordinatorMain.contentSearch.root.visibility = View.VISIBLE

            val db = DB.getInstance(this@MainActivity)
            searchJob.cancel()
            searchJob = uiScope.launch {
                searchListAdapter.clearItems()
                val tracks = db.searchTrackByFuzzyTitle(it).await().take(3)
                if (tracks.isNotEmpty()) {
                    searchListAdapter.addItem(SearchItem(getString(R.string.search_category_song),
                            Unit, SearchItem.SearchItemType.CATEGORY))
                    searchListAdapter.addItems(tracks.map {
                        SearchItem(it.title ?: UNKNOWN, it, SearchItem.SearchItemType.TRACK)
                    })
                }
                val albums = db.searchAlbumByFuzzyTitle(it).await().take(3)
                if (albums.isNotEmpty()) {
                    searchListAdapter.addItem(SearchItem(getString(R.string.search_category_album),
                            Unit, SearchItem.SearchItemType.CATEGORY))
                    searchListAdapter.addItems(albums.map {
                        SearchItem(it.title ?: UNKNOWN, it, SearchItem.SearchItemType.ALBUM)
                    })
                }
                val artists = db.searchArtistByFuzzyTitle(it).await().take(3)
                if (artists.isNotEmpty()) {
                    searchListAdapter.addItem(SearchItem(getString(R.string.search_category_artist),
                            Unit, SearchItem.SearchItemType.CATEGORY))
                    searchListAdapter.addItems(artists.map {
                        SearchItem(it.title ?: UNKNOWN, it, SearchItem.SearchItemType.ARTIST)
                    })
                }
                val playlists = searchPlaylistByFuzzyTitle(it).take(3)
                if (playlists.isNotEmpty()) {
                    searchListAdapter.addItem(SearchItem(getString(R.string.search_category_playlist),
                            Unit, SearchItem.SearchItemType.CATEGORY))
                    searchListAdapter.addItems(playlists.map {
                        SearchItem(it.name, it, SearchItem.SearchItemType.PLAYLIST)
                    })
                }
                val genres = searchGenreByFuzzyTitle(it).take(3)
                if (genres.isNotEmpty()) {
                    searchListAdapter.addItem(SearchItem(getString(R.string.search_category_genre),
                            Unit, SearchItem.SearchItemType.CATEGORY))
                    searchListAdapter.addItems(genres.map {
                        SearchItem(it.name, it, SearchItem.SearchItemType.GENRE)
                    })
                }
            }
        }

        bottomSheetViewModel.playbackButton.observe(this) {
            if (it == null) return@observe
            if (it == PlaybackButton.UNDEFINED) {
                PlayerService.mediaSession?.controller
                        ?.dispatchMediaButtonEvent(
                                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            } else {
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, when (it) {
                    PlaybackButton.PLAY_OR_PAUSE -> PlaybackState.ACTION_PLAY_PAUSE
                    PlaybackButton.NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
                    PlaybackButton.PREV -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    PlaybackButton.FF -> PlaybackState.ACTION_FAST_FORWARD
                    PlaybackButton.REWIND -> PlaybackState.ACTION_REWIND
                    PlaybackButton.UNDEFINED -> throw IllegalStateException()
                }).send()
            }
        }

        bottomSheetViewModel.addQueueToPlaylist.observe(this) { queue ->
            if (queue == null) return@observe
            uiScope.launch {
                val playlists = fetchPlaylists(this@MainActivity).await()
                val binding = DialogAddQueuePlaylistBinding.inflate(layoutInflater)
                val dialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.dialog_title_add_queue_to_playlist)
                        .setMessage(R.string.dialog_desc_add_queue_to_playlist)
                        .setView(binding.root)
                        .setNegativeButton(R.string.dialog_ng) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setPositiveButton(R.string.dialog_ok) { _, _ -> }
                        .setCancelable(true)
                        .create()
                binding.recyclerView.adapter = QueueAddPlaylistListAdapter(playlists) {
                    queue.forEachIndexed { i, song ->
                        contentResolver.insert(
                                MediaStore.Audio.Playlists.Members
                                        .getContentUri("external", it.id),
                                ContentValues().apply {
                                    put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, it.memberCount + 1 + i)
                                    put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)
                                })
                    }
                    dialog.dismiss()
                }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val title = binding.editText.text?.toString()
                    if (title.isNullOrBlank()) {
                        // TODO: エラーメッセージ表示
                    } else {
                        val playlistId = contentResolver.insert(
                                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                ContentValues().apply {
                                    val now = System.currentTimeMillis()
                                    put(MediaStore.Audio.PlaylistsColumns.NAME, title)
                                    put(MediaStore.Audio.PlaylistsColumns.DATE_ADDED, now)
                                    put(MediaStore.Audio.PlaylistsColumns.DATE_MODIFIED, now)
                                })?.let { ContentUris.parseId(it) } ?: kotlin.run {
                            dialog.dismiss()
                            return@setOnClickListener
                        }
                        queue.forEachIndexed { i, song ->
                            contentResolver.insert(
                                    MediaStore.Audio.Playlists.Members
                                            .getContentUri("external", playlistId),
                                    ContentValues().apply {
                                        put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, i + 1)
                                        put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.mediaId)
                                    })
                        }
                        dialog.dismiss()
                    }
                }
            }
        }

        bottomSheetViewModel.clearQueue.observe(this) {
            player?.clear(true)
        }

        bottomSheetViewModel.newSeekBarProgress.observe(this) {
            if (it == null) return@observe
            player?.seek(it)
        }

        bottomSheetViewModel.shuffle.observe(this) { player?.shuffle() }

        bottomSheetViewModel.changeRepeatMode.observe(this) { player?.rotateRepeatMode() }

        bottomSheetViewModel.changedQueue.observe(this) {
            if (it == null) return@observe
            player?.submitQueue(InsertQueue(
                    QueueMetadata(InsertActionType.OVERRIDE,
                            OrientedClassType.SONG), it))
        }

        bottomSheetViewModel.changedPosition.observe(this) {
            if (it == null) return@observe
            player?.forcePosition(it)
        }

        paymentViewModel.saveSuccess.observe(this) {
            if (it == null) return@observe
            Snackbar.make(binding.root,
                    if (it) R.string.payment_save_success
                    else R.string.payment_save_failure,
                    Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                binding.drawerLayout, binding.coordinatorMain.toolbar,
                R.string.drawer_open, R.string.drawer_close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(onNavigationItemSelected)
        binding.navigationView.getHeaderView(0).findViewById<View>(R.id.drawer_head_icon)?.setOnLongClickListener {
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.EASTER_EGG)
            tryTransaction()
            true
        }
    }

    private fun setLockingIndicator(syncing: Boolean, loading: Boolean) {
        when {
            syncing -> {
                indicateSync()
                toggleIndicateLock(true)
            }
            syncing.not() && loading -> {
                indicateLoad()
                toggleIndicateLock(true)
            }
            syncing.not() && loading.not() -> {
                toggleIndicateLock(false)
            }
        }
    }

    private fun indicateSync() {
        binding.coordinatorMain.indicatorLocking.descLocking.text = getString(R.string.syncing)
        binding.coordinatorMain.indicatorLocking.progressSync.visibility = View.VISIBLE
        binding.coordinatorMain.indicatorLocking.buttonCancelSync.visibility = View.VISIBLE
    }

    private fun indicateLoad() {
        binding.coordinatorMain.indicatorLocking.descLocking.text = getString(R.string.loading)
        binding.coordinatorMain.indicatorLocking.progressSync.visibility = View.GONE
        binding.coordinatorMain.indicatorLocking.buttonCancelSync.visibility = View.GONE
    }

    private fun toggleIndicateLock(locking: Boolean) {
        binding.coordinatorMain.indicatorLocking.root.visibility =
                if (locking) View.VISIBLE else View.GONE
        binding.drawerLayout.setDrawerLockMode(
                if (locking) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                else DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    private fun tryTransaction() {
        if (paused.not()) {
            requestedTransaction?.apply {
                Timber.d("qgeck requested transaction: $requestedTransaction")
                dismissSearch()
                when (this.tag) {
                    RequestedTransaction.Tag.ARTIST -> {
                        if (artist != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.content_main,
                                            AlbumListFragment.newInstance(artist), artist.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    RequestedTransaction.Tag.ALBUM -> {
                        if (album != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.content_main,
                                            SongListFragment.newInstance(album), album.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    RequestedTransaction.Tag.GENRE -> {
                        if (genre != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.content_main,
                                            SongListFragment.newInstance(genre), genre.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    RequestedTransaction.Tag.PLAYLIST -> {
                        if (playlist != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.content_main,
                                            SongListFragment.newInstance(playlist), playlist.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    RequestedTransaction.Tag.EASTER_EGG -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.content_main, EasterEggFragment.newInstance())
                                .addToBackStack(null)
                                .commit()
                        binding.drawerLayout.closeDrawer(binding.navigationView)
                    }
                }
                requestedTransaction = null
            }
        }
    }

    private fun dismissSearch() {
        binding.coordinatorMain.contentSearch.root.visibility = View.GONE
        getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    internal fun deleteFromDevice(song: Song) {
        File(song.sourcePath).apply {
            if (this.exists()) {
                player?.removeQueue(song.id)
                this.delete()
            }
        }
        contentResolver.delete(
                android.provider.MediaStore.Files.getContentUri("external"),
                "${android.provider.MediaStore.Files.FileColumns.DATA}=?",
                kotlin.arrayOf(song.sourcePath))

        bgScope.launch {
            val db = com.geckour.q.data.db.DB.getInstance(this@MainActivity)
            val track = db.trackDao().get(song.id) ?: return@launch

            player?.removeQueue(track.id)

            var deleted = db.trackDao().delete(track.id) > 0
            if (deleted)
                uiScope.launch {
                    songListViewModel.songIdDeleted.value = track.id
                }
            if (db.trackDao().findByAlbum(track.albumId).isEmpty()) {
                deleted = db.albumDao().delete(track.albumId) > 0
                if (deleted)
                    uiScope.launch {
                        albumListViewModel.albumIdDeleted.value = track.albumId
                    }
            }
            if (db.trackDao().findByArtist(track.artistId).isEmpty()) {
                deleted = db.artistDao().delete(track.artistId) > 0
                if (deleted)
                    uiScope.launch {
                        artistListViewModel.artistIdDeleted.value = track.artistId
                    }
            }
        }
    }
}
