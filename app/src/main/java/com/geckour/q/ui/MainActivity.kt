package com.geckour.q.ui

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.State
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.playlist.PlaylistListFragment
import com.geckour.q.ui.library.song.SongListFragment
import com.geckour.q.ui.sheet.BottomSheetViewModel
import com.geckour.q.util.MediaRetrieveWorker
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber
import java.util.*

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREF_KEY_LATEST_WORKER_ID = "pref_key_latest_worker_id"

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
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

    private var isBoundedService = false

    private val onNavigationItemSelectedListener: ((MenuItem) -> Boolean) = {
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
                isBoundedService = true
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

                    publishQueue()
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

        startService(PlayerService.createIntent(this))

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setSupportActionBar(binding.coordinatorMain.contentMain.toolbar)

        setupDrawer()

        // TODO: 設定画面でどの画面を初期画面にするか設定できるようにする
        val navId = R.id.nav_artist
        onNavigationItemSelectedListener(binding.navigationView.menu.findItem(navId))
        binding.navigationView.setCheckedItem(navId)

        observeEvents()

        retrieveMediaIfEmpty()
    }

    override fun onResume() {
        super.onResume()

        getLatestWorkerId()?.apply {
            WorkManager.getInstance()
                    .getStatusById(this)
                    .observe(this@MainActivity, Observer {
                        viewModel.isLoading.value = it?.state == State.RUNNING
                    })
        }
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
        if (isBoundedService.not()) {
            bindService(PlayerService.createIntent(this),
                    serviceConnection, Context.BIND_ADJUST_WITH_ACTIVITY)
        }
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
        if (isBoundedService) {
            isBoundedService = false
            unbindService(serviceConnection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.onActivityDestroy()
    }

    override fun onBackPressed() {
        if (bottomSheetViewModel.sheetState.value == BottomSheetBehavior.STATE_EXPANDED)
            bottomSheetViewModel.sheetState.value = BottomSheetBehavior.STATE_COLLAPSED
        else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.keySet()?.forEach {
            Timber.d("saved state: $it - ${outState[it]}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun retrieveMedia() {
        WorkManager.getInstance().also { workManager ->
            val work = OneTimeWorkRequestBuilder<MediaRetrieveWorker>().build()
            getLatestWorkerId()?.apply {
                workManager.getStatusById(this).observe(this@MainActivity, Observer {
                    if (it?.state != State.RUNNING) {
                        workManager.cancelWorkById(this)
                        workManager.invokeRetrieveMediaWorker(work)
                    }
                })
            } ?: workManager.invokeRetrieveMediaWorker(work)
        }
    }

    private fun retrieveMediaIfEmpty() {
        launch(parentJob) {
            DB.getInstance(this@MainActivity)
                    .trackDao()
                    .getAllAsync()
                    .observe(this@MainActivity, Observer {
                        if (it?.isNotEmpty() == false) retrieveMediaWithPermissionCheck()
                    })
        }
    }

    private fun WorkManager.invokeRetrieveMediaWorker(work: WorkRequest) {
        setLatestWorkerId(work.id)
        enqueue(work)
        viewModel.isLoading.value = true
        getStatusById(work.id).observe(this@MainActivity, Observer {
            viewModel.isLoading.value = it?.state == State.RUNNING
        })
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        retrieveMediaWithPermissionCheck()
    }

    private fun getLatestWorkerId(): UUID? =
            PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(PREF_KEY_LATEST_WORKER_ID, null)?.let { UUID.fromString(it) }

    private fun setLatestWorkerId(id: UUID) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(PREF_KEY_LATEST_WORKER_ID, id.toString())
                .apply()
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
        })
        viewModel.isLoading.observe(this, Observer {
            binding.coordinatorMain.indicatorLoading.visibility =
                    if (it == true) View.VISIBLE else View.GONE
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

        viewModel.requestedSongInQueue.observe(this, Observer {
            if (it == null) return@Observer
            player?.play(it)
        })

        bottomSheetViewModel.playbackButton.observe(this, Observer {
            if (it == null) return@Observer
            when (it) {
                BottomSheetViewModel.PlaybackButton.PLAY_OR_PAUSE -> player?.togglePlayPause()
                BottomSheetViewModel.PlaybackButton.NEXT -> player?.next()
                BottomSheetViewModel.PlaybackButton.PREV -> player?.headOrPrev()
                BottomSheetViewModel.PlaybackButton.FF -> player?.fastForward()
                BottomSheetViewModel.PlaybackButton.REWIND -> player?.rewind()
            }
        })

        bottomSheetViewModel.newSeekBarProgress.observe(this, Observer {
            if (it == null) return@Observer
            player?.seek(it)
        })

        bottomSheetViewModel.shuffle.observe(this, Observer {
            player?.shuffle()
        })

        bottomSheetViewModel.changedQueue.observe(this, Observer {
            if (it == null) return@Observer
            player?.submitQueue(PlayerService.InsertQueue(
                    PlayerService.QueueMetadata(PlayerService.InsertActionType.OVERRIDE,
                            PlayerService.OrientedClassType.SONG), it))
        })
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                binding.drawerLayout, binding.coordinatorMain.contentMain.toolbar,
                R.string.drawer_open, R.string.drawer_close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(onNavigationItemSelectedListener)
    }
}
