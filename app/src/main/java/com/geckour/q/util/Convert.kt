package com.geckour.q.util

fun Float.getReadableString(): String {
    var returnValue = this
    var count = 0
    return if (returnValue < 1) {
        val suffixList: List<String> = listOf("", "m", "μ", "n", "p", "f", "a", "z", "y")
        while (returnValue < 1) {
            returnValue *= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        String.format("%.3f%s", returnValue, suffix)
    } else {
        val suffixList: List<String> = listOf("", "k", "M", "G", "T", "P", "E", "Z", "Y")
        while (returnValue >= 1000) {
            returnValue /= 1000
            count++
        }
        val suffix = suffixList.getOrNull(count) ?: return this.toString()
        String.format("%.3f%s", returnValue, suffix)
    }
}