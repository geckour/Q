package com.geckour.q.ui.library.genre

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.Main
import kotlin.coroutines.experimental.CoroutineContext

class GenreListFragment : Fragment() {

    companion object {
        fun newInstance(): GenreListFragment = GenreListFragment()
    }

    private val viewModel: GenreListViewModel by lazy {
        ViewModelProviders.of(requireActivity())[GenreListViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: GenreListAdapter by lazy { GenreListAdapter(mainViewModel) }

    private var parentJob = Job()
    private val bgScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = parentJob
    }
    private val uiScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = Dispatchers.Main + parentJob
    }

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
        binding.recyclerView.addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))


        if (adapter.itemCount == 0) {
            uiScope.launch {
                mainViewModel.loading.value = true
                adapter.setItems(fetchGenres().await())
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.resumedFragmentId.value = R.id.nav_genre
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
        mainViewModel.loading.value = false
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.genres_toolbar, menu)
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
                val songs = adapter.getItems().map { genre ->
                    genre.getTrackMediaIds(context).mapNotNull {
                        getSong(DB.getInstance(context), it, genreId = genre.id).await()
                    }
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
            uiScope.launch {
                mainViewModel.loading.value = true
                adapter.setItems(fetchGenres().await())
                binding.recyclerView.smoothScrollToPosition(0)
                mainViewModel.loading.value = false
            }
        }
    }

    private fun fetchGenres(): Deferred<List<Genre>> = bgScope.async {
        context?.let { context ->
            context.contentResolver?.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                    null, null, null)?.use {
                val db = DB.getInstance(context)
                val list: ArrayList<Genre> = ArrayList()
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres._ID))
                    val tracks = getTrackMediaIdsByGenreId(context, id)
                            .mapNotNull { db.trackDao().getByMediaId(it) }
                    val totalDuration = tracks.map { it.duration }.sum()
                    val genre = Genre(id, tracks.getGenreThumb(context).await(),
                            it.getString(it.getColumnIndex(MediaStore.Audio.Genres.NAME)).let {
                                if (it.isBlank()) UNKNOWN else it
                            }, totalDuration)
                    list.add(genre)
                }

                return@use list.toList().sortedBy { it.name }
            }
        } ?: emptyList()
    }

    private fun List<Track>.getGenreThumb(context: Context): Deferred<Bitmap?> = bgScope.async {
        val db = DB.getInstance(context)
        this@getGenreThumb.distinctBy { it.albumId }.takeOrFillNull(5)
                .map {
                    it?.let { db.getArtworkUriStringFromId(it.albumId).await()?.let { Uri.parse(it) } }
                }
                .getThumb(context)
                .await()
    }
}