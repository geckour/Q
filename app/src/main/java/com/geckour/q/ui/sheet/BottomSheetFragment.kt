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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.work.WorkManager
import com.geckour.q.R
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.main.Queue
import com.geckour.q.ui.sheet.BottomSheetViewModel.Companion.PREF_KEY_SHOW_LOCK_TOUCH_QUEUE
import com.geckour.q.util.getIsNightMode
import com.geckour.q.util.getTimeString
import com.geckour.q.util.loadOrDefault
import com.geckour.q.util.showCurrentRemain
import com.geckour.q.worker.SleepTimerWorker
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class BottomSheetFragment : Fragment() {

    companion object {

        fun newInstance(): BottomSheetFragment = BottomSheetFragment()
    }

    private val viewModel by viewModel<BottomSheetViewModel>()
    private val mainViewModel by activityViewModel<MainViewModel>()
    private lateinit var binding: FragmentSheetBottomBinding

    //    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var centerControlButtonAnimator: ValueAnimator

    private val sharedPreferences by inject<SharedPreferences>()

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

//        behavior = BottomSheetBehavior.from((requireActivity() as MainActivity).binding.bottomSheet)
//        behavior.addBottomSheetCallback(bottomSheetCallback)
//        onBackPressedCallback =
//            object : OnBackPressedCallback(behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
//                override fun handleOnBackPressed() {
//                    viewModel.toggleSheetState.value = Unit
//                }
//            }

        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, onBackPressedCallback)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.isTouchLocked = sharedPreferences.getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)

        binding.queue.setContent {
            val domainTracks by mainViewModel.currentQueueFlow.collectAsState()
            val currentIndex by mainViewModel.currentIndexFlow.collectAsState()
            val isNightMode by LocalContext.current.getIsNightMode().collectAsState(initial = isSystemInDarkTheme())
            QTheme(darkTheme = isNightMode) {
                Surface(
                    color = QTheme.colors.colorBackgroundBottomSheet,
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
                ) {
                    Queue(
                        domainTracks = domainTracks,
                        currentIndex = currentIndex.coerceAtLeast(0),
                        scrollTo = currentIndex,
                        onQueueMove = mainViewModel::onQueueMove,
                        onChangeRequestedTrackInQueue = mainViewModel::onChangeRequestedTrackInQueue,
                        onRemoveTrackFromQueue = mainViewModel::onRemoveTrackFromQueue
                    )
                }
            }
        }

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
                                viewModel.onTransitionToArtist(
                                    mainViewModel,
                                    mainViewModel.currentDomainTrack
                                )
                                true
                            }

                            R.id.menu_transition_to_album -> {
                                viewModel.onTransitionToAlbum(
                                    mainViewModel,
                                    mainViewModel.currentDomainTrack
                                )
                                true
                            }

                            else -> false
                        }//.apply { behavior.state = BottomSheetBehavior.STATE_COLLAPSED }
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
                mainViewModel.currentDomainTrack
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentQueueFlow.collect {
                    binding.currentDomainTrack = mainViewModel.currentDomainTrack
                    binding.artwork.loadOrDefault(
                        mainViewModel.currentDomainTrack?.artworkUriString,
                        defaultResource = null
                    )
                    onQueueChanged(it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentIndexFlow.collect {
                    binding.currentDomainTrack = mainViewModel.currentDomainTrack
                    binding.artwork.loadOrDefault(
                        mainViewModel.currentDomainTrack?.artworkUriString,
                        defaultResource = null
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.currentPlaybackPositionFlow.collect {
                    onPlaybackPositionChanged(it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.currentRepeatModeFlow.collect {
                    onRepeatModeChanged(it)
                }
            }
        }
//        viewModel.toggleSheetState.observe(viewLifecycleOwner) {
//            behavior.state = when (val state = behavior.state) {
//                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_EXPANDED
//                BottomSheetBehavior.STATE_SETTLING -> state
//                else -> BottomSheetBehavior.STATE_COLLAPSED
//            }
//        }
        viewModel.showCurrentRemain.observe(viewLifecycleOwner) { showCurrentRemain ->
            showCurrentRemain ?: return@observe
            sharedPreferences.showCurrentRemain = showCurrentRemain
            val track = mainViewModel.currentDomainTrack ?: return@observe
            val ratio = binding.seekBar.progress / binding.seekBar.max.toFloat()
            val elapsedTime = (track.duration * ratio).toLong()
            setTimeRightText(track, elapsedTime)
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
        viewModel.onClickScrollToCurrentButton()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onQueueChanged(queue: List<DomainTrack>) {
        val totalTime = queue.map { it.duration }.sum()
        binding.textTimeTotal.text =
            requireContext().getString(R.string.bottom_sheet_time_total, totalTime.getTimeString())

//        if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
//            binding.buttonToggleVisibleQueue.shake()
//        }

        val noCurrentTrack = mainViewModel.currentDomainTrack == null
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

    private fun onPlayingChanged(isPlaying: Boolean) {
        viewModel.playing.value = isPlaying
        binding.isPlaying = isPlaying
        if (isPlaying.not()) {
            WorkManager.getInstance(requireContext().applicationContext)
                .cancelUniqueWork(SleepTimerWorker.NAME)
        }
    }

    private fun onPlaybackPositionChanged(playbackPosition: Long) {
        val track = mainViewModel.currentDomainTrack ?: return

        binding.seekBar.max = track.duration.toInt()
        binding.seekBar.progress = playbackPosition.toInt()

        binding.textTimeLeft.text = playbackPosition.getTimeString()
        setTimeRightText(track, playbackPosition)
        val remain = mainViewModel.currentQueueFlow.value
            .drop(mainViewModel.currentIndexFlow.value + 1)
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