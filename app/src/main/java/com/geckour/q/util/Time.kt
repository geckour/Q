package com.geckour.q.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun Long.getDateTimeString(): String =
    SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.JAPAN).format(Date(this))

fun Long.getTimeString(withMillis: Boolean = false): String {
    val absoluteValue = abs(this)
    val hour = absoluteValue / 3600000
    val minute = (absoluteValue % 3600000) / 60000
    val second = (absoluteValue % 60000) / 1000
    val secondWithMillis = (absoluteValue % 60000) / 1000.0
    return (if (this < 0) "-" else "") +
            (if (hour > 0) String.format("%d:", hour) else "") +
            (if (withMillis) String.format("%02d:%05.2f", minute, secondWithMillis)
            else String.format("%02d:%02d", minute, second))
}
