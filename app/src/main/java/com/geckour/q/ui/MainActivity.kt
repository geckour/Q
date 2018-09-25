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
import com.geckour.q.domain.model.RequestedTransaction
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.dialog.playlist.QueueAddPlaylistListAdapter
import com.geckour.q.ui.easteregg.EasterEggFragment
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.playlist.PlaylistListFragment
import com.geckour.q.ui.library.song.SongListFragment
import com.geckour.q.ui.pay.PaymentFragment
import com.geckour.q.ui.pay.PaymentViewModel
import com.geckour.q.ui.sheet.BottomSheetViewModel
import com.geckour.q.util.*
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import java.io.File

@RuntimePermissions
class MainActivity : AppCompatActivity() {

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
    private val paymentViewModel: PaymentViewModel by lazy {
        ViewModelProviders.of(this)[PaymentViewModel::class.java]
    }
    internal lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var parentJob = Job()

    private var requestedTransaction: RequestedTransaction? = null
    private var paused = true

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
        val fragment = when (it.itemId) {
            R.id.nav_artist -> ArtistListFragment.newInstance()
            R.id.nav_album -> AlbumListFragment.newInstance()
            R.id.nav_song -> SongListFragment.newInstance()
            R.id.nav_genre -> GenreListFragment.newInstance()
            R.id.nav_playlist -> PlaylistListFragment.newInstance()
            R.id.nav_setting -> null
            R.id.nav_sync -> {
                retrieveMediaWithPermissionCheck()
                null
            }
            R.id.nav_pay -> PaymentFragment.newInstance()
            else -> null
        }
        if (fragment != null) {
            supportFragmentManager.beginTransaction()
                    .apply {
                        if (binding.coordinatorMain.contentMain.fragmentContainer.childCount == 0)
                            add(R.id.fragment_container, fragment)
                        else {
                            replace(R.id.fragment_container, fragment)
                            addToBackStack(null)
                        }
                    }
                    .commit()
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

        observeEvents()
        registerReceiver(syncingProgressReceiver, IntentFilter(ACTION_PROGRESS_SYNCING))

        setSupportActionBar(binding.coordinatorMain.contentMain.toolbar)
        setupDrawer()

        if (savedInstanceState == null) {
            retrieveMediaIfEmpty()

            // TODO: 設定画面でどの画面を初期画面にするか設定できるようにする
            val navId = R.id.nav_artist
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

        WorkManager.getInstance().monitorSyncState()

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
        unbindPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.onRequestedStopService()
        unregisterReceiver(syncingProgressReceiver)
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
        launch(parentJob) {
            if (DB.getInstance(this@MainActivity).trackDao().count() == 0)
                retrieveMediaWithPermissionCheck()
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
                else -> return@Observer
            })
            supportActionBar?.title = title
        })

        viewModel.syncing.observe(this, Observer {
            if (it == null) return@Observer
            toggleIndicateSync(it)
        })

        viewModel.loading.observe(this, Observer {
            if (it == null || viewModel.syncing.value == true) return@Observer
            toggleIndicateLoad(it)
        })

        viewModel.selectedArtist.observe(this, Observer {
            if (it == null) return@Observer
            requestedTransaction = RequestedTransaction(ArtistListFragment.TAG, artist = it)
            tryTransaction()
        })

        viewModel.selectedAlbum.observe(this, Observer {
            if (it == null) return@Observer
            requestedTransaction = RequestedTransaction(AlbumListFragment.TAG, album = it)
            tryTransaction()
        })

        viewModel.selectedGenre.observe(this, Observer {
            if (it == null) return@Observer
            requestedTransaction = RequestedTransaction(GenreListFragment.TAG, genre = it)
            tryTransaction()
        })

        viewModel.selectedPlaylist.observe(this, Observer {
            if (it == null) return@Observer
            requestedTransaction = RequestedTransaction(PlaylistListFragment.TAG, playlist = it)
            tryTransaction()
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

        viewModel.songToDelete.observe(this, Observer {
            if (it == null) return@Observer

            File(it.sourcePath).apply {
                if (this.exists()) {
                    player?.removeQueue(it.id)
                    this.delete()
                }
            }
            val deleted = contentResolver.delete(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.Files.FileColumns.DATA}=?",
                    arrayOf(it.sourcePath)) == 1

            if (deleted) {
                launch { DB.getInstance(this@MainActivity).trackDao().delete(it.id) }
                viewModel.songIdDeleted.value = it.id
            }
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
            launch(UI + parentJob) {
                val playlists = fetchPlaylists(this@MainActivity).await()
                val binding = DialogAddQueuePlaylistBinding.inflate(layoutInflater)
                val dialog = AlertDialog.Builder(this@MainActivity, R.style.DialogStyle)
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

        paymentViewModel.saveSuccess.observe(this, Observer {
            if (it == null) return@Observer
            Snackbar.make(binding.root,
                    if (it) R.string.payment_save_success
                    else R.string.payment_save_failure,
                    Snackbar.LENGTH_SHORT).show()
        })
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                binding.drawerLayout, binding.coordinatorMain.contentMain.toolbar,
                R.string.drawer_open, R.string.drawer_close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(onNavigationItemSelected)
        binding.navigationView.getHeaderView(0).findViewById<View>(R.id.drawer_head_icon)?.setOnLongClickListener {
            requestedTransaction = RequestedTransaction(EasterEggFragment.TAG)
            tryTransaction()
            true
        }
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

    private fun tryTransaction() {
        if (paused.not()) {
            requestedTransaction?.apply {
                when (this.tag) {
                    ArtistListFragment.TAG -> {
                        if (artist != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container,
                                            AlbumListFragment.newInstance(artist), artist.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    AlbumListFragment.TAG -> {
                        if (album != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container,
                                            SongListFragment.newInstance(album), album.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    GenreListFragment.TAG -> {
                        if (genre != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container,
                                            SongListFragment.newInstance(genre), genre.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    PlaylistListFragment.TAG -> {
                        if (playlist != null) {
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container,
                                            SongListFragment.newInstance(playlist), playlist.name)
                                    .addToBackStack(null)
                                    .commit()
                        }
                    }
                    EasterEggFragment.TAG -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, EasterEggFragment.newInstance())
                                .addToBackStack(null)
                                .commit()
                        binding.drawerLayout.closeDrawer(binding.navigationView)
                    }
                }
                requestedTransaction = null
            }
        }
    }
}
