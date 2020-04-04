package com.geckour.q.presentation.library.album

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Album
import com.geckour.q.domain.model.Artist
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getSong
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toDomainModel
import com.geckour.q.util.toNightModeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.geckour.q.data.db.model.Album as DBAlbum

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

    private val viewModel: AlbumListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: AlbumListAdapter by lazy { AlbumListAdapter(mainViewModel) }

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private var latestDbAlbumList: List<DBAlbum> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    private var artist: Artist? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)
        binding.recyclerView.adapter = adapter

        artist = arguments?.getParcelable(ARGS_KEY_ARTIST)
        if (adapter.itemCount == 0) observeAlbums()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_album
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.albums_toolbar, menu)
        (menu.findItem(R.id.menu_search)?.actionView as? SearchView?)
            ?.let {
                mainViewModel.initSearchQueryListener(it)
                it.setOnQueryTextListener(mainViewModel.searchQueryListener)
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

            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val sortByTrackOrder = item.itemId.let {
                    it != R.id.menu_insert_all_simple_shuffle_next || it != R.id.menu_insert_all_simple_shuffle_last || it != R.id.menu_override_all_simple_shuffle
                }
                mainViewModel.onLoadStateChanged(true)
                val songs = adapter.currentList.map { album ->
                    DB.getInstance(context).let { db ->
                        db.trackDao()
                            .let {
                                if (sortByTrackOrder) {
                                    it.getAllByAlbumSorted(
                                        album.id,
                                        BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                                    )
                                } else {
                                    it.getAllByAlbum(
                                        album.id,
                                        BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                                    )
                                }
                            }
                            .mapNotNull { getSong(db, it) }
                    }
                }.apply {
                    mainViewModel.onLoadStateChanged(false)
                }.flatten()

                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        mainViewModel.scrollToTop.observe(this) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        mainViewModel.forceLoad.observe(this) {
            context?.also { context ->
                viewModel.viewModelScope.launch {
                    mainViewModel.onLoadStateChanged(true)
                    val items = withContext(Dispatchers.IO) { fetchAlbums(DB.getInstance(context)) }
                    mainViewModel.onLoadStateChanged(false)
                    adapter.submitList(items)
                    binding.recyclerView.smoothScrollToPosition(0)
                }
            }
        }

        viewModel.albumIdDeleted.observe(this) {
            if (it == null) return@observe
            adapter.onAlbumDeleted(it)
        }
    }

    private fun observeAlbums() {
        context?.apply {
            DB.getInstance(this).also { db ->
                (artist?.let {
                    db.albumDao().getAllByArtistIdAsync(it.id)
                } ?: db.albumDao().getAllAsync()).observe(this@AlbumListFragment) { dbAlbumList ->
                    if (dbAlbumList == null) return@observe

                    mainViewModel.onLoadStateChanged(true)
                    latestDbAlbumList = dbAlbumList
                    upsertAlbumListIfPossible(db)
                    mainViewModel.onLoadStateChanged(false)
                }
            }
        }
    }

    private fun fetchAlbums(db: DB): List<Album> =

        (artist?.let { db.albumDao().getAllByArtistId(it.id) } ?: db.albumDao()
            .getAll()).getAlbumList(db)

    private fun upsertAlbumListIfPossible(db: DB) {
        viewModel.viewModelScope.launch {
            mainViewModel.onLoadStateChanged(true)
            val items = withContext(Dispatchers.IO) { latestDbAlbumList.getAlbumList(db) }
            mainViewModel.onLoadStateChanged(false)
            upsertAlbumListIfPossible(items)
        }
    }

    private fun upsertAlbumListIfPossible(items: List<Album>) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            viewModel.viewModelScope.launch {
                delay(500)
                adapter.submitList(items)
                chatteringCancelFlag = false
            }
        }
    }

    private fun List<DBAlbum>.getAlbumList(db: DB): List<Album> = this@getAlbumList.mapNotNull {
        val artist = db.artistDao().get(it.artistId)
        val artistName = artist?.title ?: return@mapNotNull null
        val artistNameSort = artist.titleSort
        val totalDuration = db.trackDao().getAllByAlbum(it.id).map { it.duration }.sum()
        it.toDomainModel(artistName, artistNameSort, totalDuration)
    }
}