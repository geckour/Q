package com.geckour.q.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import com.geckour.q.R
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.artist.SongListFragment
import com.geckour.q.util.ui
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.coroutines.experimental.Job

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(this)[MainViewModel::class.java]
    }
    internal lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private val parentJob = Job()

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
    }

    private fun observeEvents() {
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
                }
                R.id.nav_playlist -> {
                }
                R.id.nav_setting -> {
                }
            }
            ui(parentJob) { binding.drawerLayout.closeDrawers() }
            true
        }
    }
}
