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
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getSong
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
    private val adapter: AlbumListAdapter by lazy { AlbumListAdapter(mainViewModel) }

    private var latestDbAlbumList: List<DbAlbum> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    private var parentJob = Job()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (adapter.itemCount == 0)
            arguments?.getParcelable<Artist>(ARGS_KEY_ARTIST).apply { fetchAlbums(this) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)
        binding.recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_album
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.albums, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        launch {
            val songs = adapter.getItems().map {
                DB.getInstance(requireContext()).let { db ->
                    db.trackDao().findByAlbum(it.id).mapNotNull { getSong(db, it).await() }
                }
            }.flatten()

            adapter.onNewQueue(songs, when (item.itemId) {
                R.id.menu_insert_all_next -> PlayerService.InsertActionType.NEXT
                R.id.menu_insert_all_last -> PlayerService.InsertActionType.LAST
                R.id.menu_override_all -> PlayerService.InsertActionType.OVERRIDE
                R.id.menu_insert_all_shuffle_next -> PlayerService.InsertActionType.SHUFFLE_NEXT
                R.id.menu_insert_all_shuffle_last -> PlayerService.InsertActionType.SHUFFLE_LAST
                R.id.menu_override_all_shuffle -> PlayerService.InsertActionType.SHUFFLE_OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> PlayerService.InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> PlayerService.InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> PlayerService.InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return@launch
            })
        }

        return true
    }

    private fun fetchAlbums(artist: Artist?) {
        DB.getInstance(requireContext()).also { db ->
            db.albumDao().getAllAsync().observe(this@AlbumListFragment, Observer { dbAlbumList ->
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