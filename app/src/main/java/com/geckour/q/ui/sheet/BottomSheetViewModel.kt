package com.geckour.q.ui.sheet

import android.arch.lifecycle.ViewModel
import android.support.design.widget.BottomSheetBehavior
import com.geckour.q.util.SingleLifeEvent

class BottomSheetViewModel : ViewModel() {

    internal val sheetState: SingleLifeEvent<Int> = SingleLifeEvent()

    init {
        sheetState.value = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun onClickQueueButton() {
        sheetState.value = when (sheetState.value) {
            BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
            else -> BottomSheetBehavior.STATE_EXPANDED
        }
    }
}