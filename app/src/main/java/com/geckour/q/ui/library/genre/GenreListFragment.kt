package com.geckour.q.ui.library.genre

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GenreListFragment : ScopedFragment() {

    companion object {
        fun newInstance(): GenreListFragment = GenreListFragment()
    }

    private val viewModel: GenreListViewModel by lazy {
        ViewModelProviders.of(requireActivity())[GenreListViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: GenreListAdapter by lazy { GenreListAdapter(mainViewModel) }

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


        if (adapter.itemCount == 0) loadItems()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_genre
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.genres_toolbar, menu)
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
            launch(Dispatchers.IO) {
                val songs = adapter.getItems().map { genre ->
                    genre.getTrackMediaIds(context).mapNotNull {
                        getSong(DB.getInstance(context), it, genreId = genre.id)
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

        viewModel.forceLoad.observe(this) { loadItems() }
    }

    private fun loadItems() {
        launch {
            mainViewModel.loading.value = true
            adapter.setItems(fetchGenres(context))
            binding.recyclerView.smoothScrollToPosition(0)
            mainViewModel.loading.value = false
        }
    }
}