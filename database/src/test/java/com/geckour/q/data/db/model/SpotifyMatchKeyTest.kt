package com.geckour.q.data.db.model

import com.google.common.truth.Truth
import org.junit.Test

class SpotifyMatchKeyTest {

    @Test
    fun `Test the width and the case do not matter`() {
        Truth.assertThat(spotifyMatchKeyOf("ＬＯＶＥ ｿﾝｸﾞ", "Ａｌｂｕｍ", "ARTIST"))
            .isEqualTo(spotifyMatchKeyOf("love ソング", "album", "artist"))
    }

    @Test
    fun `Test spaces and symbols do not matter`() {
        Truth.assertThat(spotifyMatchKeyOf("Song!", "Album ~Deluxe~", "A, B"))
            .isEqualTo(spotifyMatchKeyOf("Song", "Album 〜Deluxe〜", "A & B"))
    }

    @Test
    fun `Test digits tell songs apart`() {
        Truth.assertThat(spotifyMatchKeyOf("Part 1", "Album", "Artist"))
            .isNotEqualTo(spotifyMatchKeyOf("Part 2", "Album", "Artist"))
    }

    @Test
    fun `Test letters of any script are kept`() {
        Truth.assertThat(spotifyMatchKeyOf("エメラルドグリーンの揺らめき", "アルバム", "Mime"))
            .isEqualTo("エメラルドグリーンの揺らめき/アルバム/mime")
    }

    @Test
    fun `Test an unknown part makes no key`() {
        Truth.assertThat(spotifyMatchKeyOf("Song", "UNKNOWN", "Artist")).isNull()
        Truth.assertThat(spotifyMatchKeyOf("Song", null, "Artist")).isNull()
    }

    @Test
    fun `Test a part without letters or digits makes no key`() {
        Truth.assertThat(spotifyMatchKeyOf("!!!", "Album", "Artist")).isNull()
    }
}
