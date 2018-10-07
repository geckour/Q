package com.geckour.q.util

import timber.log.Timber
import kotlin.math.abs

fun Float.getReadableString(digitToKeep: Int = 3): String {
    fun Float.format(suffix: String): String =
            String.format("${if (this@getReadableString < 0) "-" else ""}%.${digitToKeep}f", this).apply { Timber.d("qgeck middle result: $this") }
                    .replace(Regex("^(.+\\..*?)0+$"), "$1")
                    .replace(Regex("^(.+)\\.$"), "$1") + suffix

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