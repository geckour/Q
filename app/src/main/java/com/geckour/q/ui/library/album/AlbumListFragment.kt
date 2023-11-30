package com.geckour.q.ui.library.album

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
import com.geckour.q.data.db.model.Artist
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
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class AlbumListFragment : Fragment() {

    companion object {
        private const val ARGS_KEY_ARTIST = "args_key_artist"
        fun newInstance(artist: Artist? = null): AlbumListFragment = AlbumListFragment().apply {
            if (artist != null) {
                arguments = Bundle().apply {
                    putParcelable(ARGS_KEY_ARTIST, artist)
                }
            }
        }
    }

    private val viewModel by viewModel<AlbumListViewModel> {
        parametersOf(arguments?.getParcelable(ARGS_KEY_ARTIST) as Artist?)
    }
    private val mainViewModel by sharedViewModel<MainViewModel>()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: AlbumListAdapter = AlbumListAdapter(
        onClickAlbum = { album ->
            mainViewModel.onRequestNavigate(album)
        },
        onNewQueue = { actionType, album ->
            mainViewModel.onTrackMenuAction(actionType, album)
        },
        onEditMetadata = { album ->
            lifecycleScope.launchWhenResumed {
                mainViewModel.onLoadStateChanged(true)
                val db = get<DB>()
                val tracks = db.trackDao().getAllByAlbum(album.id)
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
        onDeleteAlbum = { album ->
            viewModel.deleteAlbum(album.id)
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
        if (adapter.itemCount == 0) observeAlbums()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_album
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.artist_toolbar, menu)
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
//            if (item.itemId == R.id.menu_sleep) {
//                (requireActivity() as? MainActivity)?.showSleepTimerDialog()
//                return true
//            }
            if (item.itemId == R.id.menu_edit_metadata) {
                viewModel.viewModelScope.launch {
                    val db = DB.getInstance(context)

                    mainViewModel.onLoadStateChanged(true)
                    val tracks = adapter.currentList.flatMap { joinedAlbum ->
                        db.trackDao().getAllByAlbum(joinedAlbum.album.id)
                    }
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
            if (item.itemId == R.id.menu_delete_artist) {
                viewModel.deleteArtist()
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
                val sortByTrackOrder = item.itemId.let {
                    it != R.id.menu_insert_all_simple_shuffle_next || it != R.id.menu_insert_all_simple_shuffle_last || it != R.id.menu_override_all_simple_shuffle
                }
                mainViewModel.onLoadStateChanged(true)
                val tracks = adapter.currentList.flatMap { joinedAlbum ->
                    DB.getInstance(context).let { db ->
                        db.trackDao()
                            .let {
                                if (sortByTrackOrder) {
                                    it.getAllByAlbumSorted(
                                        joinedAlbum.album.id,
                                        BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                                    )
                                } else {
                                    it.getAllByAlbum(
                                        joinedAlbum.album.id,
                                        BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                                    )
                                }
                            }
                            .map { it.toDomainTrack() }
                    }
                }
                mainViewModel.onLoadStateChanged(false)

                mainViewModel.onNewQueue(tracks, actionType, OrientedClassType.ALBUM)
            }
        }

        return true
    }

    private fun observeEvents() {
        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.albumIdDeleted.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            adapter.onAlbumDeleted(it)
        }
    }

    private fun observeAlbums() {
        lifecycleScope.launch {
            viewModel.albumListFlow.collect {
                adapter.submitList(it)
            }
        }
    }
}