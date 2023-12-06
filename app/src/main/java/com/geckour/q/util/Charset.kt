package com.geckour.q.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

fun String.convert(fromEncoding: Charset, toEncoding: Charset): String {
    return toByteArray(fromEncoding).toString(toEncoding)
}

fun String.detectCharset(charsets: List<Charset>): Charset {
    val probe: Charset = StandardCharsets.UTF_8
    charsets.forEach {
        if (this == this.convert(it, probe).convert(probe, it)) return it
    }
    return StandardCharsets.UTF_8
}