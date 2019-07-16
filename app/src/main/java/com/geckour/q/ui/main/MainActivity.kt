package com.geckour.q.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import com.geckour.q.R
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.RequestedTransaction
import com.geckour.q.domain.model.Song
import com.geckour.q.service.MediaRetrieveService
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.easteregg.EasterEggFragment
import com.geckour.q.ui.equalizer.EqualizerFragment
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
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.ducking
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.preferScreen
import com.geckour.q.util.toNightModeInt
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber
import java.io.File

@RuntimePermissions
class MainActivity : CrashlyticsBundledActivity() {

    enum class RequestCode(val code: Int) {
        RESULT_SETTING(333)
    }

    companion object {
        private const val ACTION_SYNCING = "action_syncing"
        private const val EXTRA_SYNCING_PROGRESS = "extra_syncing_progress"
        private const val EXTRA_SYNCING_COMPLETE = "extra_syncing_complete"
        private const val STATE_KEY_REQUESTED_TRANSACTION = "state_key_requested_transaction"

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

        fun createProgressIntent(progress: Pair<Int, Int>) = Intent(ACTION_SYNCING).apply {
            putExtra(EXTRA_SYNCING_PROGRESS, progress)
        }

        fun createSyncCompleteIntent(complete: Boolean) = Intent(ACTION_SYNCING).apply {
            putExtra(EXTRA_SYNCING_COMPLETE, complete)
        }
    }

    private val viewModel: MainViewModel by viewModels()

    private val bottomSheetViewModel: BottomSheetViewModel by viewModels()
    private val artistListViewModel: ArtistListViewModel by viewModels()
    private val albumListViewModel: AlbumListViewModel by viewModels()
    private val songListViewModel: SongListViewModel by viewModels()
    private val genreListViewModel: SongListViewModel by viewModels()
    private val playlistListViewModel: SongListViewModel by viewModels()
    private val paymentViewModel: PaymentViewModel by viewModels()

    internal lateinit var binding: ActivityMainBinding
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private lateinit var drawerToggle: ActionBarDrawerToggle

    private val searchListAdapter: SearchListAdapter by lazy { SearchListAdapter(viewModel) }

    private var requestedTransaction: RequestedTransaction? = null
    private var paused = true

    private val syncingProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.apply {
                extras?.getBoolean(EXTRA_SYNCING_COMPLETE, false)?.apply {
                    if (this) {
                        viewModel.syncing = false
                        setLockingIndicator()
                        when (supportFragmentManager.fragments.lastOrNull { it.isVisible }) {
                            is ArtistListFragment -> artistListViewModel.forceLoad.call()
                            is AlbumListFragment -> albumListViewModel.forceLoad.call()
                            is SongListFragment -> songListViewModel.forceLoad.call()
                            is GenreListFragment -> genreListViewModel.forceLoad.call()
                            is PlaylistListFragment -> playlistListViewModel.forceLoad.call()
                        }
                    }
                }
                (extras?.get(EXTRA_SYNCING_PROGRESS) as? Pair<Int, Int>)?.apply {
                    viewModel.syncing = true
                    setLockingIndicator()
                    binding.coordinatorMain.indicatorLocking.progressSync.text =
                            getString(R.string.progress_sync, this.first, this.second)
                }
            }
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
                startActivityForResult(
                        SettingActivity.createIntent(this), RequestCode.RESULT_SETTING.code
                )
                null
            }
            R.id.nav_equalizer -> EqualizerFragment.newInstance()
            R.id.nav_sync -> {
                FirebaseAnalytics.getInstance(this)
                        .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                            putString(
                                    FirebaseAnalytics.Param.ITEM_NAME, "Invoked force sync"
                            )
                        })

                retrieveMediaWithPermissionCheck()
                null
            }
            R.id.nav_pay -> PaymentFragment.newInstance()
            else -> null
        }
        if (fragment != null) {
            supportFragmentManager.beginTransaction().apply {
                if ((binding.coordinatorMain.contentMain.root as FrameLayout).childCount == 0) add(
                        R.id.content_main, fragment
                )
                else {
                    replace(R.id.content_main, fragment)
                    addToBackStack(null)
                }
            }.commit()
        }
        binding.drawerLayout.post { binding.drawerLayout.closeDrawers() }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.coordinatorMain.viewModel = viewModel
        binding.coordinatorMain.contentSearch.recyclerView.adapter = searchListAdapter

        observeEvents()
        registerReceiver(syncingProgressReceiver, IntentFilter(ACTION_SYNCING))

        setSupportActionBar(binding.coordinatorMain.toolbar)
        setupDrawer()

        if (savedInstanceState == null) {
            viewModel.checkDBIsEmpty()

            val navId = PreferenceManager.getDefaultSharedPreferences(this).preferScreen.value.navId
            onNavigationItemSelected(binding.navigationView.menu.findItem(navId))
        } else if (savedInstanceState.containsKey(STATE_KEY_REQUESTED_TRANSACTION)) {
            requestedTransaction =
                    savedInstanceState.getParcelable(STATE_KEY_REQUESTED_TRANSACTION) as RequestedTransaction
        }
    }

    override fun onResume() {
        super.onResume()

        delegate.localNightMode = sharedPreferences.isNightMode.toNightModeInt

        paused = false

        tryTransaction()
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

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(syncingProgressReceiver)
    }

    override fun onBackPressed() {
        when {
            binding.drawerLayout.isDrawerOpen(binding.navigationView) -> binding.drawerLayout.closeDrawer(
                    binding.navigationView
            )
            bottomSheetViewModel.sheetState == BottomSheetBehavior.STATE_EXPANDED -> bottomSheetViewModel.toggleSheetState.call()
            else -> super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RequestCode.RESULT_SETTING.code -> {
                if (viewModel.player.value?.getDuking() != sharedPreferences.ducking) {
                    viewModel.rebootPlayer()
                }
            }
        }
    }

    @NeedsPermission(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    internal fun retrieveMedia() {
        startService(MediaRetrieveService.getIntent(this, false))
    }

    @OnPermissionDenied(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    internal fun onReadExternalStorageDenied() {
        retrieveMediaWithPermissionCheck()
    }

    private fun observeEvents() {
        viewModel.player.observe(this) { player ->
            player ?: return@observe

            player.setOnDestroyedListener {
                viewModel.onDestroyPlayer()
            }

            player.publishStatus()
        }

        viewModel.dbEmpty.observe(this) {
            retrieveMediaWithPermissionCheck()
        }

        viewModel.currentFragmentId.observe(this) { navId ->
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
            }?.tag ?: getString(
                    when (navId) {
                        R.id.nav_artist -> R.string.nav_artist
                        R.id.nav_album -> R.string.nav_album
                        R.id.nav_song -> R.string.nav_song
                        R.id.nav_genre -> R.string.nav_genre
                        R.id.nav_playlist -> R.string.nav_playlist
                        R.id.nav_equalizer -> R.string.nav_equalizer
                        R.id.nav_pay -> R.string.nav_pay
                        R.layout.fragment_easter_egg -> R.string.nav_fortune
                        else -> return@observe
                    }
            )
            supportActionBar?.title = title
        }

        viewModel.loading.observe(this) {
            if (it == null) return@observe
            setLockingIndicator()
        }

        viewModel.scrollToTop.observe(this) {
            artistListViewModel.scrollToTop.call()
            albumListViewModel.scrollToTop.call()
            songListViewModel.scrollToTop.call()
            genreListViewModel.scrollToTop.call()
            playlistListViewModel.scrollToTop.call()
        }

        viewModel.selectedArtist.observe(this) {
            if (it == null) return@observe
            requestedTransaction = RequestedTransaction(
                    RequestedTransaction.Tag.ARTIST, artist = it
            )
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
            requestedTransaction = RequestedTransaction(
                    RequestedTransaction.Tag.PLAYLIST, playlist = it
            )
            tryTransaction()
        }

        viewModel.newQueueInfo.observe(this) {
            if (it == null) return@observe
            viewModel.player.value?.submitQueue(it)
        }

        viewModel.requestedPositionInQueue.observe(this) {
            if (it == null) return@observe
            viewModel.player.value?.play(it)
        }

        viewModel.swappedQueuePositions.observe(this) {
            if (it == null) return@observe
            viewModel.player.value?.swapQueuePosition(it.first, it.second)
        }

        viewModel.removedQueueIndex.observe(this) {
            if (it == null) return@observe
            viewModel.player.value?.removeQueue(it)
        }

        viewModel.songToDelete.observe(this) {
            if (it == null) return@observe

            deleteFromDeviceWithPermissionCheck(it)
        }

        viewModel.searchItems.observe(this) {
            if (it == null) {
                binding.coordinatorMain.contentSearch.root.visibility = View.GONE
                return@observe
            } else binding.coordinatorMain.contentSearch.root.visibility = View.VISIBLE

            searchListAdapter.replaceItems(it)
        }

        bottomSheetViewModel.playbackButton.observe(this) {
            if (it == null) return@observe
            PlayerService.mediaSession?.controller?.dispatchMediaButtonEvent(
                    KeyEvent(
                            if (it == PlaybackButton.UNDEFINED)
                                KeyEvent.ACTION_UP
                            else KeyEvent.ACTION_DOWN,
                            when (it) {
                                PlaybackButton.PLAY_OR_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                                PlaybackButton.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                                PlaybackButton.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                                PlaybackButton.FF -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                                PlaybackButton.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
                                PlaybackButton.UNDEFINED -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            }
                    )
            )
        }

        bottomSheetViewModel.clearQueue.observe(this) {
            viewModel.player.value?.clear(true)
        }

        bottomSheetViewModel.newSeekBarProgress.observe(this) {
            if (it == null) return@observe
            viewModel.player.value?.seek(it)
        }

        bottomSheetViewModel.shuffle.observe(this) { viewModel.player.value?.shuffle() }

        paymentViewModel.saveSuccess.observe(this) {
            if (it == null) return@observe
            Snackbar.make(
                    binding.root, if (it) R.string.payment_save_success
            else R.string.payment_save_failure, Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
                this,
                binding.drawerLayout,
                binding.coordinatorMain.toolbar,
                R.string.drawer_open,
                R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(onNavigationItemSelected)
        binding.navigationView.getHeaderView(0).findViewById<View>(R.id.drawer_head_icon)
                ?.setOnLongClickListener {
                    requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.EASTER_EGG)
                    tryTransaction()
                    true
                }
    }

    private fun setLockingIndicator() {
        when {
            viewModel.syncing -> {
                indicateSync()
                toggleIndicateLock(true)
            }
            viewModel.loading.value == true -> {
                indicateLoad()
                toggleIndicateLock(true)
            }
            else -> {
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
                else DrawerLayout.LOCK_MODE_UNLOCKED
        )
    }

    private fun tryTransaction() {
        if (paused.not()) {
            requestedTransaction?.apply {
                Timber.d("qgeck requested transaction: $requestedTransaction")
                dismissSearch()
                when (this.tag) {
                    RequestedTransaction.Tag.ARTIST -> {
                        if (artist != null) {
                            supportFragmentManager.beginTransaction().replace(
                                    R.id.content_main, AlbumListFragment.newInstance(
                                    artist
                            ), artist.name
                            ).addToBackStack(
                                    null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.ALBUM -> {
                        if (album != null) {
                            supportFragmentManager.beginTransaction().replace(
                                    R.id.content_main, SongListFragment.newInstance(
                                    album
                            ), album.name
                            ).addToBackStack(
                                    null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.GENRE -> {
                        if (genre != null) {
                            supportFragmentManager.beginTransaction().replace(
                                    R.id.content_main, SongListFragment.newInstance(
                                    genre
                            ), genre.name
                            ).addToBackStack(
                                    null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.PLAYLIST -> {
                        if (playlist != null) {
                            supportFragmentManager.beginTransaction().replace(
                                    R.id.content_main, SongListFragment.newInstance(
                                    playlist
                            ), playlist.name
                            ).addToBackStack(
                                    null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.EASTER_EGG -> {
                        supportFragmentManager.beginTransaction().replace(
                                R.id.content_main, EasterEggFragment.newInstance()
                        ).addToBackStack(
                                null
                        ).commit()
                        binding.drawerLayout.closeDrawer(binding.navigationView)
                    }
                }
                requestedTransaction = null
            }
        }
    }

    private fun dismissSearch() {
        binding.coordinatorMain.contentSearch.root.visibility = View.GONE
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(
                currentFocus?.windowToken, 0
        )
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    internal fun deleteFromDevice(song: Song) {
        File(song.sourcePath).apply {
            if (this.exists()) {
                viewModel.player.value?.removeQueue(song.id)
                this.delete()
            }
        }
        contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.DATA}=?",
                arrayOf(song.sourcePath)
        )

        viewModel.deleteSongFromDB(song)
    }
}
