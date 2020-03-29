package com.geckour.q.presentation.sheet

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.R
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.domain.model.Song
import com.geckour.q.presentation.main.MainActivity
import com.geckour.q.presentation.main.MainViewModel
import com.geckour.q.presentation.share.SharingActivity
import com.geckour.q.util.getTimeString
import com.geckour.q.util.observe
import com.geckour.q.util.shake
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior

class BottomSheetFragment : Fragment() {

    companion object {
        private const val PREF_KEY_SHOW_CURRENT_REMAIN = "pref_key_show_current_remain"
        private const val PREF_KEY_SHOW_LOCK_TOUCH_QUEUE = "pref_key_lock_touch_queue"
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
            requireActivity().findViewById(R.id.coordinator_main),
            binding.sheet,
            event
        )
        true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSheetBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QueueListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
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

                if (viewHolder == null && from != null && to != null)
                    mainViewModel.onQueueSwap(from, to)

                this.from = null
                this.to = null
            }
        }).attachToRecyclerView(binding.recyclerView)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.viewModel = viewModel

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                viewModel.newSeekBarProgress.value = seekBar.progress.toFloat() / seekBar.max
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
                        viewModel.playbackButton.value = PlaybackButton.UNDEFINED
                    }
                }

                return@setOnTouchListener false
            }
        }

        viewModel.touchLock.value =
            sharedPreferences.getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)

        behavior = BottomSheetBehavior.from(
            (requireActivity() as MainActivity).binding.root.findViewById(R.id.bottom_sheet)
        )
        behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(v: View, dy: Float) {
                binding.sheet.progress = dy
            }

            @SuppressLint("SwitchIntDef")
            override fun onStateChanged(v: View, state: Int) {
                viewModel.sheetState = state
                reloadBindingVariable()
                binding.buttonToggleVisibleQueue.setImageResource(
                    when (state) {
                        BottomSheetBehavior.STATE_EXPANDED -> R.drawable.ic_collapse
                        else -> R.drawable.ic_queue
                    }
                )
                if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    viewModel.scrollToCurrent.call()
                }
            }
        })

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
            setOnCurrentPositionChangedListener(null)
            setOnPlaybackStateChangeListener(null)
            setOnPlaybackRatioChangedListener(null)
            setOnRepeatModeChangedListener(null)
        }
    }

    private fun observeEvents() {
        mainViewModel.player.observe(this) { player ->
            player ?: return@observe

            player.apply {
                setOnQueueChangedListener {
                    onQueueChanged(it)
                }
                setOnCurrentPositionChangedListener { position, songChanged ->
                    onCurrentQueuePositionChanged(position, songChanged)
                }
                setOnPlaybackStateChangeListener { playbackState, playWhenReady ->
                    onPlayingChanged(
                        when (playbackState) {
                            Player.STATE_READY -> {
                                playWhenReady
                            }
                            else -> false
                        }
                    )
                }
                setOnPlaybackRatioChangedListener {
                    onPlaybackRatioChanged(it)
                }
                setOnRepeatModeChangedListener {
                    onRepeatModeChanged(it)
                }
            }
        }

        viewModel.artworkLongClick.observe(this) { _ ->
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
            }
        }

        viewModel.toggleSheetState.observe(this) {
            behavior.state = when (behavior.state) {
                BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
                else -> BottomSheetBehavior.STATE_EXPANDED
            }
        }

        viewModel.scrollToCurrent.observe(this) {
            if (adapter.itemCount > 0) {
                binding.recyclerView.smoothScrollToPosition(viewModel.currentPosition)
            }
        }

        viewModel.toggleCurrentRemain.observe(this) {
            val changeTo = sharedPreferences.getBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, false).not()
            sharedPreferences.edit().putBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, changeTo).apply()
        }

        viewModel.touchLock.observe(this) {
            it ?: return@observe
            sharedPreferences.edit().putBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, it).apply()
            binding.queueUnTouchable = it
            binding.recyclerView.setOnTouchListener(if (it) touchLockListener else null)
        }

        viewModel.share.observe(this) {
            it ?: return@observe
            startActivity(SharingActivity.getIntent(requireContext(), it))
        }

        viewModel.changeRepeatMode.observe(this) {
            mainViewModel.player.value?.rotateRepeatMode()
        }
    }

    private fun onQueueChanged(queue: List<Song>) {
        adapter.setItems(queue)
        viewModel.currentQueue = adapter.getItems()

        val changed = (adapter.getItemIds() == queue.map { it.id }).not()
        val notEmpty = queue.isNotEmpty()

        binding.isQueueNotEmpty = notEmpty

        val totalTime = queue.map { it.duration }.sum()
        binding.textTimeTotal.text = requireContext()
            .getString(R.string.bottom_sheet_time_total, totalTime.getTimeString())

        if (changed && behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            binding.buttonToggleVisibleQueue.shake()
        }
    }

    private fun onCurrentQueuePositionChanged(position: Int, songChanged: Boolean) {
        adapter.setNowPlayingPosition(position)
        viewModel.currentPosition = position
        binding.viewModel = viewModel

        if (songChanged) onPlaybackRatioChanged(0f)

        val noCurrentSong = viewModel.currentSong == null
        binding.seekBar.setOnTouchListener { _, _ -> noCurrentSong }
        if (noCurrentSong) {
            with(binding) {
                textTimeLeft.text = null
                textTimeRight.text = null
                textTimeTotal.text = null
                textTimeRemain.text = null
            }
        }
        viewModel.setArtwork(binding.artwork)
    }

    private fun onPlayingChanged(playing: Boolean) {
        binding.playing = playing
    }

    private fun onPlaybackRatioChanged(ratio: Float) {
        binding.seekBar.progress = (binding.seekBar.max * ratio).toInt()

        val song = viewModel.currentSong ?: return

        val elapsed = (song.duration * ratio).toLong()
        binding.textTimeLeft.text = elapsed.getTimeString()
        binding.textTimeRight.text =
            if (sharedPreferences.getBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, false))
                "-${(song.duration - elapsed).getTimeString()}"
            else song.durationString
        val remain = adapter.getItemsAfter((viewModel.currentPosition) + 1)
            .map { it.duration }
            .sum() + (song.duration - elapsed)
        binding.textTimeRemain.text =
            getString(R.string.bottom_sheet_time_remain, remain.getTimeString())
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

    private fun reloadBindingVariable() {
        binding.viewModel = binding.viewModel
        binding.isQueueNotEmpty = binding.isQueueNotEmpty
        binding.playing = binding.playing
        binding.queueUnTouchable = binding.queueUnTouchable
    }
}