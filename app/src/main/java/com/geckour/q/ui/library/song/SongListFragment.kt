package com.geckour.q.ui.library.song

import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

class SongListFragment : Fragment() {

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
        SongListAdapter(mainViewModel,
                arguments?.getSerializable(ARGS_KEY_CLASS_TYPE)
                        as? OrientedClassType ?: OrientedClassType.SONG)
    }

    private var parentJob = Job()
    private var chatteringCancelFlag: Boolean = false

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

        if (adapter.itemCount == 0) fetchSongs()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_song
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
        inflater?.inflate(R.menu.songs_toolbar, menu)
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
        adapter.onNewQueue(when (item.itemId) {
            R.id.menu_insert_all_next -> InsertActionType.NEXT
            R.id.menu_insert_all_last -> InsertActionType.LAST
            R.id.menu_override_all -> InsertActionType.OVERRIDE
            R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
            R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
            R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
            else -> return false
        })

        return true
    }

    private fun observeEvents() {
        mainViewModel.removePlayOrderOfPlaylist.observe(this, Observer {
            if (it == null) return@Observer
            val playlist = arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST) ?: return@Observer
            val removed = context?.contentResolver
                    ?.delete(MediaStore.Audio.Playlists.Members.getContentUri("external", playlist.id),
                            "${MediaStore.Audio.Playlists.Members.PLAY_ORDER}=?",
                            arrayOf(it.toString()))?.equals(1) ?: return@Observer
            if (removed) adapter.removeByTrackNum(it)
        })

        viewModel.requireScrollTop.observe(this, Observer {
            binding.recyclerView.smoothScrollToPosition(0)
        })

        viewModel.forceLoad.observe(this, Observer {
            adapter.clearItems()
            fetchSongs()
        })

        viewModel.songIdDeleted.observe(this, Observer {
            if (it == null) return@Observer
            adapter.onSongDeleted(it)
        })
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
                db.trackDao().getAllAsync().observe(this@SongListFragment, Observer { dbTrackList ->
                    if (dbTrackList == null) return@Observer

                    mainViewModel.loading.value = true
                    upsertSongListIfPossible(db, dbTrackList, false)
                })
            }
        }
    }

    private fun observeSongsWithAlbum(album: Album) {
        mainViewModel.loading.value = true
        context?.also {
            DB.getInstance(it).also { db ->
                db.trackDao().findByAlbumAsync(album.id).observe(this@SongListFragment, Observer { dbTrackList ->
                    if (dbTrackList == null) return@Observer

                    mainViewModel.loading.value = true
                    upsertSongListIfPossible(db, dbTrackList)
                })
            }
        }
    }

    private fun fetchSongsWithGenre(genre: Genre) {
        mainViewModel.loading.value = true
        context?.also {
            launch(UI + parentJob) {
                adapter.upsertItems(
                        getSongListFromTrackMediaId(DB.getInstance(it),
                                genre.getTrackMediaIds(it),
                                genreId = genre.id), false)
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
            }
        }
    }

    private fun fetchSongsWithPlaylist(playlist: Playlist) {
        mainViewModel.loading.value = true
        context?.also {
            launch(UI + parentJob) {
                adapter.addItems(
                        getSongListFromTrackMediaIdWithTrackNum(DB.getInstance(it),
                                playlist.getTrackMediaIds(it),
                                playlistId = playlist.id)
                )
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
            }
        }
    }

    private fun upsertSongListIfPossible(db: DB, dbTrackList: List<Track>, sortByTrackOrder: Boolean = true) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI + parentJob) {
                delay(500)
                val items = getSongListFromTrackList(db, dbTrackList)
                adapter.upsertItems(items, sortByTrackOrder)
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
                chatteringCancelFlag = false
            }
        }
    }
}