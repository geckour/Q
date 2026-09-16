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
    fun `Test parseLrc with positive offset tag`() {
        Truth.assertThat("[offset:+500]\n[00:01.00]a\n[00:02.50]b".parseLrc())
            .containsExactly(LyricLine(500, "a"), LyricLine(2000, "b"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc with negative offset tag`() {
        Truth.assertThat("[offset:-500]\n[00:01.00]a\n[00:02.50]b".parseLrc())
            .containsExactly(LyricLine(1500, "a"), LyricLine(3000, "b"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc with unsigned offset tag and spaces`() {
        Truth.assertThat("[ti:title]\n [OFFSET: 250 ] \n[00:01.00]a".parseLrc())
            .containsExactly(LyricLine(750, "a"))
    }

    @Test
    fun `Test parseLrc keeps timing at least 1 when offset exceeds timing`() {
        Truth.assertThat("[offset:3000]\n[00:01.00]a\n[00:05.00]b".parseLrc())
            .containsExactly(LyricLine(1, "a"), LyricLine(2000, "b"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc does not shift zero timing lines`() {
        Truth.assertThat("[offset:-500]\n[00:00.00]title\n[00:01.00]a".parseLrc())
            .containsExactly(LyricLine(0, "title"), LyricLine(1500, "a"))
            .inOrder()
    }

    @Test
    fun `Test parseLrc ignores malformed offset tag`() {
        Truth.assertThat("[offset:abc]\n[00:01.00]a".parseLrc())
            .containsExactly(LyricLine(1000, "a"))
    }
}
