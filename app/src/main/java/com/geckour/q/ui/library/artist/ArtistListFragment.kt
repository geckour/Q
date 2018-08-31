package com.geckour.q.ui.library.artist

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber

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

        setHasOptionsMenu(true)

        mainViewModel.onFragmentInflated(R.id.nav_artist)
        adapter = ArtistListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        fetchArtistsWithPermissionCheck()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.artists, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_insert_all_first -> Unit
            R.id.menu_insert_all_last -> Unit
            R.id.menu_override_all -> Unit
            R.id.menu_artists_insert_all_shuffle_first -> Unit
            R.id.menu_artists_insert_all_shuffle_last -> Unit
            R.id.menu_artists_override_all_shuffle -> Unit
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchArtists() {
        // アルバムアーティストで引っ張るために MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI を使う
        requireActivity().contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Albums.ARTIST,
                        MediaStore.Audio.Albums._ID),
                null, null, null)?.apply {
            val list: ArrayList<Artist> = ArrayList()
            while (moveToNext()) {
                val artist = Artist(
                        getString(getColumnIndex(MediaStore.Audio.Albums.ARTIST)),
                        getLong(getColumnIndex(MediaStore.Audio.Albums._ID)))
                list.add(artist)
            }
            adapter.setItems(list.distinctBy { it.name }.sortedBy { it.name })
        }
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        fetchArtistsWithPermissionCheck()
    }
}