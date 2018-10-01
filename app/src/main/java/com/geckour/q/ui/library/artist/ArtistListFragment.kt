package com.geckour.q.ui.library.artist

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getSong
import com.geckour.q.util.sortedByTrackOrder
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlin.coroutines.experimental.CoroutineContext

class ArtistListFragment : Fragment() {

    companion object {
        val TAG: String = ArtistListFragment::class.java.simpleName
        fun newInstance(): ArtistListFragment = ArtistListFragment()
    }

    private val viewModel: ArtistListViewModel by lazy {
        ViewModelProviders.of(requireActivity())[ArtistListViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: ArtistListAdapter by lazy { ArtistListAdapter(mainViewModel) }

    private var parentJob = Job()
    private val bgScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = parentJob
    }
    private val uiScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = Dispatchers.Main + parentJob
    }
    private var latestDbAlbumList: List<Album> = emptyList()
    private var chatteringCancelFlag: Boolean = false

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

        if (adapter.itemCount == 0) observeArtists()
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_artist
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.artists_toolbar, menu)
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
            bgScope.launch {
                val sortByTrackOrder = item.itemId.let {
                    it != R.id.menu_insert_all_simple_shuffle_next
                            || it != R.id.menu_insert_all_simple_shuffle_last
                            || it != R.id.menu_override_all_simple_shuffle
                }
                val artistAlbumMap = latestDbAlbumList.groupBy { it.artistId }
                val songs = adapter.getItems().mapNotNull {
                    artistAlbumMap[it.id]?.map {
                        DB.getInstance(context).let { db ->
                            db.trackDao().findByAlbum(it.id)
                                    .mapNotNull { getSong(db, it).await() }
                                    .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                        }
                    }?.flatten()
                }.flatten()

                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        viewModel.requireScrollTop.observe(this) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.forceLoad.observe(this) {
            context?.also { context ->
                uiScope.launch {
                    mainViewModel.loading.value = true
                    adapter.setItems(fetchArtists(DB.getInstance(context)).await())
                    binding.recyclerView.smoothScrollToPosition(0)
                    mainViewModel.loading.value = false
                }
            }
        }

        viewModel.artistIdDeleted.observe(this) {
            if (it == null) return@observe
            adapter.onArtistDeleted(it)
        }
    }

    private fun observeArtists() {
        context?.apply {
            DB.getInstance(this).also { db ->
                db.albumDao().getAllAsync().observe(this@ArtistListFragment, Observer { dbAlbumList ->
                    if (dbAlbumList == null) return@Observer

                    mainViewModel.loading.value = true
                    latestDbAlbumList = dbAlbumList
                    upsertArtistListIfPossible(db)
                })
            }
        }
    }

    private fun fetchArtists(db: DB): Deferred<List<Artist>> =
            bgScope.async { db.albumDao().getAll().getArtistList(db) }

    private fun upsertArtistListIfPossible(db: DB, albumList: List<Album> = latestDbAlbumList) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            bgScope.launch {
                delay(500)
                val items = albumList.getArtistList(db)
                uiScope.launch {
                    adapter.upsertItems(items)
                    binding.recyclerView.smoothScrollToPosition(0)
                    mainViewModel.loading.value = false
                    chatteringCancelFlag = false
                }
            }
        }
    }

    private fun List<Album>.getArtistList(db: DB): List<Artist> =
            this.asSequence()
                    .groupBy { it.artistId }
                    .map {
                        val artwork = it.value.firstOrNull { it.artworkUriString != null }
                                ?.artworkUriString
                        val totalDuration = it.value
                                .map { db.trackDao().findByAlbum(it.id) }.flatten()
                                .map { it.duration }.sum()
                        val artistName = db.artistDao().get(it.key)?.title ?: UNKNOWN
                        Artist(it.key, artistName, artwork, totalDuration)
                    }

}