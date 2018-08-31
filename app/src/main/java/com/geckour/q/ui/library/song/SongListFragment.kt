package com.geckour.q.ui.library.song

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
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

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        mainViewModel.onFragmentInflated(R.id.nav_song)
        adapter = SongListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        arguments?.getParcelable<Album>(ARGS_KEY_ALBUM).apply {
            fetchSongsWithPermissionCheck(this)
        }
        arguments?.getParcelable<Genre>(ARGS_KEY_GENRE)?.apply {
            fetchSongsWithGenreWithPermissionCheck(this)
        }
        arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST)?.apply {
            fetchSongsWithPlaylistWithPermissionCheck(this)
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchSongs(album: Album?) {
        requireActivity().contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.TRACK),
                if (album == null) null else "${MediaStore.Audio.Media.ALBUM_ID}=?",
                if (album == null) null else arrayOf(album.id.toString()),
                null)?.apply {
            val list: ArrayList<Song> = ArrayList()
            while (moveToNext()) {
                val song = Song(
                        getLong(getColumnIndex(MediaStore.Audio.Media._ID)),
                        getLong(getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)),
                        getString(getColumnIndex(MediaStore.Audio.Media.TITLE)),
                        getString(getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                        getFloat(getColumnIndex(MediaStore.Audio.Media.DURATION)),
                        getInt(getColumnIndex(MediaStore.Audio.Media.TRACK)))
                list.add(song)
            }
            adapter.setItems(list.let {
                if (album == null) it.sortedBy { it.name }
                else it.sortedBy { it.trackNum }
            })
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchSongsWithGenre(genre: Genre) {
        requireActivity().contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", genre.id),
                arrayOf(
                        MediaStore.Audio.Genres.Members.AUDIO_ID,
                        MediaStore.Audio.Genres.Members.ALBUM_ID,
                        MediaStore.Audio.Genres.Members.TITLE,
                        MediaStore.Audio.Genres.Members.ARTIST,
                        MediaStore.Audio.Genres.Members.DURATION,
                        MediaStore.Audio.Genres.Members.TRACK),
                null,
                null,
                null)?.apply {
            val list: ArrayList<Song> = ArrayList()
            while (moveToNext()) {
                val song = Song(
                        getLong(getColumnIndex(MediaStore.Audio.Genres.Members.ALBUM_ID)),
                        getLong(getColumnIndex(MediaStore.Audio.Genres.Members.ALBUM_ID)),
                        getString(getColumnIndex(MediaStore.Audio.Genres.Members.TITLE)),
                        getString(getColumnIndex(MediaStore.Audio.Genres.Members.ARTIST)),
                        getFloat(getColumnIndex(MediaStore.Audio.Genres.Members.DURATION)),
                        getInt(getColumnIndex(MediaStore.Audio.Genres.Members.TRACK)))
                list.add(song)
            }
            adapter.setItems(list.sortedBy { it.name })
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchSongsWithPlaylist(playlist: Playlist) {
        requireActivity().contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlist.id),
                arrayOf(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        MediaStore.Audio.Playlists.Members.ALBUM_ID,
                        MediaStore.Audio.Playlists.Members.TITLE,
                        MediaStore.Audio.Playlists.Members.ARTIST,
                        MediaStore.Audio.Playlists.Members.DURATION,
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER),
                null,
                null,
                null)?.apply {
            val list: ArrayList<Song> = ArrayList()
            while (moveToNext()) {
                val song = Song(
                        getLong(getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM_ID)),
                        getLong(getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM_ID)),
                        getString(getColumnIndex(MediaStore.Audio.Playlists.Members.TITLE)),
                        getString(getColumnIndex(MediaStore.Audio.Playlists.Members.ARTIST)),
                        getFloat(getColumnIndex(MediaStore.Audio.Playlists.Members.DURATION)),
                        getInt(getColumnIndex(MediaStore.Audio.Playlists.Members.PLAY_ORDER)))
                list.add(song)
            }
            adapter.setItems(list.sortedBy { it.trackNum })
        }
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        arguments?.getParcelable<Album>(ARGS_KEY_ALBUM).apply {
            fetchSongsWithPermissionCheck(this)
        }
        arguments?.getParcelable<Genre>(ARGS_KEY_GENRE)?.apply {
            fetchSongsWithGenreWithPermissionCheck(this)
        }
        arguments?.getParcelable<Playlist>(ARGS_KEY_PLAYLIST)?.apply {
            fetchSongsWithPlaylistWithPermissionCheck(this)
        }
    }
}