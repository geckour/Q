package com.geckour.q.presentation.library.genre

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
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
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.UNKNOWN
import com.geckour.q.util.getArtworkUriStringFromId
import com.geckour.q.util.getSong
import com.geckour.q.util.getThumb
import com.geckour.q.util.getTrackMediaIds
import com.geckour.q.util.getTrackMediaIdsByGenreId
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.takeOrFillNull
import com.geckour.q.util.toNightModeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    ): View? {
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
                mainViewModel.loading.value = true
                adapter.setItems(fetchGenres())
                mainViewModel.loading.value = false
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

        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.genres_toolbar, menu)
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

            viewModel.viewModelScope.launch(Dispatchers.IO) {
                mainViewModel.loading.postValue(true)
                val songs = adapter.getItems().map { genre ->
                    genre.getTrackMediaIds(context).mapNotNull {
                        getSong(DB.getInstance(context), it, genreId = genre.id)
                    }
                }.apply {
                    mainViewModel.loading.postValue(false)
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
            viewModel.viewModelScope.launch {
                mainViewModel.loading.value = true
                adapter.setItems(fetchGenres())
                mainViewModel.loading.value = false
                binding.recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private suspend fun fetchGenres(): List<Genre> = context?.let { context ->
        withContext(Dispatchers.IO) {
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
                    val tracks = getTrackMediaIdsByGenreId(context, id).mapNotNull {
                        db.trackDao().getByMediaId(it)
                    }
                    val totalDuration = tracks.map { it.duration }.sum()
                    val genre = Genre(
                        id,
                        tracks.getGenreThumb(context),
                        it.getString(it.getColumnIndex(MediaStore.Audio.Genres.NAME)).let {
                            if (it.isBlank()) UNKNOWN else it
                        },
                        totalDuration
                    )
                    list.add(genre)
                }

                return@use list.toList().sortedBy { it.name }
            }
        }
    } ?: emptyList()

    private suspend fun List<Track>.getGenreThumb(context: Context): Bitmap? {
        val db = DB.getInstance(context)
        return this.distinctBy { it.albumId }.takeOrFillNull(5).map {
            it?.let { db.getArtworkUriStringFromId(it.albumId)?.let { Uri.parse(it) } }
        }.getThumb(context)
    }
}