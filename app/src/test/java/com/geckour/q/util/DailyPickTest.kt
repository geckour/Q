package com.geckour.q.util

import com.google.common.truth.Truth
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random

class DailyPickTest {

    private val library = mapOf(
        "Artist A" to mapOf(
            "Album 1" to listOf("local:a1-1", "spotify:a1-2"),
            "Album 2" to listOf("spotify:a2-1"),
        ),
        "Artist B" to mapOf(
            "Album 3" to listOf("local:b3-1"),
        ),
        "Artist C" to mapOf(
            "Album 4" to listOf("spotify:c4-1"),
        ),
    )

    private fun pick(seed: Int): String? = runBlocking {
        pickByArtistAndAlbum(
            random = Random(seed),
            artistNames = library.keys,
            albumGroupsOf = { artistName ->
                library[artistName].orEmpty().mapValues { (_, tracks) -> suspend { tracks } }
            },
        )
    }

    @Test
    fun `Same seed picks the same track`() {
        repeat(20) { seed ->
            Truth.assertThat(pick(seed)).isEqualTo(pick(seed))
        }
    }

    @Test
    fun `Every track including Spotify-only artists can be picked`() {
        val picked = (0 until 1000).mapNotNull { pick(it) }.toSet()
        Truth.assertThat(picked)
            .containsExactly("local:a1-1", "spotify:a1-2", "spotify:a2-1", "local:b3-1", "spotify:c4-1")
    }

    @Test
    fun `Artists are picked evenly regardless of their track count`() {
        val counts = (0 until 3000).mapNotNull { pick(it) }
            .groupingBy { track -> library.entries.first { (_, albums) -> albums.values.any { track in it } }.key }
            .eachCount()
        counts.values.forEach { Truth.assertThat(it).isIn(800..1200) }
    }

    @Test
    fun `Empty library picks nothing`() {
        val picked = runBlocking {
            pickByArtistAndAlbum<String>(
                random = Random(0),
                artistNames = emptySet(),
                albumGroupsOf = { emptyMap() },
            )
        }
        Truth.assertThat(picked).isNull()
    }
}
