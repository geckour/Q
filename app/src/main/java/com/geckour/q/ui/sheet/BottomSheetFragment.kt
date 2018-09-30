package com.geckour.q.ui.sheet

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.domain.model.PlaybackButton
import com.geckour.q.ui.MainActivity
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getArtworkUriStringFromId
import com.geckour.q.util.getTimeString
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

class BottomSheetFragment : Fragment() {

    private val viewModel: BottomSheetViewModel by lazy {
        ViewModelProviders.of(requireActivity())[BottomSheetViewModel::class.java]
    }
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
    }
    private lateinit var binding: FragmentSheetBottomBinding
    private lateinit var adapter: QueueListAdapter
    private lateinit var behavior: BottomSheetBehavior<*>

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

        behavior = BottomSheetBehavior.from(
                (requireActivity() as MainActivity).binding.root
                        .findViewById<View>(R.id.bottom_sheet))
        behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(v: View, dy: Float) {
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
            }
        })

        viewModel.currentQueue.value = emptyList()
    }

    override fun onResume() {
        super.onResume()

        observeEvents()
    }

    private fun observeEvents() {
        viewModel.toggleSheetState.observe(this) {
            behavior.state = when (behavior.state) {
                BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
                else -> BottomSheetBehavior.STATE_EXPANDED
            }
        }

        viewModel.currentQueue.observe(this) {
            adapter.setItems(it ?: emptyList())
            val state = it?.isNotEmpty() ?: false
            binding.isQueueNotEmpty = state
            val totalTime = it?.asSequence()?.map { it.duration }?.sum()
            binding.textTimeTotal.text = totalTime?.let {
                context?.getString(R.string.bottom_sheet_time_total, it.getTimeString())
            }
            viewModel.currentPosition.value = if (state) viewModel.currentPosition.value else 0
            mainViewModel.loading.value = false
        }

        viewModel.currentPosition.observe(this) {
            val song = adapter.getItem(it)

            context?.let { context ->
                launch(UI) {
                    val model = song?.albumId?.let {
                        DB.getInstance(context)
                                .getArtworkUriStringFromId(it).await() ?: R.drawable.ic_empty
                    }
                    Glide.with(binding.artwork)
                            .load(model)
                            .into(binding.artwork)
                }
            }
            binding.textSong.text = song?.name
            binding.textArtist.text = song?.artist
            binding.seekBar.apply {
                context?.also {
                    thumbTintList =
                            if (song != null) {
                                setOnTouchListener(null)
                                ColorStateList.valueOf(ContextCompat.getColor(it,
                                        R.color.colorPrimaryDark))
                            } else {
                                setOnTouchListener { _, _ -> true }
                                ColorStateList.valueOf(ContextCompat.getColor(it,
                                        R.color.colorTintInactive))
                            }
                }
            }
            if (song == null) {
                binding.textTimeLeft.text = null
                binding.seekBar.progress = 0
                binding.textTimeTotal.text = null
            }
            binding.textTimeRight.text = song?.durationString
            adapter.setNowPlaying(it)

            if (it != null && song != null && behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                binding.recyclerView.smoothScrollToPosition(it)
            }
        }

        viewModel.playing.observe(this) {
            binding.playing = it
        }

        viewModel.playbackRatio.observe(this) {
            if (it == null) return@observe
            binding.seekBar.progress = (binding.seekBar.max * it).toInt()
            val song = adapter.getItem(viewModel.currentPosition.value) ?: return@observe
            binding.textTimeLeft.text = (song.duration * it).toLong().getTimeString()
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
    }
}