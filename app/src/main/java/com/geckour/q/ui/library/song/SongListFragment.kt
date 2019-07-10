package com.geckour.q.ui.library.song

import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.ScopedFragment
import com.geckour.q.util.getSongListFromTrackList
import com.geckour.q.util.getSongListFromTrackMediaId
import com.geckour.q.util.getSongListFromTrackMediaIdWithTrackNum
import com.geckour.q.util.getTrackMediaIds
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toNightModeInt
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SongListFragment : ScopedFragment() {

    companion object {
        private const val ARGS_KEY_CLASS_TYPE = "args_key_class_type"
        private const val ARGS_KEY_ALBUM = "args_key_album"
        private const val ARGS_KEY_GENRE = "args_key_genre"
        private const val ARGS_KEY_PLAYLIST = "args_key_playlist"

        fun newInstance(): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.SONG)
            }
        }

        fun newInstance(album: Album): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.ALBUM)
                putParcelable(ARGS_KEY_ALBUM, album)
            }
        }

        fun newInstance(genre: Genre): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.GENRE)
                putParcelable(ARGS_KEY_GENRE, genre)
            }
        }

        fun newInstance(playlist: Playlist): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARGS_KEY_CLASS_TYPE, OrientedClassType.PLAYLIST)
                putParcelable(ARGS_KEY_PLAYLIST, playlist)
            }
        }
    }

    private val viewModel: SongListViewModel by lazy {
        ViewModelProviders.of(requireActivity())[SongListViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: SongListAdapter by lazy {
        SongListAdapter(
                mainViewModel, arguments?.getSerializable(ARGS_KEY_CLASS_TYPE)
                as? OrientedClassType ?: OrientedClassType.SONG
        )
    }
    private var chatteringCancelFlag: Boolean = false

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        GlobalScope
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

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

        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.songs_toolbar, menu)
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
        if (item.itemId == R.id.menu_toggle_daynight) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val toggleTo = sharedPreferences.isNightMode.not()
            sharedPreferences.isNightMode = toggleTo
            (requireActivity() as CrashlyticsBundledActivity).delegate.localNightMode =
                    toggleTo.toNightModeInt
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
        mainViewModel.playOrderOfPlaylistToRemove.observe(this) {
            if (it == null) return@observe
            val playlist = arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST) ?: return@observe
            val removed = context?.contentResolver?.delete(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", playlist.id),
                    "${MediaStore.Audio.Playlists.Members.PLAY_ORDER}=?",
                    arrayOf(it.toString())
            )?.equals(1) ?: return@observe
            if (removed) adapter.removeByTrackNum(it)
        }

        viewModel.scrollToTop.observe(this) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.forceLoad.observe(this) {
            adapter.clearItems()
            fetchSongs()
        }

        mainViewModel.deletedSongId.observe(this) {
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
            genre != null -> fetchSongsWithGenre(genre)
            playlist != null -> fetchSongsWithPlaylist(playlist)
            else -> observeAllSongs()
        }
    }

    private fun observeAllSongs() {
        context?.apply {
            DB.getInstance(this).also { db ->
                db.trackDao().getAllAsync().observe(this@SongListFragment) { dbTrackList ->
                    if (dbTrackList == null) return@observe

                    upsertSongListIfPossible(db, dbTrackList, false)
                }
            }
        }
    }

    private fun observeSongsWithAlbum(album: Album) {
        context?.also {
            DB.getInstance(it).also { db ->
                db.trackDao().findByAlbumAsync(album.id)
                        .observe(this@SongListFragment) { dbTrackList ->
                            if (dbTrackList == null) return@observe

                            upsertSongListIfPossible(db, dbTrackList)
                        }
            }
        }
    }

    private fun fetchSongsWithGenre(genre: Genre) {
        context?.also {
            launch {
                mainViewModel.loading.value = true
                adapter.upsertItems(
                        getSongListFromTrackMediaId(
                                DB.getInstance(it), genre.getTrackMediaIds(it), genreId = genre.id
                        ), false
                )
                mainViewModel.loading.value = false
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private fun fetchSongsWithPlaylist(playlist: Playlist) {
        context?.also {
            launch {
                mainViewModel.loading.value = true
                adapter.addItems(
                        getSongListFromTrackMediaIdWithTrackNum(
                                DB.getInstance(it), playlist.getTrackMediaIds(it), playlistId = playlist.id
                        )
                )
                mainViewModel.loading.value = false
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private fun upsertSongListIfPossible(
            db: DB, dbTrackList: List<Track>, sortByTrackOrder: Boolean = true
    ) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch {
                delay(500)
                mainViewModel.loading.value = true
                val items = getSongListFromTrackList(db, dbTrackList)
                adapter.upsertItems(items, sortByTrackOrder)
                mainViewModel.loading.value = false
                chatteringCancelFlag = false
            }
        }
    }
}