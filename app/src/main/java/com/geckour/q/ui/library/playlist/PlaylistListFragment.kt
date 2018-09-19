package com.geckour.q.ui.library.playlist

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Playlist
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getSong
import com.geckour.q.util.getTrackIds
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch

class PlaylistListFragment : Fragment() {

    companion object {
        fun newInstance(): PlaylistListFragment = PlaylistListFragment()
    }

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: PlaylistListAdapter by lazy { PlaylistListAdapter(mainViewModel) }

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
        if (adapter.itemCount == 0) fetchPlaylists()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_playlist
        parentJob.cancel()
        parentJob = Job()
    }

    override fun onPause() {
        super.onPause()
        parentJob.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.playlists, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        launch(parentJob) {
            val songs = adapter.getItems().map { playlist ->
                playlist.getTrackIds(requireContext())
                        .mapNotNull {
                            getSong(DB.getInstance(requireContext()),
                                    it,
                                    playlistId = playlist.id)
                                    .await()
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

    internal fun fetchPlaylists() {
        requireActivity().contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Playlists._ID,
                        MediaStore.Audio.Playlists.NAME),
                null,
                null,
                MediaStore.Audio.Playlists.DATE_MODIFIED)?.apply {
            val list: ArrayList<Playlist> = ArrayList()
            while (moveToNext()) {
                val playlist = Playlist(
                        getLong(getColumnIndex(MediaStore.Audio.Playlists._ID)),
                        null,
                        getString(getColumnIndex(MediaStore.Audio.Playlists.NAME)).let {
                            if (it.isBlank()) "<unknown>" else it
                        })
                list.add(playlist)
            }
            adapter.setItems(list.sortedBy { it.name })
        }
    }
}