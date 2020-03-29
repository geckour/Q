package com.geckour.q.presentation.pay

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.geckour.q.util.SingleLiveEvent

class PaymentViewModel : ViewModel() {

    companion object {
        private const val KYASH_URI = "kyash://qr/u/7842516381305069588"
    }

    internal val save: SingleLiveEvent<Unit> = SingleLiveEvent()
    internal val saveSuccess: SingleLiveEvent<Boolean> = SingleLiveEvent()

    private val kyashIntent = Intent(Intent.ACTION_VIEW, Uri.parse(KYASH_URI))

    fun onClickQR(context: Context) {
        context.startActivity(kyashIntent)
    }

    fun onClickSave() {
        save.call()
    }
}