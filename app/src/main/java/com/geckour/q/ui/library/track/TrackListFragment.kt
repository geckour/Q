package com.geckour.q.ui.library.track

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getSongListFromTrackMediaId
import com.geckour.q.util.getDomainTrackListFromTrackMediaIdWithTrackNum
import com.geckour.q.util.getTrackMediaIds
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toggleDayNight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrackListFragment : Fragment() {

    companion object {

        private const val ARGS_KEY_CLASS_TYPE = "args_key_class_type"
        private const val ARGS_KEY_ALBUM = "args_key_album"
        private const val ARGS_KEY_GENRE = "args_key_genre"
        private const val ARGS_KEY_PLAYLIST = "args_key_playlist"

        fun newInstance(): TrackListFragment = TrackListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.SONG)
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

        fun newInstance(playlist: Playlist): TrackListFragment = TrackListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.PLAYLIST)
                putParcelable(ARGS_KEY_PLAYLIST, playlist)
            }
        }
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: TrackListAdapter by lazy {
        TrackListAdapter(
            mainViewModel,
            arguments?.getSerializable(ARGS_KEY_CLASS_TYPE) as? OrientedClassType
                ?: OrientedClassType.SONG
        )
    }

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

        if (adapter.itemCount == 0) fetchSongs()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_song
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.songs_toolbar, menu)
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

        adapter.onNewQueue(
            requireContext(), when (item.itemId) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }
        )

        return true
    }

    private fun observeEvents() {
        mainViewModel.playOrderToRemoveFromPlaylist.observe(viewLifecycleOwner) {
            it ?: return@observe

            val playlist = arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST) ?: return@observe
            val removed = context?.contentResolver?.delete(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlist.id),
                "${MediaStore.Audio.Playlists.Members.PLAY_ORDER}=?",
                arrayOf(it.toString())
            )?.equals(1) ?: return@observe
            if (removed) adapter.removeByTrackNum(it)
        }

        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        mainViewModel.forceLoad.observe(viewLifecycleOwner) {
            adapter.clearItems()
            fetchSongs()
        }

        mainViewModel.deletedSongId.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            adapter.onSongDeleted(it)
        }
    }

    private fun fetchSongs() {
        val album = arguments?.getParcelable<Album>(ARGS_KEY_ALBUM)
        val genre = arguments?.getParcelable<Genre>(ARGS_KEY_GENRE)
        val playlist = arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST)

        when {
            album != null -> observeSongsWithAlbum(album)
            genre != null -> loadSongsWithGenre(genre)
            playlist != null -> loadSongsWithPlaylist(playlist)
            else -> observeAllSongs()
        }
    }

    private fun observeAllSongs() {
        lifecycleScope.launch {
            DB.getInstance(requireContext()).trackDao().getAllAsync()
                .collectLatest { joinedTracks ->
                    adapter.submitList(joinedTracks.map { it.toDomainTrack() })
                }
        }
    }

    private fun observeSongsWithAlbum(album: Album) {
        lifecycleScope.launch {
            DB.getInstance(requireContext()).trackDao()
                .getAllByAlbumAsync(album.id)
                .collectLatest { joinedTracks ->
                    adapter.submitList(joinedTracks.map { it.toDomainTrack() }.sortedByTrackOrder())
                }
        }
    }

    private fun loadSongsWithGenre(genre: Genre) {
        lifecycleScope.launch {
            adapter.submitList(
                getSongListFromTrackMediaId(
                    DB.getInstance(requireContext()),
                    genre.getTrackMediaIds(requireContext()),
                    genreId = genre.id
                )
            )
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun loadSongsWithPlaylist(playlist: Playlist) {
        lifecycleScope.launch {
            adapter.submitList(
                getDomainTrackListFromTrackMediaIdWithTrackNum(
                    DB.getInstance(requireContext()),
                    playlist.getTrackMediaIds(requireContext()),
                    playlistId = playlist.id
                )
            )
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }
}