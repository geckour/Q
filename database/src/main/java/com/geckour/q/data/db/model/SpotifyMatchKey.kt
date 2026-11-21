package com.geckour.q.data.db.model

import com.geckour.q.core.util.UNKNOWN
import java.text.Normalizer
import java.util.Locale

/**
 * Makes the key that tells a local track and a Spotify track are the same song: their title, album
 * and artist match once normalized, so that the width, the case, spaces and symbols do not matter.
 * `null` when any of them is unknown, as such tracks cannot be told apart.
 */
fun spotifyMatchKeyOf(title: String?, albumTitle: String?, artistName: String?): String? {
    val parts = listOf(title, albumTitle, artistName).map {
        if (it == null || it == UNKNOWN) return null
        it.normalizedForMatch().ifEmpty { return null }
    }

    // The parts keep letters and digits only, so the separator never appears in them.
    return parts.joinToString("/")
}

private fun String.normalizedForMatch(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    return buildString {
        normalized.codePoints().forEach { if (Character.isLetterOrDigit(it)) appendCodePoint(it) }
    }
}
