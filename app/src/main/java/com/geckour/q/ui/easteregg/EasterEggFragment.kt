package com.geckour.q.ui.easteregg

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.geckour.q.R
import com.geckour.q.databinding.FragmentEasterEggBinding
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.loadOrDefault
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toggleDayNight
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class EasterEggFragment : Fragment() {

    companion object {
        fun newInstance(): EasterEggFragment = EasterEggFragment()
    }

    private lateinit var binding: FragmentEasterEggBinding
    private val viewModel by viewModel<EasterEggViewModel>()
    private val mainViewModel by sharedViewModel<MainViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentEasterEggBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        FirebaseAnalytics.getInstance(requireContext())
            .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_NAME, "Show easter egg screen")
            })

        binding.lifecycleOwner = viewLifecycleOwner

        observeEvents()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.layout.fragment_easter_egg
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toggle_daynight -> requireActivity().toggleDayNight()
            else -> return false
        }
        return true
    }

    private fun observeEvents() {
        viewModel.track.onEach { track ->
            binding.track = track
            binding.tapArea?.setOnClickListener {
                FirebaseAnalytics.getInstance(requireContext())
                    .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                        putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped today's track")
                    })

                mainViewModel.onNewQueue(
                    listOf(track), InsertActionType.NEXT, OrientedClassType.TRACK
                )
            }

//            binding.tapArea?.setOnLongClickListener {
//                PopupMenu(requireContext(), binding.artwork, Gravity.BOTTOM).apply {
//                    setOnMenuItemClickListener {
//                        return@setOnMenuItemClickListener when (it.itemId) {
//                            R.id.menu_transition_to_artist -> {
//                                mainViewModel.selectedArtist.value = track.artist
//                                true
//                            }
//                            R.id.menu_transition_to_album -> {
//                                mainViewModel.selectedAlbum.value = track.album
//                                true
//                            }
//                            else -> false
//                        }
//                    }
//                    inflate(R.menu.track_transition)
//                }.show()
//
//                return@setOnLongClickListener true
//            }

            binding.artwork.loadOrDefault(track.thumbUriString)
        }.launchIn(lifecycleScope)
    }
}