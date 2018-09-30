package com.geckour.q.ui.pay

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.geckour.q.R
import com.geckour.q.databinding.FragmentPaymentBinding
import kotlinx.coroutines.experimental.Job

class PaymentFragment : Fragment() {

    companion object {
        fun newInstance(): PaymentFragment = PaymentFragment()
    }

    private lateinit var binding: FragmentPaymentBinding
    private val viewModel: PaymentViewModel by lazy {
        ViewModelProviders.of(requireActivity())[PaymentViewModel::class.java]
    }

    private var parentJob = Job()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewModel = viewModel
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        observeEvents()
    }

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
    }

    private fun observeEvents() {
        viewModel.save.observe(this, Observer {
            val success = insertQRImage()
            viewModel.saveSuccess.value = success
        })
    }

    private fun insertQRImage(): Boolean =
            context?.let { context ->
                val bitmap = (context.getDrawable(R.drawable.kyash_qr) as? BitmapDrawable)?.bitmap
                        ?: return@let false
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.Images.Media.TITLE, "Q_donation_QR.png")
                            put(MediaStore.Images.Media.DISPLAY_NAME, "Q donation QR code")
                            put(MediaStore.Images.Media.DESCRIPTION, "QR code for donation to author of Q")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        }) ?: return@let false
                bitmap.compress(Bitmap.CompressFormat.PNG, 100,
                        context.contentResolver.openOutputStream(uri))
            } ?: false
}