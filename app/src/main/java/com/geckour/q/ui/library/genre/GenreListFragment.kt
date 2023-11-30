package com.geckour.q.ui.library.genre

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getThumb
import com.geckour.q.util.setIconTint
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toggleDayNight
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class GenreListFragment : Fragment() {

    companion object {
        fun newInstance(): GenreListFragment = GenreListFragment()
    }

    private val viewModel: GenreListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: GenreListAdapter = GenreListAdapter(
        onNewQueue = { genre, actionType ->
            lifecycleScope.launchWhenResumed {
                mainViewModel.onLoadStateChanged(true)
                val tracks = get<DB>().trackDao()
                    .getAllByGenreName(genre.name)
                    .mapIndexed { i, track -> track.toDomainTrack(i) }
                mainViewModel.onLoadStateChanged(false)
                mainViewModel.onNewQueue(tracks, actionType, OrientedClassType.TRACK)
            }
        },
        onClickGenre = { genre ->
            mainViewModel.onRequestNavigate(genre)
        },
        onEditMetadata = { genre ->
            lifecycleScope.launchWhenResumed {
                mainViewModel.onLoadStateChanged(true)
                val tracks = get<DB>().trackDao()
                    .getAllByGenreName(genre.name)
                mainViewModel.onLoadStateChanged(false)

                requireContext().showFileMetadataUpdateDialog(tracks) { binding ->
                    lifecycleScope.launchWhenResumed {
                        mainViewModel.onLoadStateChanged(true)
                        binding.updateFileMetadata(requireContext(), get(), tracks)
                        mainViewModel.onLoadStateChanged(false)
                    }
                }
            }
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )


        if (adapter.itemCount == 0) {
            viewModel.viewModelScope.launch {
                mainViewModel.onLoadStateChanged(true)
                adapter.submitList(fetchGenres())
                mainViewModel.onLoadStateChanged(false)
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_genre
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.genres_toolbar, menu)
        (menu.findItem(R.id.menu_search)?.actionView as? SearchView?)
            ?.let {
                mainViewModel.initSearchQueryListener(it)
                it.setOnQueryTextListener(mainViewModel.searchQueryListener)
            }

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_toggle_daynight) {
            requireActivity().toggleDayNight()
            return true
        }
//        if (item.itemId == R.id.menu_sleep) {
//            (requireActivity() as? MainActivity)?.showSleepTimerDialog()
//            return true
//        }

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
            mainViewModel.onLoadStateChanged(true)
            val tracks = adapter.currentList.flatMap { genre ->
                get<DB>().trackDao()
                    .getAllByGenreName(genre.name)
                    .mapIndexed { i, track -> track.toDomainTrack(i) }
            }
            mainViewModel.onLoadStateChanged(false)

            mainViewModel.onNewQueue(tracks, actionType, OrientedClassType.TRACK)
        }

        return true
    }

    private fun observeEvents() {
        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        mainViewModel.forceLoad.observe(viewLifecycleOwner) {
            viewModel.viewModelScope.launch {
                mainViewModel.onLoadStateChanged(true)
                adapter.submitList(fetchGenres())
                mainViewModel.onLoadStateChanged(false)
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private suspend fun fetchGenres(): List<Genre> =
        get<DB>().trackDao().getAllGenre().map { genreName ->
            val tracks = get<DB>().trackDao().getAllByGenreName(genreName)
            Genre(
                tracks.getGenreThumb(requireContext()),
                genreName,
                tracks.map { it.track.duration }.sum()
            )
        }

    private suspend fun List<JoinedTrack>.getGenreThumb(context: Context): Bitmap? =
        this.distinctBy { it.album.id }
            .take(5)
            .mapNotNull { it.album.artworkUriString }
            .getThumb(context)
}