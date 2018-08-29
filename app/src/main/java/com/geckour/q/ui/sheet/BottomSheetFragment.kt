package com.geckour.q.ui.sheet

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geckour.q.databinding.FragmentSheetBottomBinding
import kotlinx.android.synthetic.main.app_bar_main.*

class BottomSheetFragment : Fragment() {

    private val viewModel: BottomSheetViewModel = BottomSheetViewModel()
    private lateinit var binding: FragmentSheetBottomBinding
    private lateinit var behavior: BottomSheetBehavior<*>

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSheetBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.viewModel = viewModel
        behavior = BottomSheetBehavior.from(bottom_sheet.view)
        observeEvents()
    }

    private fun observeEvents() {
        viewModel.sheetState.observe(this, Observer {
            if (it == null) return@Observer
            behavior.state = it
        })
    }
}