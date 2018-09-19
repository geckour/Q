package com.geckour.q.ui.sheet

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.res.ColorStateList
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.ui.MainActivity
import com.geckour.q.ui.MainViewModel
import com.geckour.q.util.getArtworkUriFromAlbumId
import com.google.android.exoplayer2.Player

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
            override fun onMove(recyclerView: RecyclerView, fromHolder: RecyclerView.ViewHolder, toHolder: RecyclerView.ViewHolder): Boolean {
                val from = fromHolder.adapterPosition
                val to = toHolder.adapterPosition
                adapter.move(from, to)
                (fromHolder as QueueListAdapter.ViewHolder).dismissPopupMenu()
                mainViewModel.onQueueSwap(from, to)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, position: Int) {

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
                        viewModel.playbackButton.value =
                                BottomSheetViewModel.PlaybackButton.UNDEFINED
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

            override fun onStateChanged(v: View, state: Int) {
                viewModel.sheetState.value = state
            }
        })

        observeEvents()

        viewModel.currentQueue.value = emptyList()
    }

    override fun onResume() {
        super.onResume()

        viewModel.restoreState()
    }

    private fun observeEvents() {
        viewModel.sheetState.observe(this, Observer {
            if (it == null) return@Observer
            behavior.state = it
            binding.buttonToggleVisibleQueue.setImageResource(
                    when (it) {
                        BottomSheetBehavior.STATE_EXPANDED -> R.drawable.ic_collapse
                        else -> R.drawable.ic_queue
                    }
            )
        })

        viewModel.currentQueue.observe(this, Observer {
            adapter.setItems(it ?: emptyList())
            val state = it?.isNotEmpty() ?: false
            binding.isQueueNotEmpty = state
            binding.seekBar.apply {
                thumbTintList =
                        if (state) {
                            setOnTouchListener(null)
                            ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                                    R.color.colorPrimaryDark))
                        } else {
                            setOnTouchListener { _, _ -> true }
                            ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                                    R.color.colorTintInactive))
                        }
            }
        })

        viewModel.currentPosition.observe(this, Observer {
            val song = adapter.getItem(it)

            Glide.with(binding.artwork)
                    .load(song?.albumId?.let { getArtworkUriFromAlbumId(it) })
                    .into(binding.artwork)
            binding.textSong.text = song?.name
            binding.textArtist.text = song?.artist
            binding.textTimeRight.text = song?.duration?.getTimeString()
            adapter.setNowPlaying(it)
        })

        viewModel.playing.observe(this, Observer {
            binding.playing = it
        })

        viewModel.playbackRatio.observe(this, Observer {
            if (it == null) return@Observer
            binding.seekBar.progress = (binding.seekBar.max * it).toInt()
            val song = adapter.getItem(viewModel.currentPosition.value) ?: return@Observer
            binding.textTimeLeft.text = (song.duration * it).toLong().getTimeString()
        })

        viewModel.repeatMode.observe(this, Observer {
            if (it == null) return@Observer
            binding.buttonRepeat.apply {
                setImageResource(
                        if (it == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one
                        else R.drawable.ic_repeat)
                imageTintList = ColorStateList.valueOf(requireContext().getColor(
                        if (it == Player.REPEAT_MODE_OFF) R.color.colorTintInactive
                        else R.color.colorAccent))
                visibility = View.VISIBLE
            }
        })
    }

    private fun Long.getTimeString(): String {
        val hour = this / 3600000
        val minute = (this / 60000) % 3600
        val second = (this / 1000) % 60
        return if (hour > 0) String.format("%02d", hour) else "" + String.format("%02d:%02d", minute, second)
    }
}