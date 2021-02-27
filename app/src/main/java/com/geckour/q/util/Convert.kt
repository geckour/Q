package com.geckour.q.util

import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import kotlin.math.abs

fun Float.getReadableString(digitToKeep: Int = 3): String {
    fun Float.format(suffix: String): String = String.format(
        "${if (this@getReadableString < 0) "-" else ""}%.${digitToKeep}f", this
    ).replace(Regex("^(.+\\..*?)0+$"), "$1").replace(Regex("^(.+)\\.$"), "$1") + suffix

    var returnValue = abs(this)
    var count = 0

    return if (returnValue < 1) {
        val suffixList: List<String> = listOf("", "m", "μ", "n", "p", "f", "a", "z", "y")
        while (returnValue < 1) {
            returnValue *= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        returnValue.format(suffix)
    } else {
        val suffixList: List<String> = listOf("", "k", "M", "G", "T", "P", "E", "Z", "Y")
        while (returnValue >= 1000) {
            returnValue /= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        returnValue.format(suffix)
    }
}

val Boolean.toNightModeInt: Int
    get() = if (this) AppCompatDelegate.MODE_NIGHT_YES
    else AppCompatDelegate.MODE_NIGHT_NO

val String.hiraganized: String
    get() = this.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")

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