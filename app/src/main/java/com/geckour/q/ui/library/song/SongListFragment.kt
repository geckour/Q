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
import com.geckour.q.ui.MainViewModel
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
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.songs, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_insert_all_first -> Unit
            R.id.menu_insert_all_last -> Unit
            R.id.menu_override_all -> Unit
            R.id.menu_songs_insert_all_shuffle_first -> Unit
            R.id.menu_songs_insert_all_shuffle_last -> Unit
            R.id.menu_songs_override_all_shuffle -> Unit
        }
        return true
    }

    private fun fetchSongs() {
        DB.getInstance(requireContext()).also { db ->
            db.trackDao().getAll().observe(this@SongListFragment, Observer { dbTrackList ->
                if (dbTrackList == null) return@Observer

                latestDbTrackList = dbTrackList
                upsertSongListIfPossible(db, false)
            })
        }
    }

    private fun fetchSongsWithAlbum(album: Album) {
        DB.getInstance(requireContext()).also { db ->
            db.trackDao().getAll().observe(this@SongListFragment, Observer { dbTrackList ->
                if (dbTrackList == null) return@Observer

                latestDbTrackList = dbTrackList.filter { it.albumId == album.id }
                upsertSongListIfPossible(db)
            })
        }
    }

    private fun fetchSongsWithGenre(genre: Genre) {
        requireActivity().contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", genre.id),
                arrayOf(
                        MediaStore.Audio.Genres.Members._ID),
                null,
                null,
                null)?.use {
            val db = DB.getInstance(requireContext())
            val trackIdList: ArrayList<Long> = ArrayList()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres.Members._ID))
                trackIdList.add(id)
            }
            launch(UI + parentJob) {
                adapter.upsertItems(getSongListWithTrackId(db, trackIdList, genreId = genre.id).await(), false)
            }
        }
    }

    private fun fetchSongsWithPlaylist(playlist: Playlist) {
        requireActivity().contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlist.id),
                arrayOf(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID),
                null,
                null,
                null)?.use {
            val db = DB.getInstance(requireContext())
            val trackIdList: ArrayList<Long> = ArrayList()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID))
                trackIdList.add(id)
            }
            launch(UI + parentJob) {
                adapter.upsertItems(getSongListWithTrackId(db, trackIdList, playlistId = playlist.id).await())
            }
        }
    }

    private fun upsertSongListIfPossible(db: DB, sortByTrackId: Boolean = true) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI + parentJob) {
                delay(500)
                val items = getSongListWithTrack(db, latestDbTrackList).await()
                adapter.upsertItems(items, sortByTrackId)
                chatteringCancelFlag = false
            }
        }
    }

    private fun getSongListWithTrack(db: DB, dbTrackList: List<Track>): Deferred<List<Song>> =
            async(parentJob) { dbTrackList.mapNotNull { getSong(db, it).await() } }

    private fun getSongListWithTrackId(db: DB,
                                       dbTrackIdList: List<Long>,
                                       genreId: Long? = null,
                                       playlistId: Long? = null): Deferred<List<Song>> =
            async(parentJob) {
                dbTrackIdList.mapNotNull { getSong(db, it, genreId, playlistId).await() }
            }

    private fun getSong(db: DB, track: Track, genreId: Long? = null, playlistId: Long? = null): Deferred<Song?> =
            async(parentJob) {
                val artist = db.artistDao().get(track.artistId) ?: return@async null
                Song(track.id, track.albumId, track.title, artist.title, track.duration,
                        track.trackNum, track.trackTotal, track.discNum, track.discTotal,
                        genreId, playlistId)
            }

    private fun getSong(db: DB, trackId: Long,
                        genreId: Long? = null, playlistId: Long? = null): Deferred<Song?> =
            async(parentJob) {
                db.trackDao().get(trackId)?.let { getSong(db, it, genreId, playlistId).await() }
            }
}