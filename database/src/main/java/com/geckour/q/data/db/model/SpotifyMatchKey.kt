package com.geckour.q.data.db.model

import com.geckour.q.core.util.UNKNOWN
import com.geckour.q.core.util.normalizedForMatch
import com.geckour.q.core.util.withoutStoreAlbumSuffix

/**
 * Makes the key that tells a local track and a Spotify track are the same song: their title, album
 * and artist match once normalized, so that the width, the case, spaces, symbols and the suffixes
 * of the album names such as " - Single" do not matter. `null` when any of them is unknown, as such
 * tracks cannot be told apart.
 */
fun spotifyMatchKeyOf(title: String?, albumTitle: String?, artistName: String?): String? {
    val parts = listOf(title, albumTitle?.withoutStoreAlbumSuffix(), artistName).map {
        if (it == null || it == UNKNOWN) return null
        it.normalizedForMatch().ifEmpty { return null }
    }

    // The parts keep letters and digits only, so the separator never appears in them.
    return parts.joinToString("/")
}
