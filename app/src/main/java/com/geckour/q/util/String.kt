package com.geckour.q.util

fun String.getExtension(): String = replace(Regex("^.+\\.(.+?)$"), "$1")