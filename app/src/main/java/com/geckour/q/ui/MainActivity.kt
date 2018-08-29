package com.geckour.q.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import com.geckour.q.R
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.ui.library.artist.ArtistListFragment
import kotlinx.android.synthetic.main.activity_main.view.*

class MainActivity : AppCompatActivity() {


    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(this)[MainViewModel::class.java]
    }
    internal lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setSupportActionBar(binding.appBarMain.toolbar)

        setupDrawer()

        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, ArtistListFragment.newInstance())
                .commit()

        observeEvents()
    }

    private fun observeEvents() {
        viewModel.selectedNavId.observe(this, Observer {
            if (it == null) return@Observer
            binding.navigationView.navigation_view.setCheckedItem(it)
        })
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(this,
                binding.drawerLayout, binding.appBarMain.toolbar,
                R.string.drawer_open, R.string.drawer_close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.drawerLayout
    }
}
