package com.geckour.q.presentation.pay

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PaymentViewModel : ViewModel() {

    companion object {
        private const val KYASH_URI = "kyash://qr/u/7842516381305069588"
    }

    internal val save: MutableLiveData<Unit> = MutableLiveData()
    internal val saveSuccess: MutableLiveData<Boolean> = MutableLiveData()

    private val kyashIntent = Intent(Intent.ACTION_VIEW, Uri.parse(KYASH_URI))

    fun onClickQR(context: Context) {
        context.startActivity(kyashIntent)
    }

    fun onClickSave() {
        save.value = null
    }
}