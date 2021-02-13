package com.geckour.q.ui.pay

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
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
import com.geckour.q.ui.main.MainActivity
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.observe
import com.geckour.q.util.setIconTint
import com.geckour.q.util.toggleDayNight

class PaymentFragment : Fragment() {

    companion object {
        fun newInstance(): PaymentFragment = PaymentFragment()
    }

    private lateinit var binding: FragmentPaymentBinding
    private val viewModel: PaymentViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.kyashQr.setOnTouchListener { v, event ->
            v.elevation =
                (if (event.action == MotionEvent.ACTION_UP) 8 else 4) * resources.displayMetrics.density
            return@setOnTouchListener false
        }
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
            R.id.menu_toggle_daynight -> requireActivity().toggleDayNight()
            R.id.menu_sleep -> (requireActivity() as? MainActivity)?.showSleepTimerDialog()
            else -> return false
        }
        return true
    }


}