package com.geckour.q.ui.library.artist

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.setIconTint
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toggleDayNight
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class ArtistListFragment : Fragment() {

    companion object {

        fun newInstance(): ArtistListFragment = ArtistListFragment()
    }

    private val viewModel by viewModel<ArtistListViewModel>()
    private val mainViewModel by sharedViewModel<MainViewModel>()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: ArtistListAdapter = ArtistListAdapter(
        onClickArtist = { artist ->
            mainViewModel.onRequestNavigate(artist)
        },
        onNewQueue = { actionType, artist ->
            lifecycleScope.launchWhenResumed {
                mainViewModel.onLoadStateChanged(true)
                val tracks = get<DB>().trackDao()
                    .getAllByArtist(
                        artist.id,
                        BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                    )
                    .map { it.toDomainTrack() }
                mainViewModel.onLoadStateChanged(false)

                mainViewModel.onNewQueue(tracks, actionType, OrientedClassType.ALBUM)
            }
        },
        onEditMetadata = { artist ->
            lifecycleScope.launchWhenResumed {
                mainViewModel.onLoadStateChanged(true)
                val db = get<DB>()
                val tracks = db.trackDao()
                    .getAllByArtist(artist.id)
                mainViewModel.onLoadStateChanged(false)

                requireContext().showFileMetadataUpdateDialog(tracks) { binding ->
                    lifecycleScope.launchWhenResumed {
                        mainViewModel.onLoadStateChanged(true)
                        binding.updateFileMetadata(requireContext(), db, tracks)
                        mainViewModel.onLoadStateChanged(false)
                    }
                }
            }
        },
        onDeleteArtist = { artist ->
            viewModel.deleteArtist(artist.id)
        }
    )

    private val sharedPreferences by inject<SharedPreferences>()

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
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_artist
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.artists_toolbar, menu)
        (menu.findItem(R.id.menu_search)?.actionView as? SearchView?)
            ?.let {
                mainViewModel.initSearchQueryListener(it)
                it.setOnQueryTextListener(mainViewModel.searchQueryListener)
            }

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context?.also { context ->
            if (item.itemId == R.id.menu_toggle_daynight) {
                requireActivity().toggleDayNight(sharedPreferences)
                return true
            }
            if (item.itemId == R.id.menu_sleep) {
                (requireActivity() as? MainActivity)?.showSleepTimerDialog()
                return true
            }
            if (item.itemId == R.id.menu_edit_metadata) {
                viewModel.viewModelScope.launch {
                    val db = DB.getInstance(context)

                    mainViewModel.onLoadStateChanged(true)
                    val tracks = db.trackDao().getAll()
                    mainViewModel.onLoadStateChanged(false)

                    context.showFileMetadataUpdateDialog(tracks) { binding ->
                        viewModel.viewModelScope.launch {
                            mainViewModel.onLoadStateChanged(true)
                            binding.updateFileMetadata(context, db, tracks)
                            mainViewModel.onLoadStateChanged(false)
                        }
                    }
                }
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

            lifecycleScope.launch {

                mainViewModel.onLoadStateChanged(true)
                val tracks = DB.getInstance(context)
                    .trackDao()
                    .getAll()
                    .map { it.toDomainTrack() }

                mainViewModel.onNewQueue(tracks, actionType, OrientedClassType.ARTIST)
            }
        }

        return true
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.artistListFlow.collectLatest {
                adapter.submitList(it)
            }
        }

        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.artistIdDeleted.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            adapter.onArtistDeleted(it)
        }
    }
}