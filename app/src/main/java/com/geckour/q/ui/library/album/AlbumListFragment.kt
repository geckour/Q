package com.geckour.q.ui.library.album

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
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
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

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private lateinit var adapter: AlbumListAdapter

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mainViewModel.onFragmentInflated(R.id.nav_album)
        adapter = AlbumListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        arguments?.getParcelable<Artist>(ARGS_KEY_ARTIST).apply {
            fetchAlbumsWithPermissionCheck(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchAlbums(artist: Artist?) {
        requireActivity().contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST),
                if (artist == null) null else "${MediaStore.Audio.Albums.ARTIST}=?",
                if (artist == null) null else arrayOf(artist.name),
                null)?.apply {
            val list: ArrayList<Album> = ArrayList()
            while (moveToNext()) {
                val album = Album(
                        getLong(getColumnIndex(MediaStore.Audio.Albums._ID)),
                        getString(getColumnIndex(MediaStore.Audio.Albums.ALBUM)),
                        getString(getColumnIndex(MediaStore.Audio.Albums.ARTIST))
                )
                list.add(album)
            }
            adapter.setItems(list.sortedBy { it.name })
        }
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        arguments?.getParcelable<Artist>(ARGS_KEY_ARTIST)?.apply {
            fetchAlbumsWithPermissionCheck(this)
        }
    }
}