package com.geckour.q.util

import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import kotlin.math.abs

fun Float.getReadableString(digitToKeep: Int = 3): String {
    val sign = if (this < 0) -1 else 1
    var absReturnValue = abs(this)
    var count = 0

    return if (absReturnValue < 1) {
        val suffixList: List<String> = listOf("", "m", "μ", "n", "p", "f", "a", "z", "y")
        while (absReturnValue < 1) {
            absReturnValue *= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        (absReturnValue * sign).format(suffix, digitToKeep)
    } else {
        val suffixList: List<String> = listOf("", "k", "M", "G", "T", "P", "E", "Z", "Y")
        while (absReturnValue >= 1000) {
            absReturnValue /= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        (absReturnValue * sign).format(suffix, digitToKeep)
    }
}

private fun Float.format(suffix: String, digitToKeep: Int): String =
    String.format("%.${digitToKeep}f", this)
        .replace(Regex("^(.+)\\.0+$"), "$1") + suffix

val Boolean.toNightModeInt: Int
    get() = if (this) AppCompatDelegate.MODE_NIGHT_YES
    else AppCompatDelegate.MODE_NIGHT_NO

val String.hiraganized: String
    get() = this.codePoints()
        .map { if (it in 'ァ'.code..'ヶ'.code) it - 0x60 else it }
        .toArray()
        .let { String(it, 0, it.size) }

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