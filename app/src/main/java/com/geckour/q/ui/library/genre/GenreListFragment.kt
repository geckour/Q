package com.geckour.q.ui.library.genre

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.recyclerview.widget.DividerItemDecoration
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getDomainTrack
import com.geckour.q.util.getThumb
import com.geckour.q.util.getTrackMediaIds
import com.geckour.q.util.getTrackMediaIdsByGenreId
import com.geckour.q.util.setIconTint
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.takeOrFillNull
import com.geckour.q.util.toggleDayNight
import com.geckour.q.util.updateFileMetadata
import kotlinx.coroutines.launch

class GenreListFragment : Fragment() {

    companion object {
        fun newInstance(): GenreListFragment = GenreListFragment()
    }

    private val viewModel: GenreListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: GenreListAdapter by lazy { GenreListAdapter(mainViewModel) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        observeEvents()
        binding = FragmentListLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )


        if (adapter.itemCount == 0) {
            viewModel.viewModelScope.launch {
                mainViewModel.onLoadStateChanged(true)
                adapter.submitList(fetchGenres())
                mainViewModel.onLoadStateChanged(false)
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_genre
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.genres_toolbar, menu)
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
                requireActivity().toggleDayNight()
                return true
            }
            if (item.itemId == R.id.menu_sleep) {
                (requireActivity() as? MainActivity)?.showSleepTimerDialog()
                return true
            }
            if (item.itemId == R.id.menu_edit_metadata) {
                viewModel.viewModelScope.launch {
                    val db = DB.getInstance(context)

                    mainViewModel.onLoadStateChanged(true)
                    val tracks = adapter.getItems().flatMap { genre ->
                        genre.getTrackMediaIds(context)
                            .mapNotNull { db.trackDao().getByMediaId(it) }
                    }
                    mainViewModel.onLoadStateChanged(false)

                    context.showFileMetadataUpdateDialog(tracks) { binding ->
                        viewModel.viewModelScope.launch {
                            mainViewModel.onLoadStateChanged(true)
                            binding.updateFileMetadata(context, db, tracks)
                            mainViewModel.onLoadStateChanged(false)
                        }
                    }
                }
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

            viewModel.viewModelScope.launch {
                mainViewModel.onLoadStateChanged(true)
                val tracks = adapter.getItems().flatMap { genre ->
                    genre.getTrackMediaIds(context).mapNotNull {
                        getDomainTrack(DB.getInstance(context), it, genreId = genre.id)
                    }
                }
                mainViewModel.onLoadStateChanged(false)

                adapter.onNewQueue(tracks, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        mainViewModel.forceLoad.observe(viewLifecycleOwner) {
            viewModel.viewModelScope.launch {
                mainViewModel.onLoadStateChanged(true)
                adapter.submitList(fetchGenres())
                mainViewModel.onLoadStateChanged(false)
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private suspend fun fetchGenres(): List<Genre> = context?.let { context ->
        context.contentResolver?.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null
        )?.use {
            val db = DB.getInstance(context)
            val list: ArrayList<Genre> = ArrayList()
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres._ID))
                val joinedTracks = getTrackMediaIdsByGenreId(context, id).mapNotNull {
                    db.trackDao().getByMediaId(it)
                }
                val totalDuration = joinedTracks.map { it.track.duration }.sum()
                val genre = Genre(
                    id,
                    joinedTracks.getGenreThumb(context),
                    it.getString(it.getColumnIndex(MediaStore.Audio.Genres.NAME)).let {
                        if (it.isBlank()) UNKNOWN else it
                    },
                    totalDuration
                )
                list.add(genre)
            }

            return@use list.toList().sortedBy { it.name }
        }
    } ?: emptyList()

    private suspend fun List<JoinedTrack>.getGenreThumb(context: Context): Bitmap? =
        this.distinctBy { it.album.id }
            .takeOrFillNull(5)
            .map { it?.album?.artworkUriString }
            .getThumb(context)
}