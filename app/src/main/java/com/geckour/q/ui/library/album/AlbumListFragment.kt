package com.geckour.q.ui.library.album

import android.os.Bundle
import android.view.*
import android.widget.SearchView
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
import com.geckour.q.util.sortedByTrackOrder
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import com.geckour.q.data.db.model.Album as DBAlbum

class AlbumListFragment : Fragment() {

    companion object {
        val TAG: String = AlbumListFragment::class.java.simpleName
        private const val ARGS_KEY_ARTIST = "args_key_artist"
        fun newInstance(artist: Artist? = null): AlbumListFragment = AlbumListFragment().apply {
            if (artist != null) {
                arguments = Bundle().apply {
                    putParcelable(ARGS_KEY_ARTIST, artist)
                }
            }
        }
    }

    private val viewModel: AlbumListViewModel by lazy {
        ViewModelProviders.of(requireActivity())[AlbumListViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: AlbumListAdapter by lazy { AlbumListAdapter(mainViewModel) }

    private var latestDbAlbumList: List<DBAlbum> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    private var parentJob = Job()

    private var artist: Artist? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)
        binding.recyclerView.adapter = adapter

        artist = arguments?.getParcelable(ARGS_KEY_ARTIST)
        if (adapter.itemCount == 0) observeAlbums(artist)
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
        inflater?.inflate(R.menu.albums_toolbar, menu)
        (menu?.findItem(R.id.menu_search)?.actionView as? SearchView)?.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(newText: String?): Boolean {
                    mainViewModel.searchQuery.value = newText
                    return true
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    mainViewModel.searchQuery.value = query
                    return true
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context?.also { context ->
            val actionType = when (item.itemId) {
                R.id.menu_insert_all_next -> InsertActionType.NEXT
                R.id.menu_insert_all_last -> InsertActionType.LAST
                R.id.menu_override_all -> InsertActionType.OVERRIDE
                R.id.menu_insert_all_shuffle_next -> InsertActionType.SHUFFLE_NEXT
                R.id.menu_insert_all_shuffle_last -> InsertActionType.SHUFFLE_LAST
                R.id.menu_override_all_shuffle -> InsertActionType.SHUFFLE_OVERRIDE
                R.id.menu_insert_all_simple_shuffle_next -> InsertActionType.SHUFFLE_SIMPLE_NEXT
                R.id.menu_insert_all_simple_shuffle_last -> InsertActionType.SHUFFLE_SIMPLE_LAST
                R.id.menu_override_all_simple_shuffle -> InsertActionType.SHUFFLE_SIMPLE_OVERRIDE
                else -> return false
            }

            mainViewModel.loading.value = true
            launch(parentJob) {
                val sortByTrackOrder = item.itemId.let {
                    it != R.id.menu_insert_all_simple_shuffle_next
                            || it != R.id.menu_insert_all_simple_shuffle_last
                            || it != R.id.menu_override_all_simple_shuffle
                }
                val songs = adapter.getItems().map {
                    DB.getInstance(context).let { db ->
                        db.trackDao().findByAlbum(it.id)
                                .mapNotNull { getSong(db, it).await() }
                                .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                    }
                }.flatten()

                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        viewModel.requireScrollTop.observe(this, Observer {
            binding.recyclerView.smoothScrollToPosition(0)
        })

        viewModel.forceLoad.observe(this, Observer {
            context?.also { context ->
                launch(UI + parentJob) {
                    mainViewModel.loading.value = true
                    val items = fetchAlbums(DB.getInstance(context)).await()
                    adapter.setItems(items)
                    binding.recyclerView.smoothScrollToPosition(0)
                    mainViewModel.loading.value = false
                }
            }
        })

        viewModel.albumIdDeleted.observe(this, Observer {
            if (it == null) return@Observer
            adapter.onAlbumDeleted(it)
        })
    }

    private fun observeAlbums(artist: Artist?) {
        context?.apply {
            DB.getInstance(this).also { db ->
                db.albumDao().getAllAsync().observe(this@AlbumListFragment, Observer { dbAlbumList ->
                    if (dbAlbumList == null) return@Observer

                    mainViewModel.loading.value = true
                    latestDbAlbumList = dbAlbumList.let {
                        if (artist != null) it.filter { it.artistId == artist.id } else it
                    }
                    upsertAlbumListIfPossible(db)
                })
            }
        }
    }

    private fun fetchAlbums(db: DB): Deferred<List<Album>> =
            async(parentJob) { db.albumDao().getAll().getAlbumList(db).await() }

    private fun upsertAlbumListIfPossible(db: DB) {
        launch(UI) {
            val items = latestDbAlbumList.getAlbumList(db).await()
            upsertAlbumListIfPossible(items)
        }
    }

    private fun upsertAlbumListIfPossible(items: List<Album>) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI) {
                delay(500)
                adapter.upsertItems(items)
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
                chatteringCancelFlag = false
            }
        }
    }

    private fun List<DBAlbum>.getAlbumList(db: DB): Deferred<List<Album>> =
            async(parentJob) {
                this@getAlbumList.mapNotNull {
                    val artistName = db.artistDao().get(it.artistId)?.title
                            ?: return@mapNotNull null
                    val totalDuration = db.trackDao().findByAlbum(it.id).map { it.duration }.sum()
                    Album(it.id, it.mediaId,
                            it.title, artistName, it.artworkUriString, totalDuration)
                }
            }
}