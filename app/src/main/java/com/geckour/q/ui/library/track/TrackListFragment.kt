package com.geckour.q.ui.library.track

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
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Bool
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.setIconTint
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toggleDayNight
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class TrackListFragment : Fragment() {

    companion object {

        private const val ARGS_KEY_CLASS_TYPE = "args_key_class_type"
        private const val ARGS_KEY_ALBUM = "args_key_album"
        private const val ARGS_KEY_GENRE = "args_key_genre"

        fun newInstance(): TrackListFragment = TrackListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.TRACK)
            }
        }

        fun newInstance(album: Album): TrackListFragment = TrackListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.ALBUM)
                putParcelable(ARGS_KEY_ALBUM, album)
            }
        }

        fun newInstance(genre: Genre): TrackListFragment = TrackListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.GENRE)
                putParcelable(ARGS_KEY_GENRE, genre)
            }
        }
    }

    private val viewModel by viewModel<TrackListViewModel>()
    private val mainViewModel by sharedViewModel<MainViewModel>()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: TrackListAdapter = TrackListAdapter(
        onNewQueue = { actionType, track ->
            mainViewModel.onNewQueue(listOf(track), actionType, OrientedClassType.TRACK)
        },
        onEditMetadata = { track ->
            lifecycleScope.launchWhenResumed {
                val db = DB.getInstance(binding.root.context)

                mainViewModel.onLoadStateChanged(true)
                val tracks =
                    db.trackDao().get(track.id)?.let { listOf(it) }.orEmpty()
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
        onTransitToArtist = { artist ->
            mainViewModel.selectedArtist.value = artist
        },
        onTransitToAlbum = { album ->
            mainViewModel.selectedAlbum.value = album
        },
        onDeleteTrack = { track ->
            mainViewModel.deleteTrack(track)
        },
        onClickTrack = {
            mainViewModel.onRequestNavigate()
        },
        onToggleIgnored = { track ->
            lifecycleScope.launchWhenResumed {
                get<DB>().trackDao().apply {
                    val ignored = when (checkNotNull(this.get(track.id)).track.ignored) {
                        Bool.TRUE -> Bool.FALSE
                        Bool.FALSE, Bool.UNDEFINED -> Bool.TRUE
                    }
                    setIgnored(track.id, ignored)
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

        if (adapter.itemCount == 0) fetchTracks()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_track
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        val album = arguments?.getParcelable<Album>(ARGS_KEY_ALBUM)
        val genre = arguments?.getParcelable<Genre>(ARGS_KEY_GENRE)

        val menuRes = when {
            album != null -> R.menu.album_toolbar
            genre != null -> R.menu.genre_toolbar
            else -> null
        }
        menuRes?.let {
            inflater.inflate(it, menu)
        }
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
        if (item.itemId == R.id.menu_sleep) {
            (requireActivity() as? MainActivity)?.showSleepTimerDialog()
            return true
        }
        if (item.itemId == R.id.menu_edit_metadata) {
            mainViewModel.viewModelScope.launch {
                val db = DB.getInstance(requireContext())

                mainViewModel.onLoadStateChanged(true)
                val tracks = adapter.currentList.mapNotNull { db.trackDao().get(it.id) }
                mainViewModel.onLoadStateChanged(false)

                requireContext().showFileMetadataUpdateDialog(tracks) { binding ->
                    mainViewModel.viewModelScope.launch {
                        mainViewModel.onLoadStateChanged(true)
                        binding.updateFileMetadata(requireContext(), db, tracks)
                        mainViewModel.onLoadStateChanged(false)
                    }
                }
            }
            return true
        }
        if (item.itemId == R.id.menu_delete_album) {
            arguments?.getParcelable<Album>(ARGS_KEY_ALBUM)?.let {
                viewModel.deleteAlbum(it.id)
            }
            return true
        }

        mainViewModel.onNewQueue(
            adapter.currentList.let { tracks ->
                if (get<SharedPreferences>().ignoringEnabled) tracks.filter { it.ignored != true }
                else tracks
            },
            when (item.itemId) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            },
            OrientedClassType.TRACK
        )

        return true
    }

    private fun observeEvents() {
        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        mainViewModel.forceLoad.observe(viewLifecycleOwner) {
            adapter.clearItems()
            fetchTracks()
        }
    }

    private fun fetchTracks() {
        val album = arguments?.getParcelable<Album>(ARGS_KEY_ALBUM)
        val genre = arguments?.getParcelable<Genre>(ARGS_KEY_GENRE)

        when {
            album != null -> observeTrackWithAlbum(album)
            genre != null -> loadTracksWithGenre(genre)
            else -> observeAllTracks()
        }
    }

    private fun observeAllTracks() {
        lifecycleScope.launch {
            get<DB>().trackDao().getAllAsync()
                .collect { joinedTracks ->
                    adapter.submitList(joinedTracks.map { it.toDomainTrack() })
                }
        }
    }

    private fun observeTrackWithAlbum(album: Album) {
        lifecycleScope.launch {
            get<DB>().trackDao()
                .getAllByAlbumAsync(album.id)
                .collect { joinedTracks ->
                    adapter.submitList(
                        joinedTracks.map { it.toDomainTrack() }
                            .sortedByTrackOrder(OrientedClassType.TRACK, InsertActionType.LAST)
                    )
                }
        }
    }

    private fun loadTracksWithGenre(genre: Genre) {
        lifecycleScope.launch {
            adapter.submitList(
                get<DB>().trackDao()
                    .getAllByGenreName(genre.name)
                    .mapIndexed { i, track -> track.toDomainTrack(i) }
            )
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }
}