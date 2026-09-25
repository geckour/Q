package com.geckour.q.util

import com.geckour.q.data.db.model.LyricLine
import com.google.common.truth.Truth
import org.junit.Test

class LrcTest {

    @Test
    fun `Test parseLrc without offset tag`() {
        Truth.assertThat("[00:01.00]a\n[00:02.50]b".parseLrc())
            .containsExactly(LyricLine(1000, "a"), LyricLine(2500, "b"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc with millisecond timings`() {
        Truth.assertThat("[00:12.345]a\n[01:02.005]b".parseLrc())
            .containsExactly(LyricLine(12345, "a"), LyricLine(62005, "b"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc with decisecond timing`() {
        Truth.assertThat("[00:12.3]a".parseLrc())
            .containsExactly(LyricLine(12300, "a"))
    }

    @Test
    fun `Test parseLrc with centisecond timing starting with zero`() {
        Truth.assertThat("[00:12.05]a".parseLrc())
            .containsExactly(LyricLine(12050, "a"))
    }

    @Test
    fun `Test parseLrc with timing without fraction`() {
        Truth.assertThat("[00:12]a\n[01:02.50]b".parseLrc())
            .containsExactly(LyricLine(12000, "a"), LyricLine(62500, "b"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc with multiple timings in a line`() {
        Truth.assertThat("[00:01.00][00:02][00:03.250]a".parseLrc())
            .containsExactly(LyricLine(1000, "a"), LyricLine(2000, "a"), LyricLine(3250, "a"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc sorts lines by timing`() {
        Truth.assertThat("[00:03.00][00:01.00]a\n[00:02.00]b".parseLrc())
            .containsExactly(LyricLine(1000, "a"), LyricLine(2000, "b"), LyricLine(3000, "a"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc ignores offset tag`() {
        Truth.assertThat("[offset:+500]\n[00:01.00]a\n[00:02.50]b".parseLrc())
            .containsExactly(LyricLine(1000, "a"), LyricLine(2500, "b"))
            .inOrder()
    }
}
