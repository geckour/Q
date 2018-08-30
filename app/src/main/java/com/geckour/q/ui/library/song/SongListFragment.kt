package com.geckour.q.ui.library.artist

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.q.R
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class SongListFragment : Fragment() {

    companion object {
        private const val ARGS_KEY_ALBUM = "args_key_album"
        fun newInstance(album: Album? = null): SongListFragment = SongListFragment().apply {
            if (album != null) {
                arguments = Bundle().apply {
                    putParcelable(ARGS_KEY_ALBUM, album)
                }
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

        mainViewModel.onFragmentInflated(R.id.nav_song)
        adapter = SongListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        arguments?.getParcelable<Album>(ARGS_KEY_ALBUM).apply {
            fetchSongsWithPermissionCheck(this)
        }
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

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        arguments?.getParcelable<Album>(ARGS_KEY_ALBUM).apply {
            fetchSongsWithPermissionCheck(this)
        }
    }
}