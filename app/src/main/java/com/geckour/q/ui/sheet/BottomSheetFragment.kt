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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.geckour.q.R
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.sheet.BottomSheetViewModel.Companion.PREF_KEY_SHOW_LOCK_TOUCH_QUEUE
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isNightMode
import com.geckour.q.util.loadOrDefault
import com.geckour.q.util.moved
import com.geckour.q.util.shake
import com.geckour.q.util.showCurrentRemain
import com.geckour.q.worker.SleepTimerWorker
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
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
    private lateinit var behavior: BottomSheetBehavior<View>
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

    @Composable
    fun Queue(domainTracks: List<DomainTrack>, currentIndex: Int) {
        var items by remember { mutableStateOf(domainTracks) }
        SideEffect {
            items = domainTracks
        }
        val reorderableState = rememberReorderableLazyListState(
            onMove = { from, to -> items = items.moved(from.index, to.index) },
            onDragEnd = { from, to -> mainViewModel.onQueueMove(from, to) }
        )
        remember {
            viewModel.scrollToCurrent.map {
                if (it > -1) {
                    reorderableState.listState.animateScrollToItem(
                        mainViewModel.currentIndexFlow.value
                    )
                }
            }
        }.collectAsState(initial = -1)

        LazyColumn(
            state = reorderableState.listState,
            modifier = Modifier
                .reorderable(reorderableState)
                .detectReorderAfterLongPress(reorderableState)
        ) {
            itemsIndexed(items, { _, item -> item.id }) { index, domainTrack ->
                ReorderableItem(
                    reorderableState = reorderableState,
                    key = domainTrack.id
                ) { isDragging ->
                    QueueItem(
                        domainTrack = domainTrack,
                        index = index,
                        currentIndex = currentIndex,
                        isDragging = isDragging
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun QueueItem(
        modifier: Modifier = Modifier,
        domainTrack: DomainTrack,
        index: Int,
        currentIndex: Int,
        isDragging: Boolean
    ) {
//        val popupMenu = PopupMenu(LocalContext.current, LocalView.current).apply {
//            setOnMenuItemClickListener { menuItem ->
//                when (menuItem.itemId) {
//                    R.id.menu_transition_to_artist -> {
//                        mainViewModel.onRequestNavigate(domainTrack.artist)
//                    }
//
//                    R.id.menu_transition_to_album -> {
//                        mainViewModel.onRequestNavigate(domainTrack.album)
//                    }
//
//                    R.id.menu_insert_all_next,
//                    R.id.menu_insert_all_last,
//                    R.id.menu_override_all -> {
//                        mainViewModel.onNewQueue(
//                            listOf(domainTrack),
//                            when (menuItem.itemId) {
//                                R.id.menu_insert_all_next -> {
//                                    InsertActionType.NEXT
//                                }
//
//                                R.id.menu_insert_all_last -> {
//                                    InsertActionType.LAST
//                                }
//
//                                R.id.menu_override_all -> {
//                                    InsertActionType.OVERRIDE
//                                }
//
//                                else -> return@setOnMenuItemClickListener false
//                            },
//                            OrientedClassType.TRACK
//                        )
//                    }
//
//                    R.id.menu_edit_metadata -> {
//                        lifecycleScope.launch {
//                            val db = DB.getInstance(requireContext())
//                            val tracks = mainViewModel.currentQueueFlow.value.mapNotNull {
//                                db.trackDao().get(it.id)
//                            }
//                            requireContext().showFileMetadataUpdateDialog(
//                                tracks,
//                                onUpdate = { binding ->
//                                    lifecycleScope.launch {
//                                        binding.updateFileMetadata(requireContext(), db, tracks)
//                                    }
//                                }
//                            )
//                        }
//                    }
//
//                    R.id.menu_delete_track -> {
//                        mainViewModel.deleteTrack(domainTrack)
//                    }
//                }
//
//                return@setOnMenuItemClickListener true
//            }
//            inflate(R.menu.queue)
//        }

        val elevation by animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp, label = "")

        Card(
            shape = RectangleShape,
            elevation = elevation,
            backgroundColor = if (index == currentIndex) QTheme.colors.colorWeekAccent else QTheme.colors.colorBackgroundBottomSheet,
            onClick = { mainViewModel.onChangeRequestedTrackInQueue(domainTrack) },
            modifier = modifier
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index == currentIndex) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_spectrum),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(color = QTheme.colors.colorAccent),
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            modifier = Modifier.size(48.dp),
                            model = domainTrack.artworkUriString ?: R.drawable.ic_empty,
                            contentDescription = null
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .width(IntrinsicSize.Max)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = domainTrack.title,
                                color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${domainTrack.artist.title} - ${domainTrack.album.title}",
                                color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { mainViewModel.onRemoveTrackFromQueue(domainTrack) },
                            modifier = Modifier
                                .padding(12.dp)
                                .size(20.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_remove),
                                contentDescription = null,
                                tint = QTheme.colors.colorButtonNormal
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.height(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.width(48.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                color = QTheme.colors.colorTextPrimary,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = "${domainTrack.codec}・${domainTrack.bitrate}kbps・${domainTrack.sampleRate}kHz",
                            color = if (domainTrack.ignored != false) QTheme.colors.colorInactive else QTheme.colors.colorTextPrimary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .width(IntrinsicSize.Max),
                        )
                        Text(
                            text = domainTrack.durationString,
                            color = QTheme.colors.colorTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.isTouchLocked = sharedPreferences.getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)

        binding.queue.setContent {
            val domainTracks by mainViewModel.currentQueueFlow.collectAsState()
            val currentIndex by mainViewModel.currentIndexFlow.collectAsState()
            QTheme(darkTheme = sharedPreferences.isNightMode) {
                Surface(
                    color = QTheme.colors.colorBackgroundBottomSheet,
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
                ) {
                    Queue(
                        domainTracks = domainTracks,
                        currentIndex = currentIndex.coerceAtLeast(0)
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

        if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            binding.buttonToggleVisibleQueue.shake()
        }

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