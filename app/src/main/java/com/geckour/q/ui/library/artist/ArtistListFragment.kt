package com.geckour.q.ui.library.artist

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Album
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.ScopedFragment
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getSong
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.isNightMode
import com.geckour.q.util.setIconTint
import com.geckour.q.util.sortedByTrackOrder
import com.geckour.q.util.toNightModeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistListFragment : ScopedFragment() {

    companion object {
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

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private var latestDbAlbumList: List<Album> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
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

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_artist
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.artists_toolbar, menu)
        (menu.findItem(R.id.menu_search)?.actionView as? SearchView?)?.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(newText: String?): Boolean {
                    mainViewModel.search(requireContext(), newText)
                    return true
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    mainViewModel.search(requireContext(), query)
                    return true
                }
            })
        }

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context?.also { context ->
            if (item.itemId == R.id.menu_toggle_daynight) {
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                val toggleTo = sharedPreferences.isNightMode.not()
                sharedPreferences.isNightMode = toggleTo
                (requireActivity() as CrashlyticsBundledActivity).delegate.localNightMode =
                        toggleTo.toNightModeInt
                return true
            }

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

            launch(Dispatchers.IO) {
                val sortByTrackOrder = item.itemId.let {
                    it != R.id.menu_insert_all_simple_shuffle_next || it != R.id.menu_insert_all_simple_shuffle_last || it != R.id.menu_override_all_simple_shuffle
                }
                val artistAlbumMap = latestDbAlbumList.groupBy { it.artistId }
                mainViewModel.loading.postValue(true)
                val songs = adapter.getItems().mapNotNull {
                    artistAlbumMap[it.id]?.map {
                        DB.getInstance(context).let { db ->
                            db.trackDao().findByAlbum(
                                    it.id,
                                    BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                            ).mapNotNull { getSong(db, it) }
                                    .let { if (sortByTrackOrder) it.sortedByTrackOrder() else it }
                        }
                    }?.flatten()
                }.apply {
                    mainViewModel.loading.postValue(true)
                }.flatten()

                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        viewModel.scrollToTop.observe(this) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.forceLoad.observe(this) {
            context?.also { context ->
                launch {
                    mainViewModel.loading.value = true
                    adapter.setItems(fetchArtists(DB.getInstance(context)))
                    mainViewModel.loading.value = false
                    binding.recyclerView.smoothScrollToPosition(0)
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
                db.albumDao().getAllAsync().observe(this@ArtistListFragment) { dbAlbumList ->
                    if (dbAlbumList == null) return@observe

                    latestDbAlbumList = dbAlbumList
                    upsertArtistListIfPossible(db)
                }
            }
        }
    }

    private suspend fun fetchArtists(db: DB): List<Artist> =
            withContext(Dispatchers.IO) { db.albumDao().getAll().getArtistList(db) }

    private fun upsertArtistListIfPossible(db: DB, albumList: List<Album> = latestDbAlbumList) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch {
                delay(500)
                mainViewModel.loading.value = true
                val items = albumList.getArtistList(db)
                mainViewModel.loading.value = false

                adapter.upsertItems(items)
                chatteringCancelFlag = false
            }
        }
    }

    private suspend fun List<Album>.getArtistList(db: DB): List<Artist> =
            withContext(Dispatchers.IO) {
                this@getArtistList.asSequence().groupBy { it.artistId }.map {
                    val artworkUriString = it.value.sortedByDescending { it.playbackCount }
                            .mapNotNull { it.artworkUriString }.firstOrNull()
                    val totalDuration =
                            it.value.map { db.trackDao().findByAlbum(it.id) }.flatten().map { it.duration }
                                    .sum()
                    val artistName = db.artistDao().get(it.key)?.title ?: UNKNOWN
                    Artist(it.key, artistName, artworkUriString, totalDuration)
                }
            }
}