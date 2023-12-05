package com.geckour.q.util

fun Long.getNumberWithUnitPrefix(
    index: Int = 0,
    onFormat: (value: Long, prefix: String) -> String
): String {
    val prefixes = listOf("", "k", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q")

    return if (this < 1000) {
        onFormat(this, prefixes[index])
    } else {
        (this / 1000).getNumberWithUnitPrefix(index + 1, onFormat)
    }
}