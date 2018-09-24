package com.geckour.q.ui.pay

import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

class PaymentViewModel : ViewModel() {

    internal val save: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val saveSuccess: SingleLiveEvent<Boolean> = SingleLiveEvent()

    fun onClickSave() {
        save.call()
    }
}