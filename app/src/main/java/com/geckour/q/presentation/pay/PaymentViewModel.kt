package com.geckour.q.presentation.pay

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.geckour.q.R

class PaymentViewModel : ViewModel() {

    companion object {
        private const val KYASH_URI_STRING = "kyash://qr/u/7842516381305069588"
    }

    private val _saveSuccess = MutableLiveData<Boolean>()
    internal val saveSuccess: LiveData<Boolean> = _saveSuccess.distinctUntilChanged()

    private val kyashIntent = Intent(Intent.ACTION_VIEW, Uri.parse(KYASH_URI_STRING))

    fun onClickQR(context: Context) {
        context.startActivity(kyashIntent)
    }

    fun onClickSave(context: Context) {
        val success = insertQRImage(context)
        _saveSuccess.value = success
    }

    private fun insertQRImage(context: Context): Boolean {
        val bitmap = (context.getDrawable(R.drawable.kyash_qr) as? BitmapDrawable)?.bitmap
            ?: return false
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "Q_donation_QR.png")
                put(MediaStore.Images.Media.DISPLAY_NAME, "Q donation QR code")
                put(MediaStore.Images.Media.DESCRIPTION, "QR code for donation to author of Q")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }) ?: return false
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return true
    }
}