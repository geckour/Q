package com.geckour.q.ui.sheet

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.res.ColorStateList
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.q.R
import com.geckour.q.databinding.FragmentSheetBottomBinding
import com.geckour.q.ui.MainActivity
import com.geckour.q.ui.MainViewModel

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
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.viewModel = viewModel

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
            binding.isControllerActive = state
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
    }
}