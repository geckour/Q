package com.geckour.q.ui

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.State
import androidx.work.WorkManager
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.databinding.DialogAddQueuePlaylistBinding
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.playlist.PlaylistListFragment
import com.geckour.q.ui.library.song.SongListFragment
import com.geckour.q.ui.sheet.BottomSheetViewModel
import com.geckour.q.util.*
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_PROGRESS_SYNCING = "action_progress_syncing"
        private const val EXTRA_PROGRESS_SYNCING = "extra_progress_syncing"

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
    internal lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var parentJob = Job()

    private var player: PlayerService? = null

    private var isBoundService = false

    private val syncingProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            (intent?.extras?.get(EXTRA_PROGRESS_SYNCING) as? Pair<Int, Int>)?.apply {
                binding.coordinatorMain.progressSync.text =
                        getString(R.string.progress_sync, this.first, this.second)
            }
        }
    }

    private val onNavigationItemSelected: (MenuItem) -> Boolean = {
        when (it.itemId) {
            R.id.nav_artist -> {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container,
                                ArtistListFragment.newInstance(), getString(R.string.nav_artist))
                        .addToBackStack(null)
                        .commit()
            }
            R.id.nav_album -> {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container,
                                AlbumListFragment.newInstance(), getString(R.string.nav_album))
                        .addToBackStack(null)
                        .commit()
            }
            R.id.nav_song -> {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container,
                                SongListFragment.newInstance(), getString(R.string.nav_song))
                        .addToBackStack(null)
                        .commit()
            }
            R.id.nav_genre -> {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container,
                                GenreListFragment.newInstance(), getString(R.string.nav_genre))
                        .addToBackStack(null)
                        .commit()
            }
            R.id.nav_playlist -> {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container,
                                PlaylistListFragment.newInstance(),
                                getString(R.string.nav_playlist))
                        .addToBackStack(null)
                        .commit()
            }
            R.id.nav_setting -> {
            }
            R.id.nav_sync -> {
                retrieveMediaWithPermissionCheck()
            }
        }
        launch(UI + parentJob) { binding.drawerLayout.closeDrawers() }
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
                player = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.coordinatorMain.viewModel = viewModel
        binding.coordinatorMain.contentMain.viewModel = viewModel

        setSupportActionBar(binding.coordinatorMain.contentMain.toolbar)

        setupDrawer()

        // TODO: 設定画面でどの画面を初期画面にするか設定できるようにする
        val navId = R.id.nav_artist
        onNavigationItemSelected(binding.navigationView.menu.findItem(navId))
        binding.navigationView.setCheckedItem(navId)

        observeEvents()

        retrieveMediaIfEmpty()

        registerReceiver(syncingProgressReceiver, IntentFilter(ACTION_PROGRESS_SYNCING))
    }

    override fun onResume() {
        super.onResume()

        WorkManager.getInstance().monitorSyncState()

        if (player == null) {
            bottomSheetViewModel.currentQueue.value = emptyList()
            startService(PlayerService.createIntent(this))
        }
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
        bindPlayer()
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
        unbindPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.onRequestedStop()
        unregisterReceiver(syncingProgressReceiver)
    }

    override fun onBackPressed() {
        if (bottomSheetViewModel.sheetState.value == BottomSheetBehavior.STATE_EXPANDED)
            bottomSheetViewModel.sheetState.value = BottomSheetBehavior.STATE_COLLAPSED
        else super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun bindPlayer() {
        if (isBoundService.not()) {
            bindService(PlayerService.createIntent(this),
                    serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindPlayer() {
        if (isBoundService) {
            isBoundService = false
            unbindService(serviceConnection)
        }
    }

    private fun onDestroyPlayer() {
        player = null
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun retrieveMedia() {
        WorkManager.getInstance().invokeRetrieveMediaWorker()
    }

    private fun retrieveMediaIfEmpty() {
        launch(UI + parentJob) {
            DB.getInstance(this@MainActivity)
                    .trackDao()
                    .getAllAsync()
                    .observe(this@MainActivity, Observer {
                        if (it?.isNotEmpty() == false) retrieveMediaWithPermissionCheck()
                    })
        }
    }

    private fun WorkManager.invokeRetrieveMediaWorker() {
        beginUniqueWork(MediaRetrieveWorker.WORK_NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MediaRetrieveWorker>().build()).enqueue()
        viewModel.syncing.value = true
        monitorSyncState()
    }

    private fun WorkManager.monitorSyncState() {
        getStatusesForUniqueWork(MediaRetrieveWorker.WORK_NAME)
                .observe(this@MainActivity, Observer {
                    viewModel.syncing.value =
                            it?.firstOrNull { it.state == State.RUNNING } != null
                })
    }

    private fun WorkManager.cancelSync() {
        cancelUniqueWork(MediaRetrieveWorker.WORK_NAME)
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        retrieveMediaWithPermissionCheck()
    }


    private fun observeEvents() {
        viewModel.resumedFragmentId.observe(this, Observer { navId ->
            if (navId == null) return@Observer
            supportFragmentManager.fragments.firstOrNull {
                when (navId) {
                    R.id.nav_artist -> it is ArtistListFragment
                    R.id.nav_album -> it is AlbumListFragment
                    R.id.nav_song -> it is SongListFragment
                    R.id.nav_genre -> it is GenreListFragment
                    R.id.nav_playlist -> it is PlaylistListFragment
                    else -> false
                }
            }?.tag?.apply {
                supportActionBar?.title = this
            }
            binding.navigationView.setCheckedItem(navId)
        })

        viewModel.syncing.observe(this, Observer {
            if (it == null) return@Observer
            toggleIndicateSync(it)
        })

        viewModel.loading.observe(this, Observer {
            Timber.d("qgeck loading: $it, syncing: ${viewModel.syncing.value}")
            if (it == null || viewModel.syncing.value == true) return@Observer
            toggleIndicateLoad(it)
        })

        viewModel.selectedArtist.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AlbumListFragment.newInstance(it), it.name)
                    .addToBackStack(null)
                    .commit()
        })

        viewModel.selectedAlbum.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SongListFragment.newInstance(it), it.name)
                    .addToBackStack(null)
                    .commit()
        })

        viewModel.selectedGenre.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SongListFragment.newInstance(it), it.name)
                    .addToBackStack(null)
                    .commit()
        })

        viewModel.selectedPlaylist.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SongListFragment.newInstance(it), it.name)
                    .addToBackStack(null)
                    .commit()
        })

        viewModel.newQueue.observe(this, Observer {
            if (it == null) return@Observer
            player?.submitQueue(it)
        })

        viewModel.requestedPositionInQueue.observe(this, Observer {
            if (it == null) return@Observer
            player?.play(it)
        })

        viewModel.swappedQueuePositions.observe(this, Observer {
            if (it == null) return@Observer
            player?.swapQueuePosition(it.first, it.second)
        })

        viewModel.removedQueueIndex.observe(this, Observer {
            if (it == null) return@Observer
            player?.removeQueue(it)
        })

        viewModel.deletedSongId.observe(this, Observer {
            if (it == null) return@Observer
            player?.removeQueue(it)
        })

        viewModel.cancelSync.observe(this, Observer {
            WorkManager.getInstance().cancelSync()
        })

        bottomSheetViewModel.playbackButton.observe(this, Observer {
            if (it == null) return@Observer
            when (it) {
                PlaybackButton.PLAY_OR_PAUSE -> player?.togglePlayPause()
                PlaybackButton.NEXT -> player?.next()
                PlaybackButton.PREV -> player?.headOrPrev()
                PlaybackButton.FF -> player?.fastForward()
                PlaybackButton.REWIND -> player?.rewind()
                PlaybackButton.UNDEFINED -> player?.stopRunningButtonAction()
            }
        })

        bottomSheetViewModel.addQueueToPlaylist.observe(this, Observer { queue ->
            if (queue == null) return@Observer
            val playlists = fetchPlaylists(this)
            val binding = DialogAddQueuePlaylistBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(this, R.style.DialogStyle)
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
                                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.id)
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
                                    put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.id)
                                })
                    }
                    dialog.dismiss()
                }
            }
        })

        bottomSheetViewModel.clearQueue.observe(this, Observer {
            player?.clear(true)
        })

        bottomSheetViewModel.newSeekBarProgress.observe(this, Observer {
            if (it == null) return@Observer
            player?.seek(it)
        })

        bottomSheetViewModel.shuffle.observe(this, Observer {
            player?.shuffle()
        })

        bottomSheetViewModel.changeRepeatMode.observe(this, Observer {
            player?.rotateRepeatMode()
        })

        bottomSheetViewModel.changedQueue.observe(this, Observer {
            if (it == null) return@Observer
            player?.submitQueue(InsertQueue(
                    QueueMetadata(InsertActionType.OVERRIDE,
                            OrientedClassType.SONG), it))
        })

        bottomSheetViewModel.changedPosition.observe(this, Observer {
            if (it == null) return@Observer
            player?.forcePosition(it)
        })
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                binding.drawerLayout, binding.coordinatorMain.contentMain.toolbar,
                R.string.drawer_open, R.string.drawer_close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(onNavigationItemSelected)
    }

    private fun toggleIndicateSync(syncing: Boolean) {
        toggleIndicateLock(syncing)
        binding.coordinatorMain.descLocking.text = getString(R.string.syncing)
        binding.coordinatorMain.progressSync.visibility = View.VISIBLE
        binding.coordinatorMain.buttonCancelSync.visibility = View.VISIBLE

    }

    private fun toggleIndicateLoad(loading: Boolean) {
        toggleIndicateLock(loading)
        binding.coordinatorMain.descLocking.text = getString(R.string.loading)
        binding.coordinatorMain.progressSync.visibility = View.GONE
        binding.coordinatorMain.buttonCancelSync.visibility = View.GONE
    }

    private fun toggleIndicateLock(locking: Boolean) {
        binding.coordinatorMain.indicatorLocking.visibility =
                if (locking) View.VISIBLE else View.GONE
        binding.drawerLayout.setDrawerLockMode(
                if (locking) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                else DrawerLayout.LOCK_MODE_UNLOCKED)
    }
}
