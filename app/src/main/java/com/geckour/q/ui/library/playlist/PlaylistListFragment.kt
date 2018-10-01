package com.geckour.q.ui.library.playlist

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import kotlin.coroutines.experimental.CoroutineContext

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

    private var parentJob = Job()
    private val bgScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = parentJob
    }
    private val uiScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = Dispatchers.Main + parentJob
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))


        if (adapter.itemCount == 0) {
            uiScope.launch {
                mainViewModel.loading.value = true
                context?.apply { adapter.setItems(fetchPlaylists(this).await()) }
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_playlist
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.playlists_toolbar, menu)
        (menu?.findItem(R.id.menu_search)?.actionView as? SearchView)?.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(newText: String?): Boolean {
                    mainViewModel.searchQuery.value = newText
                    return true
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    mainViewModel.searchQuery.value = query
                    return true
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context?.also { context ->
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

            mainViewModel.loading.value = true
            bgScope.launch {
                val songs = adapter.getItems().map { playlist ->
                    playlist.getTrackMediaIds(context)
                            .mapNotNull {
                                getSong(DB.getInstance(context),
                                        it.first,
                                        playlistId = playlist.id)
                                        .await()
                            }
                }.flatten()
                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        viewModel.requireScrollTop.observe(this) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.forceLoad.observe(this) {
            uiScope.launch {
                context?.apply {
                    mainViewModel.loading.value = true
                    adapter.setItems(fetchPlaylists(this).await())
                    binding.recyclerView.smoothScrollToPosition(0)
                    mainViewModel.loading.value = false
                }
            }
        }
    }
}