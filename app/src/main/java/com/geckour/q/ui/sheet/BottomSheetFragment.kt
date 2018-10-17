package com.geckour.q.ui.sheet

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
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.ui.share.SharingActivity
import com.geckour.q.util.*
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext

class BottomSheetFragment : ScopedFragment() {

    companion object {
        private const val PREF_KEY_SHOW_CURRENT_REMAIN = "pref_key_show_current_remain"
        private const val PREF_KEY_SHOW_LOCK_TOUCH_QUEUE = "pref_key_lock_touch_queue"
    }

    private val viewModel: BottomSheetViewModel by lazy {
        ViewModelProviders.of(requireActivity())[BottomSheetViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentSheetBottomBinding
    private lateinit var adapter: QueueListAdapter
    private lateinit var behavior: BottomSheetBehavior<MotionLayout>

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSheetBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QueueListAdapter(mainViewModel)
        binding.recyclerView.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            var from: Int? = null
            var to: Int? = null

            override fun onMove(recyclerView: RecyclerView, fromHolder: RecyclerView.ViewHolder, toHolder: RecyclerView.ViewHolder): Boolean {
                val from = fromHolder.adapterPosition
                val to = toHolder.adapterPosition

                if (this.from == null) this.from = from
                this.to = to

                adapter.move(from, to)
                (fromHolder as QueueListAdapter.ViewHolder).dismissPopupMenu()

                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, position: Int) {

            }

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

        listOf(binding.buttonControllerLeft,
                binding.buttonControllerCenter,
                binding.buttonControllerRight).forEach {
            it.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        viewModel.playbackButton.value = PlaybackButton.UNDEFINED
                    }
                }

                return@setOnTouchListener false
            }
        }

        viewModel.touchLock.value = sharedPreferences
                .getBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, false)

        behavior = BottomSheetBehavior.from(
                (requireActivity() as MainActivity).binding.root.findViewById(R.id.bottom_sheet))
        behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(v: View, dy: Float) {
                binding.sheet.progress = dy
            }

            @SuppressLint("SwitchIntDef")
            override fun onStateChanged(v: View, state: Int) {
                viewModel.sheetState = state
                reloadBindingVariable()
                binding.buttonToggleVisibleQueue
                        .setImageResource(when (state) {
                            BottomSheetBehavior.STATE_EXPANDED -> R.drawable.ic_collapse
                            else -> R.drawable.ic_queue
                        })
                if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    viewModel.scrollToCurrent.call()
                }
            }
        })

        binding.touchBlockWall.setOnTouchListener { _, event ->
            behavior.onTouchEvent(
                    requireActivity().findViewById(R.id.coordinator_main), binding.sheet, event)
            true
        }

        viewModel.currentQueue.value = emptyList()

        observeEvents()
    }

    override fun onResume() {
        super.onResume()

        viewModel.reAttatch()
        binding.viewModel = viewModel
    }

    private fun observeEvents() {
        viewModel.artworkLongClick.observe(this) { _ ->
            context?.also { context ->
                PopupMenu(context, binding.artwork).apply {
                    setOnMenuItemClickListener {
                        return@setOnMenuItemClickListener when (it.itemId) {
                            R.id.menu_transition_to_artist -> {
                                launch {
                                    mainViewModel.selectedArtist.value =
                                            withContext((Dispatchers.IO)) {
                                                viewModel.currentSong?.artist?.let {
                                                    DB.getInstance(context).artistDao()
                                                            .findArtist(it).firstOrNull()
                                                            ?.toDomainModel()
                                                }
                                            }
                                }
                                true
                            }
                            R.id.menu_transition_to_album -> {
                                launch {
                                    mainViewModel.selectedAlbum.value =
                                            withContext(Dispatchers.IO) {
                                                viewModel.currentSong?.albumId?.let {
                                                    DB.getInstance(context).albumDao()
                                                            .get(it)
                                                            ?.toDomainModel()
                                                }
                                            }
                                }
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

        viewModel.currentQueue.observe(this) {
            val changed = (adapter.getItemIds() == it?.map { it.id }).not()
            val state = it?.isNotEmpty() ?: false

            adapter.setItems(it ?: emptyList())

            binding.isQueueNotEmpty = state

            val totalTime = it?.asSequence()?.map { it.duration }?.sum()
            binding.textTimeTotal.text = totalTime?.let {
                context?.getString(R.string.bottom_sheet_time_total, it.getTimeString())
            }

            viewModel.currentPosition.value = if (state) viewModel.currentPosition.value else -1

            if (changed && behavior.state == BottomSheetBehavior.STATE_COLLAPSED)
                binding.buttonToggleVisibleQueue.shake()

            mainViewModel.loading.value = false
        }

        viewModel.currentPosition.observe(this) {
            val song = adapter.getItem(it)

            binding.seekBar.setOnTouchListener { _, _ -> song == null }

            adapter.setNowPlayingPosition(it)

            if (song?.id != viewModel.currentSong?.id) {
                viewModel.currentSong = song
                context?.also { context ->
                    val db = DB.getInstance(context)


                    song?.also {
                        launch(Dispatchers.IO) {
                            db.trackDao().increasePlaybackCount(it.id)
                            db.albumDao().increasePlaybackCount(it.albumId)
                            db.artistDao().apply {
                                var artist = findArtist(it.artist).firstOrNull()
                                if (artist == null) {
                                    db.albumDao().get(it.albumId)
                                            ?.artistId?.apply { artist = get(this) }
                                }
                                artist?.apply {
                                    increasePlaybackCount(this.id)
                                }
                            }
                        }
                    }

                    launch(Dispatchers.IO) {
                        val model = viewModel.currentSong?.albumId?.let {
                            db.getArtworkUriStringFromId(it)
                                    ?: R.drawable.ic_empty
                        }
                        val drawable = model?.let {
                            Glide.with(requireContext())
                                    .asDrawable()
                                    .load(it)
                                    .submit()
                                    .get()
                        }
                        withContext(Dispatchers.Main) {
                            binding.artwork.setImageDrawable(drawable)
                        }
                    }
                }
                if (song == null) {
                    binding.textTimeLeft.text = null
                    binding.textTimeRight.text = null
                    binding.seekBar.progress = 0
                    binding.textTimeTotal.text = null
                    binding.textTimeRemain.text = null
                } else {
                    binding.textTimeRight.text =
                            if (sharedPreferences.getBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, false)) {
                                viewModel.playbackRatio.value?.let {
                                    val remain = (song.duration * (1 - it)).toLong()
                                    "-${remain.getTimeString()}"
                                }
                            } else song.duration.getTimeString()
                }
                binding.viewModel = viewModel
            }
        }

        viewModel.playing.observe(this) {
            binding.playing = it
        }

        viewModel.playbackRatio.observe(this) {
            if (it == null) return@observe
            binding.seekBar.progress = (binding.seekBar.max * it).toInt()
            val song = adapter.getItem(viewModel.currentPosition.value) ?: return@observe
            val elapsed = (song.duration * it).toLong()
            binding.textTimeLeft.text = elapsed.getTimeString()
            binding.textTimeRight.text =
                    if (sharedPreferences.getBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, false))
                        "-${(song.duration - elapsed).getTimeString()}"
                    else song.durationString
            val remain = adapter.getItemsAfter((viewModel.currentPosition.value ?: 0) + 1)
                    .map { it.duration }.sum() + (song.duration - elapsed)
            binding.textTimeRemain.text =
                    getString(R.string.bottom_sheet_time_remain, remain.getTimeString())
        }

        viewModel.repeatMode.observe(this) {
            if (it == null) return@observe
            binding.buttonRepeat.apply {
                setImageResource(when (it) {
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat_off
                })
                visibility = View.VISIBLE
            }
        }

        viewModel.scrollToCurrent.observe(this) {
            binding.recyclerView.smoothScrollToPosition(viewModel.currentPosition.value ?: 0)
        }

        viewModel.toggleCurrentRemain.observe(this) {
            val changeTo = sharedPreferences
                    .getBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, false)
                    .not()
            sharedPreferences.edit()
                    .putBoolean(PREF_KEY_SHOW_CURRENT_REMAIN, changeTo)
                    .apply()
        }

        viewModel.touchLock.observe(this) {
            if (it == null) return@observe
            sharedPreferences.edit()
                    .putBoolean(PREF_KEY_SHOW_LOCK_TOUCH_QUEUE, it)
                    .apply()
            binding.queueUnTouchable = it
        }

        viewModel.share.observe(this) {
            if (it == null) return@observe
            startActivity(SharingActivity.getIntent(requireContext(), it))
        }
    }

    private fun reloadBindingVariable() {
        binding.viewModel = binding.viewModel
        binding.isQueueNotEmpty = binding.isQueueNotEmpty
        binding.playing = binding.playing
        binding.queueUnTouchable = binding.queueUnTouchable
    }
}