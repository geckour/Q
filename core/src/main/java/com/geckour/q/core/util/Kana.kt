package com.geckour.q.core.util

import kotlin.streams.toList

val String.hiraganized: String
    get() = this.codePoints()
        .map { if (it in 'ァ'.code..'ヶ'.code) it - 0x60 else it }
        .toArray()
        .let { String(it, 0, it.size) }

val String.containsKatakana: Boolean
    get() = this.codePoints().toList()
        .any { it in 'ァ'.code..'ヶ'.code }
