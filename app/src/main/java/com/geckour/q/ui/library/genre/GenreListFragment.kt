package com.geckour.q.ui.library.genre

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.view.*
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.service.PlayerService
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getSong
import com.geckour.q.util.getTrackIds
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class GenreListFragment : Fragment() {

    companion object {
        fun newInstance(): GenreListFragment = GenreListFragment()
    }

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private lateinit var adapter: GenreListAdapter

    private var parentJob = Job()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setHasOptionsMenu(true)

        adapter = GenreListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        fetchGenresWithPermissionCheck()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_genre
        parentJob.cancel()
        parentJob = Job()
    }

    override fun onPause() {
        super.onPause()
        parentJob.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.genres, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        launch(parentJob) {
            val songs = adapter.getItems().map { genre ->
                genre.getTrackIds(requireContext())
                        .mapNotNull {
                            getSong(DB.getInstance(requireContext()),
                                    it,
                                    genreId = genre.id)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun fetchGenres() {
        requireActivity().contentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Genres._ID,
                        MediaStore.Audio.Genres.NAME),
                null,
                null,
                null)?.apply {
            val list: ArrayList<Genre> = ArrayList()
            while (moveToNext()) {
                val genre = Genre(
                        getLong(getColumnIndex(MediaStore.Audio.Genres._ID)),
                        null,
                        getString(getColumnIndex(MediaStore.Audio.Genres.NAME)).let {
                            if (it.isBlank()) "<unknown>" else it
                        })
                list.add(genre)
            }
            adapter.setItems(list.sortedBy { it.name })
        }
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    internal fun onReadExternalStorageDenied() {
        fetchGenresWithPermissionCheck()
    }
}