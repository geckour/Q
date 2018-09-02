package com.geckour.q.ui.library.album

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import com.geckour.q.data.db.model.Album as DbAlbum

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

    private var latestDbAlbumList: List<DbAlbum> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    private var parentJob = Job()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        mainViewModel.onFragmentInflated(R.id.nav_album)
        adapter = AlbumListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        arguments?.getParcelable<Artist>(ARGS_KEY_ARTIST).apply { fetchAlbums(this) }
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.albums, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_insert_all_first -> Unit
            R.id.menu_insert_all_last -> Unit
            R.id.menu_override_all -> Unit
            R.id.menu_albums_insert_all_shuffle_first -> Unit
            R.id.menu_albums_insert_all_shuffle_last -> Unit
            R.id.menu_albums_override_all_shuffle -> Unit
        }
        return true
    }

    private fun fetchAlbums(artist: Artist?) {
        DB.getInstance(requireContext()).also { db ->
            db.albumDao().getAll().observe(this@AlbumListFragment, Observer { dbAlbumList ->
                if (dbAlbumList == null) return@Observer

                latestDbAlbumList = dbAlbumList
                upsertArtistListIfPossible(db, artist)
            })
        }
    }

    private fun upsertArtistListIfPossible(db: DB, artist: Artist?) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI) {
                delay(500)
                val items = getAlbumList(db, artist).await()
                adapter.upsertItems(items)
                chatteringCancelFlag = false
            }
        }
    }

    private fun getAlbumList(db: DB, artist: Artist?): Deferred<List<Album>> =
            async(parentJob) {
                (if (artist == null) latestDbAlbumList
                else latestDbAlbumList.filter { it.artistId == artist.id }).mapNotNull {
                    val artistForAlbum = db.artistDao().get(it.artistId) ?: return@mapNotNull null
                    Album(it.id, it.title, artistForAlbum.title)
                }
            }
}