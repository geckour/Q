package com.geckour.q.ui.library.genre

import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentListLibraryBinding
import com.geckour.q.domain.model.Genre
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

class GenreListFragment : Fragment() {

    companion object {
        val TAG: String = GenreListFragment::class.java.simpleName
        fun newInstance(): GenreListFragment = GenreListFragment()
    }

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: GenreListAdapter by lazy { GenreListAdapter(mainViewModel) }

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

        observeEvents()

        if (adapter.itemCount == 0) {
            launch(UI + parentJob) {
                mainViewModel.loading.value = true
                adapter.setItems(fetchGenres().await().sortedBy { it.name })
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

        inflater?.inflate(R.menu.genres, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context?.also { context ->
            launch(parentJob) {
                val songs = adapter.getItems().map { genre ->
                    genre.getTrackMediaIds(context).mapNotNull {
                        getSong(DB.getInstance(context), it, genreId = genre.id).await()
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
        }

        return true
    }

    private fun observeEvents() {
        mainViewModel.requireScrollTop.observe(this, Observer {
            binding.recyclerView.smoothScrollToPosition(0)
        })
    }

    private fun fetchGenres(): Deferred<List<Genre>> = async(parentJob) {
        context?.let { context ->
            context.contentResolver?.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                    null, null, null)?.use {
                val db = DB.getInstance(context)
                val list: ArrayList<Genre> = ArrayList()
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres._ID))
                    val totalDuration = getTrackMediaIdsByGenreId(context, id)
                            .mapNotNull { db.trackDao().getByMediaId(it)?.duration }.sum()
                    val genre = Genre(id, null,
                            it.getString(it.getColumnIndex(MediaStore.Audio.Genres.NAME)).let {
                                if (it.isBlank()) UNKNOWN else it
                            }, totalDuration)
                    list.add(genre)
                }

                return@use list.toList()
            }
        } ?: emptyList()
    }
}