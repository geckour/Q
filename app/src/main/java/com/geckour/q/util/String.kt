package com.geckour.q.util

const val UNKNOWN: String = "UNKNOWN"

fun String.getExtension(): String = replace(Regex("^.+\\.(.+?)$"), "$1")

val String.escapeSql: String
    get() = replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
