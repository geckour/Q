package com.geckour.q.ui.library.album

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)
        binding.recyclerView.adapter = adapter

        observeEvents()

        if (savedInstanceState == null && adapter.itemCount == 0)
            arguments?.getParcelable<Artist>(ARGS_KEY_ARTIST).apply { fetchAlbums(this) }
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
        mainViewModel.loading.value = false
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
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_shuffle_next -> InsertActionType.SHUFFLE_NEXT
                R.id.menu_insert_all_shuffle_last -> InsertActionType.SHUFFLE_LAST
                R.id.menu_override_all_shuffle -> InsertActionType.SHUFFLE_OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return@launch
            })
        }

        return true
    }

    private fun observeEvents() {
        mainViewModel.requireScrollTop.observe(this, Observer {
            binding.recyclerView.smoothScrollToPosition(0)
        })
    }

    private fun fetchAlbums(artist: Artist?) {
        DB.getInstance(requireContext()).also { db ->
            db.albumDao().getAllAsync().observe(this@AlbumListFragment, Observer { dbAlbumList ->
                if (dbAlbumList == null) return@Observer

                mainViewModel.loading.value = true
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
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
                chatteringCancelFlag = false
            }
        }
    }

    private fun getAlbumList(db: DB, artist: Artist?): Deferred<List<Album>> =
            async(parentJob) {
                (if (artist == null) latestDbAlbumList
                else latestDbAlbumList.filter { it.artistId == artist.id }).mapNotNull {
                    val artistName = db.artistDao().get(it.artistId)?.title ?: return@mapNotNull null
                    Album(it.id, it.mediaId, it.title, artistName, it.artworkUriString)
                }
            }
}