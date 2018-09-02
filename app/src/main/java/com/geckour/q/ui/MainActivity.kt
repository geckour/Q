package com.geckour.q.ui

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.View
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.State
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.playlist.PlaylistListFragment
import com.geckour.q.ui.library.song.SongListFragment
import com.geckour.q.util.MediaRetrieveWorker
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import java.util.*

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREF_KEY_LATEST_WORKER_ID = "pref_key_latest_worker_id"
    }

    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(this)[MainViewModel::class.java]
    }
    internal lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var parentJob = Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setSupportActionBar(binding.coordinatorMain.contentMain.toolbar)

        setupDrawer()

        // TODO: 設定画面でどの画面を初期画面にするか設定できるようにする
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, ArtistListFragment.newInstance())
                .commit()

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
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
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
                    .getAll()
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
        viewModel.isLoading.observe(this, Observer {
            binding.coordinatorMain.indicatorLoading.visibility =
                    if (it == true) View.VISIBLE else View.GONE
        })

        viewModel.selectedNavId.observe(this, Observer {
            if (it == null) return@Observer
            binding.navigationView.navigation_view.setCheckedItem(it)
            when (it) {
                R.id.nav_artist -> supportActionBar?.title = getString(R.string.nav_artist)
                R.id.nav_album -> {
                    if (viewModel.selectedArtist.value == null)
                        supportActionBar?.title = getString(R.string.nav_album)
                }
                R.id.nav_song -> {
                    if (viewModel.selectedAlbum.value == null)
                        supportActionBar?.title = getString(R.string.nav_song)
                }
                R.id.nav_genre -> supportActionBar?.title = getString(R.string.nav_genre)
                R.id.nav_playlist -> supportActionBar?.title = getString(R.string.nav_playlist)
                R.id.nav_setting -> supportActionBar?.title = getString(R.string.nav_setting)
                else -> R.string.app_name
            }

        })

        viewModel.selectedArtist.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AlbumListFragment.newInstance(it))
                    .addToBackStack(null)
                    .commit()
            supportActionBar?.title = it.name
        })

        viewModel.selectedAlbum.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SongListFragment.newInstance(it))
                    .addToBackStack(null)
                    .commit()
            supportActionBar?.title = it.name
        })

        viewModel.selectedGenre.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SongListFragment.newInstance(it))
                    .addToBackStack(null)
                    .commit()
            supportActionBar?.title = it.name
        })

        viewModel.selectedPlaylist.observe(this, Observer {
            if (it == null) return@Observer
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SongListFragment.newInstance(it))
                    .addToBackStack(null)
                    .commit()
            supportActionBar?.title = it.name
        })
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                binding.drawerLayout, binding.coordinatorMain.contentMain.toolbar,
                R.string.drawer_open, R.string.drawer_close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_artist -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, ArtistListFragment.newInstance())
                            .addToBackStack(null)
                            .commit()
                }
                R.id.nav_album -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, AlbumListFragment.newInstance())
                            .addToBackStack(null)
                            .commit()
                }
                R.id.nav_song -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, SongListFragment.newInstance())
                            .addToBackStack(null)
                            .commit()
                }
                R.id.nav_genre -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, GenreListFragment.newInstance())
                            .addToBackStack(null)
                            .commit()
                }
                R.id.nav_playlist -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, PlaylistListFragment.newInstance())
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
    }
}
