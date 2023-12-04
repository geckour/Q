package com.geckour.q.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BillingApiClient(
    context: Context,
    private val onError: () -> Unit,
    private val onDonateCompleted: (
        result: BillingApiResult,
        billingApiClient: BillingApiClient
    ) -> Unit
) {

    enum class BillingApiResult {
        SUCCESS,
        DUPLICATED,
        CANCELLED,
        FAILURE
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val result = when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases?.isEmpty() == false) {
                    if (purchases.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                        BillingApiResult.SUCCESS
                    } else BillingApiResult.DUPLICATED
                } else BillingApiResult.FAILURE
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                BillingApiResult.CANCELLED
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                BillingApiResult.DUPLICATED
            }

            else -> {
                BillingApiResult.FAILURE
            }
        }
        onDonateCompleted(result, this)
    }

    private val client: BillingClient =
        BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

    init {
        client.startConnection(
            object : BillingClientStateListener {

                override fun onBillingSetupFinished(billingResult: BillingResult) = Unit

                override fun onBillingServiceDisconnected() = Unit
            }
        )
    }

    suspend fun startBilling(activity: Activity, skus: List<String>) {
        runCatching {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    skus.map {
                        Product.newBuilder()
                            .setProductId(it)
                            .setProductType(ProductType.INAPP)
                            .build()
                    }
                ).build()
            val (billingResult, productDetailsList) = withContext(Dispatchers.IO) {
                client.queryProductDetails(params)
            }
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onError()
                return
            }

            productDetailsList?.firstOrNull()?.let {
                val productDetailParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(it)
                        .build()
                )
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailParamsList)
                    .build()
                client.launchBillingFlow(activity, billingFlowParams)
            } ?: run { onError() }
        }.onFailure { onError() }
    }

    fun requestUpdate() {
        runCatching {
            val queryPurchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
                .build()
            client.queryPurchasesAsync(queryPurchasesParams) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach {
                        if (it.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
                        if (it.isAcknowledged) return@forEach

                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(it.purchaseToken)
                            .build()
                        client.acknowledgePurchase(acknowledgePurchaseParams) {}
                    }
                }
            }
        }.onFailure { onError() }
    }
}