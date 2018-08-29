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
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class ArtistListFragment : Fragment() {

    companion object {
        fun newInstance(): ArtistListFragment = ArtistListFragment()
    }

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private lateinit var adapter: ArtistListAdapter

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mainViewModel.onFragmentInflated(R.id.nav_artist)
        adapter = ArtistListAdapter()
        binding.recyclerView.adapter = adapter
        fetchArtistsWithPermissionCheck()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchArtists() {
        requireActivity().contentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                null, null, null, null)?.apply {
            while (moveToNext()) {
                val artist = Artist(
                        getLong(getColumnIndex(MediaStore.Audio.Artists._ID)),
                        null,
                        getString(getColumnIndex(MediaStore.Audio.Artists.ARTIST))
                )
                adapter.setItem(artist)
            }
        }
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        fetchArtistsWithPermissionCheck()
    }
}