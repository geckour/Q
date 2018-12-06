package com.geckour.q.util

fun String?.containsCaseInsensitive(other: String?): Boolean =
        if (this == null && other == null) {
            true
        } else if (this != null && other != null) {
            toLowerCase().contains(other.toLowerCase())
        } else {
            false
        }