package com.geckour.q.ui.library.artist

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Artist
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getSong
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI

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
    private var latestDbTrackList: List<Track> = emptyList()
    private var chatteringCancelFlag: Boolean = false

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (adapter.itemCount == 0) fetchArtists()
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
        mainViewModel.resumedFragmentId.value = R.id.nav_artist
    }

    override fun onPause() {
        super.onPause()
        onSaveInstanceState(Bundle())
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
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

    private fun fetchArtists() {
        DB.getInstance(requireContext()).also { db ->
            db.trackDao().getAllAsync().observe(this@ArtistListFragment, Observer { dbTrackList ->
                if (dbTrackList == null) return@Observer

                latestDbTrackList = dbTrackList
                upsertArtistListIfPossible(db)
            })
        }
    }

    private fun upsertArtistListIfPossible(db: DB) {
        if (chatteringCancelFlag.not()) {
            chatteringCancelFlag = true
            launch(UI + parentJob) {
                delay(500)
                val items = getAllAlbumArtist(db, latestDbTrackList).await()
                adapter.upsertItems(items)
                chatteringCancelFlag = false
            }
        }
    }

    private fun getAllAlbumArtist(db: DB, dbTrackList: List<Track>): Deferred<List<Artist>> =
            async(parentJob) {
                dbTrackList.groupBy {
                    it.albumId
                }.mapNotNull {
                    val track = it.value.firstOrNull { it.albumArtistId != null }
                    if (track != null) {
                        val dbArtist = db.artistDao().get(track.albumArtistId
                                ?: throw IllegalStateException())
                                ?: return@mapNotNull null
                        val albumId = track.albumId
                        Artist(dbArtist.id, dbArtist.title, albumId)
                    } else {
                        val dbArtist = (it.value.maxBy { it.artistId }
                                ?: it.value.first()).let { db.artistDao().get(it.artistId) }
                                ?: return@mapNotNull null
                        Artist(dbArtist.id, dbArtist.title, it.key)
                    }
                }
            }
}