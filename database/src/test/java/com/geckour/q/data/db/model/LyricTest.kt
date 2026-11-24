package com.geckour.q.data.db.model

import com.google.common.truth.Truth
import org.junit.Test

class LyricTest {

    @Test
    fun `Test shiftedBy with positive delta`() {
        Truth.assertThat(listOf(LyricLine(1000, "a"), LyricLine(2500, "b")).shiftedBy(500))
            .containsExactly(LyricLine(1500, "a"), LyricLine(3000, "b"))
            .inOrder()
    }

    @Test
    fun `Test shiftedBy with negative delta`() {
        Truth.assertThat(listOf(LyricLine(1000, "a"), LyricLine(2500, "b")).shiftedBy(-500))
            .containsExactly(LyricLine(500, "a"), LyricLine(2000, "b"))
            .inOrder()
    }

    @Test
    fun `Test shiftedBy keeps zero timing lines`() {
        Truth.assertThat(listOf(LyricLine(0, "title"), LyricLine(1000, "a")).shiftedBy(500))
            .containsExactly(LyricLine(0, "title"), LyricLine(1500, "a"))
            .inOrder()
    }

    @Test
    fun `Test shiftedBy keeps timing at least 1`() {
        Truth.assertThat(listOf(LyricLine(1000, "a"), LyricLine(5000, "b")).shiftedBy(-3000))
            .containsExactly(LyricLine(1, "a"), LyricLine(2000, "b"))
            .inOrder()
    }

    @Test
    fun `Test shiftedBy keeps original order of lines clamped to 1`() {
        Truth.assertThat(
            listOf(LyricLine(800, "b"), LyricLine(0, "title"), LyricLine(500, "a"), LyricLine(3000, "c"))
                .shiftedBy(-1000)
        )
            .containsExactly(LyricLine(0, "title"), LyricLine(1, "a"), LyricLine(1, "b"), LyricLine(2000, "c"))
            .inOrder()
    }

    @Test
    fun `Test toSearchText ignores the width, the case, the kana, spaces and symbols`() {
        Truth.assertThat(listOf(LyricLine(0, "ＬＯＶＥ ｿﾝｸﾞ、 Don't  Stop!")).toSearchText())
            .isEqualTo("loveそんぐdontstop")
        Truth.assertThat(listOf(LyricLine(0, "ヴァイオリン〜ラララ")).toSearchText())
            .isEqualTo("ゔぁいおりんららら")
    }

    @Test
    fun `Test toSearchText keeps the long vowel mark and kanji`() {
        Truth.assertThat(listOf(LyricLine(0, "君とメーデー")).toSearchText())
            .isEqualTo("君とめーでー")
    }

    @Test
    fun `Test toSearchText puts the lines apart`() {
        Truth.assertThat(listOf(LyricLine(1000, "ab"), LyricLine(2000, "cd")).toSearchText())
            .isEqualTo("ab\ncd")
    }
}
