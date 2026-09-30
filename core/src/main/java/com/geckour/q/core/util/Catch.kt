package com.geckour.q.core.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

inline fun <reified T> catchAsNull(
    onError: (Throwable) -> Unit = {},
    block: () -> T
) = runCatching {
    block()
}.onFailure {
    Timber.e(it)
    FirebaseCrashlytics.getInstance().recordException(it)
    onError(it)
}.getOrNull()
