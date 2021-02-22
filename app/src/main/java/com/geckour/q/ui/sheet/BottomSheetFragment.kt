package com.geckour.q.ui.sheet

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.service.SleepTimerService
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.sheet.BottomSheetViewModel.Companion.PREF_KEY_SHOW_LOCK_TOUCH_QUEUE
import com.geckour.q.util.getTimeString
import com.geckour.q.util.observe
import com.geckour.q.util.shake
import com.geckour.q.util.showCurrentRemain
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BottomSheetFragment : Fragment() {

    companion object {

        fun newInstance(): BottomSheetFragment = BottomSheetFragment()
    }

    private val viewModel: BottomSheetViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentSheetBottomBinding
    private lateinit var adapter: QueueListAdapter
    private lateinit var behavior: BottomSheetBehavior<MotionLayout>

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private val touchLockListener: (View, MotionEvent) -> Boolean = { _, event ->
        behavior.onTouchEvent(
            requireActivity().findViewById(R.id.content_main), binding.sheet, event
        )
        true
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        var from: Int? = null
        var to: Int? = null

        override fun onMove(
            recyclerView: RecyclerView,
            fromHolder: RecyclerView.ViewHolder,
            toHolder: RecyclerView.ViewHolder
        ): Boolean {
            val from = fromHolder.adapterPosition
            val to = toHolder.adapterPosition

            if (this.from == null) this.from = from
            this.to = to

            adapter.move(from, to)
            (fromHolder as QueueListAdapter.ViewHolder).dismissPopupMenu()

            return true
        }

        override fun onSwiped(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)

            val from = this.from
            val to = this.to

            if (viewHolder == null && from != null && to != null) {
                mainViewModel.onQueueSwap(from, to)
            }

            this.from = null
            this.to = null
        }
    })

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(v: View, dy: Float) {
            binding.sheet.progress = dy
        }

        @SuppressLint("SwitchIntDef")
        override fun onStateChanged(v: View, state: Int) {
            viewModel.sheetState = state
            binding.buttonToggleVisibleQueue.setImageResource(
                when (state) {
                    BottomSheetBehavior.STATE_EXPANDED -> R.drawable.ic_collapse
                    else -> R.drawable.ic_queue
                }
            )
            if (state == BottomSheetBehavior.STATE_EXPANDED) scrollToCurrent()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSheetBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.mainViewModel = mainViewModel
        adapter = QueueListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.sheet.setOnTouchListener { _, _ -> true }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                Unit

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mainViewModel.onNewSeekBarProgress(seekBar.progress.toFloat() / seekBar.max)
            }
        })

        listOf(
            binding.buttonControllerLeft,
            binding.buttonControllerCenter,
            binding.buttonControllerRight
        ).forEach {
            it.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        mainViewModel.onNewPlaybackButton(PlaybackButton.UNDEFINED)
                    }
                }

                return@setOnTouchListener false
            }
        }

        resetMarquee()

        behavior = BottomSheetBehavior.from(
            (requireActivity() as MainActivity).binding.root.findViewById(R.id.bottom_sheet)
        )
        behavior.addBottomSheetCallback(bottomSheetCallback)

        observeEvents()
    }

    override fun onResume() {
        super.onResume()

        viewModel.reAttach()
        binding.viewModel = viewModel
    }

    override fun onDestroy() {
        super.onDestroy()
        mainViewModel.player.value?.apply {
            setOnQueueChangedListener(null)
            setOnPlaybackStateChangeListener(null)
            setOnPlaybackRatioChangedListener(null)
            setOnRepeatModeChangedListener(null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun observeEvents() {
        mainViewModel.player.observe(viewLifecycleOwner) { player ->
            player ?: return@observe

            player.setOnQueueChangedListener { songs, position, songChanged ->
                onQueueChanged(songs, position, songChanged)
            }
            player.setOnPlaybackStateChangeListener { playbackState, playWhenReady ->
                onPlayingChanged(
                    when (playbackState) {
                        Player.STATE_READY -> {
                            playWhenReady
                        }
                        else -> false
                    }
                )
            }
            player.setOnPlaybackRatioChangedListener {
                viewModel.playbackPosition = it
                onPlaybackPositionChanged(viewModel.playbackPosition)
            }
            player.setOnRepeatModeChangedListener {
                onRepeatModeChanged(it)
            }
        }

        viewModel.artworkLongClick.observe(viewLifecycleOwner) { valid ->
            if (valid != true) return@observe
            context?.also { context ->
                PopupMenu(context, binding.artwork).apply {
                    setOnMenuItemClickListener {
                        return@setOnMenuItemClickListener when (it.itemId) {
                            R.id.menu_transition_to_artist -> {
                                viewModel.onTransitionToArtist(mainViewModel)
                                true
                            }
                            R.id.menu_transition_to_album -> {
                                viewModel.onTransitionToAlbum(mainViewModel)
                                true
                            }
                            else -> false
                        }
                    }
                    inflate(R.menu.song_transition)
                }.show()
                viewModel.onArtworkDialogShown()
            }
        }

        viewModel.toggleSheetState.observe(viewLifecycleOwner) {
            behavior.state = when (val state = behavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.STATE_SETTLING -> state
                else -> BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        viewModel.showCurrentRemain.observe(viewLifecycleOwner) {
            it ?: return@observe
            sharedPreferences.showCurrentRemain = it
            val song = viewModel.currentDomainTrack ?: return@observe
            val ratio = binding.seekBar.progress / binding.seekBar.max.toFloat()
            val elapsedTime = (song.duration * ratio).toLong()
            setTimeRightText(song, elapsedTime)
        }

        viewModel.scrollToCurrent.observe(viewLifecycleOwner) {
            if (it != true) return@observe
            scrollToCurrent()
            viewModel.onScrollToCurrentInvoked()
        }

        viewModel.touchLock.observe(viewLifecycleOwner) {
            it ?: return@observe
            sharedPreferences.edit().putBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, it).apply()
            binding.recyclerView.setOnTouchListener(if (it) touchLockListener else null)
        }
    }

    private fun resetMarquee() {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val views = listOf(binding.textTrack, binding.textArtistAndAlbum)
            views.forEach { withContext(Dispatchers.Main) { it.isSelected = false } }
            delay(1000)
            views.forEach { withContext(Dispatchers.Main) { it.isSelected = true } }
        }
    }

    private fun scrollToCurrent() {
        if (adapter.itemCount > 0) {
            binding.recyclerView.smoothScrollToPosition(viewModel.currentPosition)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onQueueChanged(queue: List<DomainTrack>, position: Int, songChanged: Boolean) {
        adapter.setNowPlayingPosition(position, queue)
        viewModel.currentQueue = queue
        viewModel.onNewPosition(position)
        binding.viewModel = viewModel

        val totalTime = queue.map { it.duration }.sum()
        binding.textTimeTotal.text =
            requireContext().getString(R.string.bottom_sheet_time_total, totalTime.getTimeString())

        val changed = (adapter.getItemIds() == queue.map { it.id }).not()
        if (changed && behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            binding.buttonToggleVisibleQueue.shake()
        }

        if (songChanged) {
            viewModel.playbackPosition = 0
            onPlaybackPositionChanged(0)
        }

        val noCurrentSong = viewModel.currentDomainTrack == null
        binding.seekBar.setOnTouchListener { _, _ -> noCurrentSong }
        if (noCurrentSong) {
            with(binding) {
                textTimeLeft.text = null
                textTimeRight.text = null
                textTimeTotal.text = null
                textTimeRemain.text = null
                seekBar.progress = 0
            }
        }
        viewModel.setArtwork(binding.artwork)
        resetMarquee()
    }

    private fun onPlayingChanged(playing: Boolean) {
        viewModel.playing.value = playing
        if (playing.not()) {
            requireContext().startService(SleepTimerService.getCancelIntent(requireContext()))
        }
    }

    private fun onPlaybackPositionChanged(playbackPosition: Long) {
        val song = viewModel.currentDomainTrack ?: return

        val ratio = (playbackPosition.toFloat() / song.duration)
        binding.seekBar.progress = (binding.seekBar.max * ratio).toInt()

        binding.textTimeLeft.text = playbackPosition.getTimeString()
        setTimeRightText(song, playbackPosition)
        val remain = adapter.getItemsAfter(viewModel.currentPosition + 1)
            .map { it.duration }
            .sum() + (song.duration - playbackPosition)
        binding.textTimeRemain.text =
            getString(R.string.bottom_sheet_time_remain, remain.getTimeString())
    }

    private fun setTimeRightText(domainTrack: DomainTrack, elapsedTime: Long) {
        binding.textTimeRight.text =
            if (sharedPreferences.showCurrentRemain) "-${(domainTrack.duration - elapsedTime).getTimeString()}"
            else domainTrack.durationString
    }

    private fun onRepeatModeChanged(mode: Int) {
        binding.buttonRepeat.apply {
            setImageResource(
                when (mode) {
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat_off
                }
            )
            visibility = View.VISIBLE
        }
    }
}