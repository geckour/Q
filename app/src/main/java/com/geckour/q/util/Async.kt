package com.geckour.q.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import timber.log.Timber
import kotlin.coroutines.experimental.CoroutineContext

fun <T> asyncOrNull(parentJob: Job, coroutineContext: CoroutineContext = CommonPool,
                    onError: (Throwable) -> Unit = { Timber.e(it) },
                    block: suspend CoroutineScope.() -> T) =
        async(parentJob, coroutineContext) {
            try {
                block()
            } catch (t: Throwable) {
                onError(t)
                null
            }
        }

fun <T> async(parentJob: Job, coroutineContext: CoroutineContext = CommonPool,
              block: suspend CoroutineScope.() -> T) =
        kotlinx.coroutines.experimental.async(coroutineContext,
                parent = parentJob, block = block)


fun ui(parentJob: Job,
       onError: (Throwable) -> Unit = { Timber.e(it) },
       block: suspend CoroutineScope.() -> Unit) = launch(parentJob, UI, onError, block)

fun launch(parentJob: Job, coroutineContext: CoroutineContext = CommonPool,
           onError: (Throwable) -> Unit = { Timber.e(it) },
           block: suspend CoroutineScope.() -> Unit) =
        kotlinx.coroutines.experimental.launch(coroutineContext, parent = parentJob) {
            try {
                block()
            } catch (t: Throwable) {
                onError(t)
            }
        }