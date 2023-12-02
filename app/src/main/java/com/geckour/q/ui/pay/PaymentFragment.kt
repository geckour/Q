package com.geckour.q.ui.pay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geckour.q.R
import com.geckour.q.data.BillingApiClient
import com.geckour.q.databinding.FragmentPaymentBinding
import com.geckour.q.ui.main.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class PaymentFragment : Fragment() {

    companion object {
        fun newInstance(): PaymentFragment = PaymentFragment()
    }

    private lateinit var binding: FragmentPaymentBinding
    private lateinit var billingApiClient: BillingApiClient
    private val mainViewModel by activityViewModel<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        billingApiClient = BillingApiClient(
            requireContext(),
            onError = {
                Snackbar.make(
                    binding.root,
                    R.string.payment_message_error_failed_to_start,
                    Snackbar.LENGTH_LONG
                ).show()
            },
            onDonateCompleted = {
                when (it) {
                    BillingApiClient.BillingApiResult.SUCCESS -> {
                        billingApiClient.requestUpdate()
                        Snackbar.make(
                            binding.root,
                            R.string.payment_message_success,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    BillingApiClient.BillingApiResult.DUPLICATED -> {
                        billingApiClient.requestUpdate()
                        Snackbar.make(
                            binding.root,
                            R.string.payment_message_error_duplicated,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    BillingApiClient.BillingApiResult.CANCELLED -> {
                        Snackbar.make(
                            binding.root,
                            R.string.payment_message_error_canceled,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    BillingApiClient.BillingApiResult.FAILURE -> {
                        Snackbar.make(
                            binding.root,
                            R.string.payment_message_error_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pay.setOnClickListener {
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    billingApiClient.startBilling(
                        requireActivity() as AppCompatActivity,
                        emptyList()
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        billingApiClient.requestUpdate()
    }
}