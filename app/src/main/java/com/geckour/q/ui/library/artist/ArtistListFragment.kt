package com.geckour.q.ui.library.artist

import android.os.Bundle
import android.provider.MediaStore
import android.view.*
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
import com.geckour.q.util.getArtworkUriFromId
import com.geckour.q.util.getSong
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import java.io.File

class ArtistListFragment : Fragment() {

    companion object {
        fun newInstance(): ArtistListFragment = ArtistListFragment()
    }

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: ArtistListAdapter by lazy { ArtistListAdapter(mainViewModel) }

    private var parentJob = Job()
    private var latestDbAlbumList: List<Album> = emptyList()
    private var chatteringCancelFlag: Boolean = false

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

        if (savedInstanceState == null && adapter.itemCount == 0) fetchArtists()
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

        inflater?.inflate(R.menu.artists, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        launch {
            val songs = adapter.getItems().map {
                DB.getInstance(requireContext()).let { db ->
                    db.trackDao().findByArtist(it.id).mapNotNull { getSong(db, it).await() }
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

    private fun fetchArtists() {
        DB.getInstance(requireContext()).also { db ->
            db.albumDao().getAllAsync().observe(this@ArtistListFragment, Observer { dbAlbumList ->
                if (dbAlbumList == null) return@Observer

                mainViewModel.loading.value = true
                latestDbAlbumList = dbAlbumList
                upsertArtistListIfPossible(db)
            })
        }
    }

    private fun upsertArtistListIfPossible(db: DB) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI + parentJob) {
                delay(500)
                val items = getAllArtist(db, latestDbAlbumList).await()
                adapter.upsertItems(items)
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
                chatteringCancelFlag = false
            }
        }
    }

    private fun getAllArtist(db: DB, dbAlbumList: List<Album>): Deferred<List<Artist>> =
            async(parentJob) {
                dbAlbumList.asSequence()
                        .groupBy { it.artistId }
                        .map {
                            val albumId = it.value.lastOrNull { existArtwork(db, it.id) }?.id
                                    ?: it.value.first().id
                            val artistName = db.artistDao().get(it.key)?.title
                                    ?: UNKNOWN
                            Artist(it.key, artistName, albumId)
                        }
            }

    private suspend fun existArtwork(db: DB, albumId: Long): Boolean =
            db.getArtworkUriFromId(albumId).await()?.let {
                requireContext().contentResolver.query(it,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null, null, null)?.use {
                    it.moveToFirst()
                            && File(it.getString(it.getColumnIndex(MediaStore.MediaColumns.DATA))).exists()
                }
            } ?: false

}