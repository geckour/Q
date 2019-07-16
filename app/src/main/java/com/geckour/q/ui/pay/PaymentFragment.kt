package com.geckour.q.ui.pay

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.geckour.q.R
import com.geckour.q.databinding.FragmentPaymentBinding
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.CrashlyticsBundledActivity
import com.geckour.q.util.isNightMode
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toNightModeInt

class PaymentFragment : Fragment() {

    companion object {
        fun newInstance(): PaymentFragment = PaymentFragment()
    }

    private lateinit var binding: FragmentPaymentBinding
    private val viewModel: PaymentViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewModel = viewModel
        binding.kyashQr.setOnTouchListener { v, event ->
            v.elevation = (if (event.action == MotionEvent.ACTION_UP) 8 else 4) * resources.displayMetrics.density
            return@setOnTouchListener false
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        observeEvents()
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.currentFragmentId.value = R.id.nav_pay
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.toggle_theme_toolbar, menu)

        menu.setIconTint()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toggle_daynight -> {
                val sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                val toggleTo = sharedPreferences.isNightMode.not()
                sharedPreferences.isNightMode = toggleTo
                (requireActivity() as CrashlyticsBundledActivity).delegate.localNightMode =
                        toggleTo.toNightModeInt
            }
            else -> return false
        }
        return true
    }

    private fun observeEvents() {
        viewModel.save.observe(this) {
            val success = insertQRImage()
            viewModel.saveSuccess.value = success
        }
    }

    private fun insertQRImage(): Boolean = context?.let { context ->
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
        bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                context.contentResolver.openOutputStream(uri)
        )
    } ?: false
}