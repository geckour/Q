package com.geckour.q.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class BillingApiClient(
    context: Context,
    private val onError: () -> Unit,
    private val onDonateCompleted: (
        result: BillingApiResult,
        billingApiClient: BillingApiClient
    ) -> Unit
) {

    companion object {

        private const val SKU_DONATE = "donate"
    }

    enum class BillingApiResult {
        SUCCESS,
        DUPLICATED,
        CANCELLED,
        FAILURE
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val result = when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) BillingApiResult.FAILURE
                else {
                    Timber.d("qgeck purchases: $purchases")
                    purchases.forEach {
                        if (it.products.contains(SKU_DONATE).not()) return@forEach
                        it.consume()
                    }
                    null
                }
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
        result?.let { onDonateCompleted(it, this) }
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
            val (billingResult, productDetailsList) = client.queryProductDetails(params)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onError()
                return
            }
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().apply {
                        productDetailsList?.firstOrNull()?.productType?.let {
                            setProductType(it)
                        }
                    }.build()
                ) { br, purchases ->
                    if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                        if (purchases.isEmpty()) {
                            productDetailsList?.firstOrNull()?.let {
                                val productDetailParamsList = listOf(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(it)
                                        .build()
                                )
                                val billingFlowParams = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(productDetailParamsList)
                                    .build()
                                val result = client.launchBillingFlow(activity, billingFlowParams)
                                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                                    onError()
                                    return@queryPurchasesAsync
                                }
                            } ?: run { onError() }
                        } else {
                            purchases.forEach {
                                if (it.products.contains(SKU_DONATE).not()) return@forEach
                                it.consume()
                            }
                        }
                    }
                }
            }
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

    private fun Purchase.consume() {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
        ) {
            Timber.d("qgeck ack response code: ${it.responseCode}, message: ${it.debugMessage}")
            if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                client.consumeAsync(
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build()
                ) { billingResult, _ ->
                    Timber.d("qgeck consume response code: ${billingResult.responseCode}, message: ${billingResult.debugMessage}")
                    val result = when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> BillingApiResult.SUCCESS

                        else -> BillingApiResult.FAILURE
                    }
                    onDonateCompleted(result, this@BillingApiClient)
                }
            } else onDonateCompleted(BillingApiResult.FAILURE, this@BillingApiClient)
        }
    }
}