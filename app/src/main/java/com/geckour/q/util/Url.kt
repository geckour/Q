package com.geckour.q.util

import java.net.URLDecoder
import java.net.URLEncoder

fun String.encodeUrlSafe(): String = URLEncoder.encode(this, "UTF-8")

fun String.decodeUrlSafe(): String = URLDecoder.decode(this, "UTF-8")