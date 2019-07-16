package com.geckour.q.ui.library.playlist

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.fetchPlaylists
import com.geckour.q.util.getSong
import com.geckour.q.util.getTrackMediaIds
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toNightModeInt
import kotlinx.coroutines.launch

class PlaylistListFragment : Fragment() {

    companion object {
        fun newInstance(): PlaylistListFragment = PlaylistListFragment()
    }

    private val viewModel: PlaylistListViewModel by lazy {
        ViewModelProviders.of(requireActivity())[PlaylistListViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: PlaylistListAdapter by lazy { PlaylistListAdapter(mainViewModel) }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )


        if (adapter.itemCount == 0) {
            viewModel.viewModelScope.launch {
                mainViewModel.loading.value = true
                context?.apply { adapter.setItems(fetchPlaylists(this)) }
                mainViewModel.loading.value = false
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_playlist
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.playlists_toolbar, menu)
        (menu.findItem(R.id.menu_search)?.actionView as? SearchView?)?.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(newText: String?): Boolean {
                    mainViewModel.search(requireContext(), newText)
                    return true
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    mainViewModel.search(requireContext(), query)
                    return true
                }
            })
        }

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context?.also { context ->
            if (item.itemId == R.id.menu_toggle_daynight) {
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                val toggleTo = sharedPreferences.isNightMode.not()
                sharedPreferences.isNightMode = toggleTo
                (requireActivity() as CrashlyticsBundledActivity).delegate.localNightMode =
                        toggleTo.toNightModeInt
                return true
            }

            val actionType = when (item.itemId) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_shuffle_next -> InsertActionType.SHUFFLE_NEXT
                R.id.menu_insert_all_shuffle_last -> InsertActionType.SHUFFLE_LAST
                R.id.menu_override_all_shuffle -> InsertActionType.SHUFFLE_OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            viewModel.viewModelScope.launch {
                mainViewModel.loading.postValue(true)
                val songs = adapter.getItems().map { playlist ->
                    playlist.getTrackMediaIds(context).mapNotNull {
                        getSong(
                                DB.getInstance(context), it.first, playlistId = playlist.id
                        )
                    }
                }.apply {
                    mainViewModel.loading.postValue(false)
                }.flatten()
                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        viewModel.scrollToTop.observe(this) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.forceLoad.observe(this) {
            viewModel.viewModelScope.launch {
                context?.apply {
                    mainViewModel.loading.value = true
                    adapter.setItems(fetchPlaylists(this))
                    mainViewModel.loading.value = false
                    binding.recyclerView.smoothScrollToPosition(0)
                }
            }
        }
    }
}