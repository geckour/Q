package com.geckour.q.presentation.library.artist

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
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.BoolConverter
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.getSong
import com.geckour.q.util.ignoringEnabled
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toNightModeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArtistListFragment : Fragment() {

    companion object {

        fun newInstance(): ArtistListFragment = ArtistListFragment()
    }

    private val viewModel: ArtistListViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentListLibraryBinding
    private val adapter: ArtistListAdapter by lazy { ArtistListAdapter(mainViewModel) }

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

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
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_artist
    }

    override fun onStop() {
        super.onStop()

        mainViewModel.onLoadStateChanged(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.artists_toolbar, menu)
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
                val sortByTrackOrder = item.itemId !in listOf(
                    R.id.menu_insert_all_simple_shuffle_next,
                    R.id.menu_insert_all_simple_shuffle_last,
                    R.id.menu_override_all_simple_shuffle
                )

                mainViewModel.onLoadStateChanged(true)
                val db = DB.getInstance(context)
                val songs = adapter.currentList.flatMap {
                    db.albumDao().getAllByArtistId(it.id).flatMap {
                        if (sortByTrackOrder) {
                            db.trackDao().getAllByAlbumSorted(
                                it.id,
                                BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                            ).mapNotNull { getSong(db, it) }
                        } else {
                            db.trackDao().getAllByAlbum(
                                it.id,
                                BoolConverter().fromBoolean(sharedPreferences.ignoringEnabled)
                            ).mapNotNull { getSong(db, it) }
                        }
                    }
                }

                adapter.onNewQueue(songs, actionType)
            }
        }

        return true
    }

    private fun observeEvents() {
        viewModel.artistListData.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        mainViewModel.scrollToTop.observe(viewLifecycleOwner) {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        viewModel.artistIdDeleted.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            adapter.onArtistDeleted(it)
        }
    }
}