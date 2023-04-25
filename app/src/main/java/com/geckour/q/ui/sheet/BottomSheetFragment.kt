package com.geckour.q.ui.sheet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.sheet.BottomSheetViewModel.Companion.PREF_KEY_SHOW_LOCK_TOUCH_QUEUE
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.catchAsNull
import com.geckour.q.util.getTimeString
import com.geckour.q.util.shake
import com.geckour.q.util.showCurrentRemain
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.updateFileMetadata
import com.geckour.q.worker.SleepTimerWorker
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class BottomSheetFragment : Fragment() {

    companion object {

        fun newInstance(): BottomSheetFragment = BottomSheetFragment()
    }

    private val viewModel by viewModel<BottomSheetViewModel>()
    private val mainViewModel by activityViewModel<MainViewModel>()
    private lateinit var binding: FragmentSheetBottomBinding
    private lateinit var adapter: QueueListAdapter
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var centerControlButtonAnimator: ValueAnimator

    private val sharedPreferences by inject<SharedPreferences>()

    private val touchLockListener: (View, MotionEvent) -> Boolean = { _, event ->
        behavior.onTouchEvent(
            requireActivity().findViewById(R.id.content_main), binding.sheet, event
        )
        true
    }

    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        var from: Int? = null
        var to: Int? = null

        override fun onMove(
            recyclerView: RecyclerView,
            fromHolder: RecyclerView.ViewHolder,
            toHolder: RecyclerView.ViewHolder
        ): Boolean {
            (fromHolder as QueueListAdapter.ViewHolder).dismissPopupMenu()

            return true
        }

        override fun onMoved(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            fromPos: Int,
            target: RecyclerView.ViewHolder,
            toPos: Int,
            x: Int,
            y: Int
        ) {
            super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)

            if (from == null) from = fromPos
            to = toPos

            adapter.notifyItemMoved(fromPos, toPos)
        }

        override fun onSwiped(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)

            val from = this.from ?: return
            val to = this.to ?: return

            if (viewHolder == null) {
                mainViewModel.onQueueMove(from, to)

                this.from = null
                this.to = null
            }
        }
    }
    private val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(v: View, dy: Float) {
            binding.sheet.progress = dy
        }

        @SuppressLint("SwitchIntDef")
        override fun onStateChanged(v: View, state: Int) {
            binding.buttonToggleVisibleQueue.setImageResource(
                when (state) {
                    BottomSheetBehavior.STATE_EXPANDED -> R.drawable.ic_collapse
                    else -> R.drawable.ic_queue
                }
            )
            if (state == BottomSheetBehavior.STATE_EXPANDED) scrollToCurrent()
            onBackPressedCallback.isEnabled = state == BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSheetBottomBinding.inflate(inflater, container, false)

        behavior = BottomSheetBehavior.from((requireActivity() as MainActivity).binding.bottomSheet)
        behavior.addBottomSheetCallback(bottomSheetCallback)
        onBackPressedCallback =
            object : OnBackPressedCallback(behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                override fun handleOnBackPressed() {
                    viewModel.toggleSheetState.value = Unit
                }
            }

        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, onBackPressedCallback)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.isTouchLocked = sharedPreferences.getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)
        adapter = QueueListAdapter(
            onNewQueue = { actionType, track ->
                mainViewModel.onNewQueue(listOf(track), actionType, OrientedClassType.TRACK)
            },
            onEditMetadata = { tracks ->
                lifecycleScope.launchWhenResumed {
                    val db = DB.getInstance(binding.root.context)

                    mainViewModel.onLoadStateChanged(true)
                    val t = tracks.mapNotNull { db.trackDao().get(it.id) }
                    mainViewModel.onLoadStateChanged(false)

                    requireContext().showFileMetadataUpdateDialog(t) { binding ->
                        lifecycleScope.launchWhenResumed {
                            mainViewModel.onLoadStateChanged(true)
                            binding.updateFileMetadata(requireContext(), db, t)
                            mainViewModel.onLoadStateChanged(false)
                        }
                    }
                }
            },
            onQueueRemove = { position ->
                mainViewModel.onQueueRemove(position)
            },
            onClickArtist = { artist ->
                mainViewModel.selectedArtist.value = artist
            },
            onClickAlbum = { album ->
                mainViewModel.selectedAlbum.value = album
            },
            onClickTrack = {
                mainViewModel.onRequestNavigate()
            },
            onChangeCurrentPosition = { position ->
                mainViewModel.onChangeRequestedPositionInQueue(position)
            },
            onDeleteTrack = { track ->
                mainViewModel.deleteTrack(track)
            }
        )
        binding.recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.sheet.setOnTouchListener { _, _ -> true }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
                Unit

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mainViewModel.onNewSeekBarProgress(seekBar.progress.toLong())
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

        binding.artwork.setOnLongClickListener {
            return@setOnLongClickListener context?.let { context ->
                PopupMenu(context, binding.artwork).apply {
                    setOnMenuItemClickListener {
                        return@setOnMenuItemClickListener when (it.itemId) {
                            R.id.menu_transition_to_artist -> {
                                viewModel.onTransitionToArtist(mainViewModel, adapter.currentItem)
                                true
                            }
                            R.id.menu_transition_to_album -> {
                                viewModel.onTransitionToAlbum(mainViewModel, adapter.currentItem)
                                true
                            }
                            else -> false
                        }.apply { behavior.state = BottomSheetBehavior.STATE_COLLAPSED }
                    }
                    inflate(R.menu.track_transition)
                }.show()
                true
            } ?: false
        }

        binding.buttonControllerLeft.apply {
            setOnClickListener { mainViewModel.onPrev() }
            setOnLongClickListener { mainViewModel.onRewind() }
        }

        binding.buttonControllerCenter.setOnClickListener {
            mainViewModel.onPlayOrPause(viewModel.playing.value)
        }

        binding.buttonControllerRight.apply {
            setOnClickListener { mainViewModel.onNext() }
            setOnLongClickListener { mainViewModel.onFF() }
        }

        binding.buttonRepeat.setOnClickListener { mainViewModel.onClickRepeatButton() }

        binding.buttonShuffle.apply {
            setOnClickListener { mainViewModel.onClickShuffleButton() }
            setOnLongClickListener { mainViewModel.onLongClickShuffleButton() }
        }

        binding.buttonShare.setOnClickListener {
            viewModel.onClickShareButton(
                requireContext(),
                adapter.currentItem
            )
        }

        binding.buttonToggleVisibleQueue.setOnClickListener { viewModel.onClickQueueButton() }

        binding.textTimeRight.setOnClickListener { viewModel.onClickTimeRight() }

        binding.buttonScrollToCurrent.setOnClickListener {
            viewModel.onClickScrollToCurrentButton()
        }

        binding.buttonTouchLock.setOnClickListener {
            changeTouchLocked(checkNotNull(binding.isTouchLocked).not())
        }

        binding.buttonClearQueue.setOnClickListener { mainViewModel.onClickClearQueueButton() }

        centerControlButtonAnimator =
            ObjectAnimator.ofFloat(binding.buttonControllerCenter, View.ROTATION, 360f, 0f).apply {
                repeatMode = ValueAnimator.RESTART
                repeatCount = ValueAnimator.INFINITE
                duration = 1000
            }

        resetMarquee()

        observeEvents()
    }

    private fun changeTouchLocked(locked: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, locked).apply()
        binding.isTouchLocked = locked
        binding.recyclerView.setOnTouchListener(if (locked) touchLockListener else null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentSourcePathsFlow.collect { sourcePaths ->
                    val queue = sourcePaths.mapNotNull {
                        DB.getInstance(requireContext())
                            .trackDao()
                            .getBySourcePath(it)
                            ?.toDomainTrack()
                    }
                    onQueueChanged(queue)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentIndexFlow.collect {
                    submitQueueWithCurrentIndex(currentIndex = it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentPlaybackInfoFlow.collect { (playWhenReady, playbackState) ->
                    onPlayingChanged(
                        when (playbackState) {
                            Player.STATE_READY -> {
                                playWhenReady
                            }
                            else -> false
                        }
                    )
                    if (playWhenReady && playbackState == Player.STATE_BUFFERING) {
                        centerControlButtonAnimator.start()
                    } else {
                        centerControlButtonAnimator.end()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentPlaybackPositionFlow.collect {
                    onPlaybackPositionChanged(it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentRepeatModeFlow.collect {
                    onRepeatModeChanged(it)
                }
            }
        }
        viewModel.toggleSheetState.observe(viewLifecycleOwner) {
            behavior.state = when (val state = behavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.STATE_SETTLING -> state
                else -> BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        viewModel.showCurrentRemain.observe(viewLifecycleOwner) { showCurrentRemain ->
            showCurrentRemain ?: return@observe
            sharedPreferences.showCurrentRemain = showCurrentRemain
            val track = adapter.currentList.firstOrNull { it.nowPlaying } ?: return@observe
            val ratio = binding.seekBar.progress / binding.seekBar.max.toFloat()
            val elapsedTime = (track.duration * ratio).toLong()
            setTimeRightText(track, elapsedTime)
        }
        viewModel.scrollToCurrent.observe(viewLifecycleOwner) {
            if (it != true) return@observe
            scrollToCurrent()
            viewModel.onScrollToCurrentInvoked()
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
            binding.recyclerView.smoothScrollToPosition(adapter.currentIndex)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onQueueChanged(queue: List<DomainTrack>) {
        val totalTime = queue.map { it.duration }.sum()
        binding.textTimeTotal.text =
            requireContext().getString(R.string.bottom_sheet_time_total, totalTime.getTimeString())

        val changed = adapter.currentList != queue
        if (changed && behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            binding.buttonToggleVisibleQueue.shake()
        }

        submitQueueWithCurrentIndex(queue = queue)
    }

    private fun submitQueueWithCurrentIndex(
        queue: List<DomainTrack>? = null,
        currentIndex: Int? = null
    ) = lifecycleScope.launch {
        val newQueue = queue
            ?: mainViewModel.currentSourcePathsFlow.value
                .mapNotNull { it.toDomainTrack(DB.getInstance(requireContext())) }
                .ifEmpty { adapter.currentList }
        val newCurrentIndex = currentIndex
            ?: mainViewModel.currentIndexFlow.value.let { currentIndex ->
                if (currentIndex < 0) {
                    adapter.currentIndex.let { if (it in newQueue.indices) it else 0 }
                } else currentIndex
            }
        val indexChanged = adapter.currentIndex != newCurrentIndex

        adapter.submitList(newQueue.mapIndexed { index, domainTrack ->
            domainTrack.copy(nowPlaying = index == newCurrentIndex)
        }) {
            binding.currentDomainTrack = adapter.currentItem
            Glide.with(binding.artwork)
                .load(
                    adapter.currentItem
                        ?.artworkUriString
                        ?.let { catchAsNull { File(it) } }
                )
                .override(1000)
                .into(binding.artwork)
            if (indexChanged) onPlaybackPositionChanged(0)

            val noCurrentTrack = adapter.currentItem == null
            binding.seekBar.setOnTouchListener { _, _ -> noCurrentTrack }
            if (noCurrentTrack) {
                with(binding) {
                    textTimeLeft.text = null
                    textTimeRight.text = null
                    textTimeTotal.text = null
                    textTimeRemain.text = null
                    seekBar.progress = 0
                }
            } else {
                onPlaybackPositionChanged(
                    mainViewModel.currentPlaybackPositionFlow.value
                )
            }

            resetMarquee()
        }
    }

    private fun onPlayingChanged(isPlaying: Boolean) {
        viewModel.playing.value = isPlaying
        binding.isPlaying = isPlaying
        if (isPlaying.not()) {
            WorkManager.getInstance(requireContext().applicationContext)
                .cancelUniqueWork(SleepTimerWorker.NAME)
        }
    }

    private fun onPlaybackPositionChanged(playbackPosition: Long) {
        val track = adapter.currentItem ?: return

        binding.seekBar.max = track.duration.toInt()
        binding.seekBar.progress = playbackPosition.toInt()

        binding.textTimeLeft.text = playbackPosition.getTimeString()
        setTimeRightText(track, playbackPosition)
        val remain = adapter.getItemsAfter(adapter.currentIndex + 1)
            .map { it.duration }
            .sum() + (track.duration - playbackPosition)
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