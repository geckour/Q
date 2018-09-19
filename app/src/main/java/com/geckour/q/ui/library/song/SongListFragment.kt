package com.geckour.q.ui.library.song

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getSongListFromTrackId
import com.geckour.q.util.getSongListFromTrackList
import com.geckour.q.util.getTrackIds
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI

class SongListFragment : Fragment() {

    companion object {
        private const val ARGS_KEY_ALBUM = "args_key_album"
        private const val ARGS_KEY_GENRE = "args_key_genre"
        private const val ARGS_KEY_PLAYLIST = "args_key_playlist"

        fun newInstance(): SongListFragment = SongListFragment()

        fun newInstance(album: Album): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARGS_KEY_ALBUM, album)
            }
        }

        fun newInstance(genre: Genre): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARGS_KEY_GENRE, genre)
            }
        }

        fun newInstance(playlist: Playlist): SongListFragment = SongListFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARGS_KEY_PLAYLIST, playlist)
            }
        }
    }

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private lateinit var adapter: SongListAdapter

    private var parentJob = Job()
    private var latestDbTrackList: List<Track> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        adapter = SongListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter

        val album = arguments?.getParcelable<Album>(ARGS_KEY_ALBUM)
        val genre = arguments?.getParcelable<Genre>(ARGS_KEY_GENRE)
        val playlist = arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST)

        when {
            album != null -> fetchSongsWithAlbum(album)
            genre != null -> fetchSongsWithGenre(genre)
            playlist != null -> fetchSongsWithPlaylist(playlist)
            else -> fetchSongs()
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_song
        parentJob.cancel()
        parentJob = Job()
    }

    override fun onPause() {
        super.onPause()
        parentJob.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.songs, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        adapter.onNewQueue(when (item.itemId) {
            R.id.menu_insert_all_next -> PlayerService.InsertActionType.NEXT
            R.id.menu_insert_all_last -> PlayerService.InsertActionType.LAST
            R.id.menu_override_all -> PlayerService.InsertActionType.OVERRIDE
            R.id.menu_insert_all_simple_shuffle_next -> PlayerService.InsertActionType.SHUFFLE_SIMPLE_NEXT
            R.id.menu_insert_all_simple_shuffle_last -> PlayerService.InsertActionType.SHUFFLE_SIMPLE_LAST
            R.id.menu_override_all_simple_shuffle -> PlayerService.InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
            else -> return false
        })

        return true
    }

    private fun fetchSongs() {
        DB.getInstance(requireContext()).also { db ->
            db.trackDao().getAllAsync().observe(this@SongListFragment, Observer { dbTrackList ->
                if (dbTrackList == null) return@Observer

                latestDbTrackList = dbTrackList
                upsertSongListIfPossible(db, false)
            })
        }
    }

    private fun fetchSongsWithAlbum(album: Album) {
        launch(parentJob) {
            DB.getInstance(requireContext()).also { db ->
                latestDbTrackList = db.trackDao().findByAlbum(album.id)
                upsertSongListIfPossible(db)
            }
        }
    }

    private fun fetchSongsWithGenre(genre: Genre) {
        launch(UI + parentJob) {
            adapter.upsertItems(getSongListFromTrackId(DB.getInstance(requireContext()),
                    genre.getTrackIds(requireContext()),
                    genreId = genre.id)
                    .await(),
                    false)
        }
    }

    private fun fetchSongsWithPlaylist(playlist: Playlist) {
        launch(UI + parentJob) {
            adapter.upsertItems(getSongListFromTrackId(DB.getInstance(requireContext()),
                    playlist.getTrackIds(requireContext()),
                    playlistId = playlist.id)
                    .await())
        }
    }

    private fun upsertSongListIfPossible(db: DB, sortByTrackOrder: Boolean = true) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI + parentJob) {
                delay(500)
                val items = getSongListFromTrackList(db, latestDbTrackList).await()
                adapter.upsertItems(items, sortByTrackOrder)
                chatteringCancelFlag = false
            }
        }
    }
}