package com.geckour.q.util

import android.os.Build
import java.net.URLDecoder
import java.net.URLEncoder

fun String.encodeUrlSafe(): String =
    if (Build.VERSION.SDK_INT < 33) URLEncoder.encode(this)
    else URLEncoder.encode(this, "UTF-8")

fun String.decodeUrlSafe(): String =
    if (Build.VERSION.SDK_INT < 33) URLDecoder.decode(this)
    else URLDecoder.decode(this, "UTF-8")