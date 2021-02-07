package com.geckour.q.presentation.easteregg

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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.databinding.FragmentEasterEggBinding
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.applyDefaultSettings
import com.geckour.q.util.observe
import com.geckour.q.util.orDefaultForModel
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toggleDayNight
import com.google.firebase.analytics.FirebaseAnalytics

class EasterEggFragment : Fragment() {

    companion object {
        fun newInstance(): EasterEggFragment = EasterEggFragment()
    }

    private lateinit var binding: FragmentEasterEggBinding
    private val viewModel: EasterEggViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

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
        binding.viewModel = viewModel

        observeEvents()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.layout.fragment_easter_egg
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.toggle_theme_toolbar, menu)

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toggle_daynight -> requireActivity().toggleDayNight()
            else -> return false
        }
        return true
    }

    private fun observeEvents() {
        viewModel.song.observe(viewLifecycleOwner) { song ->
            song ?: return@observe

            binding.tapArea?.setOnClickListener {
                FirebaseAnalytics.getInstance(requireContext())
                    .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                        putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped today's song")
                    })

                mainViewModel.onNewQueue(
                    listOf(song), InsertActionType.NEXT, OrientedClassType.SONG
                )
            }

            binding.tapArea?.setOnLongClickListener {
                PopupMenu(requireContext(), binding.artwork, Gravity.BOTTOM).apply {
                    setOnMenuItemClickListener {
                        return@setOnMenuItemClickListener when (it.itemId) {
                            R.id.menu_transition_to_artist -> {
                                mainViewModel.selectedArtist.value = song.artist
                                true
                            }
                            R.id.menu_transition_to_album -> {
                                mainViewModel.selectedAlbum.value = song.album
                                true
                            }
                            else -> false
                        }
                    }
                    inflate(R.menu.song_transition)
                }.show()

                return@setOnLongClickListener true
            }

            Glide.with(binding.artwork)
                .load(song.thumbUriString.orDefaultForModel)
                .applyDefaultSettings()
                .into(binding.artwork)
        }
    }
}