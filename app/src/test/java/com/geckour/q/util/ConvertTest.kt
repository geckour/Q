package com.geckour.q.util

import com.google.common.truth.Truth
import org.junit.Test

class ConvertTest {

    @Test
    fun `Test k suffix getReadableString with no specifying digitToKeep`() {
        Truth.assertThat(1000f.getReadableStringWithUnit()).isEqualTo("1k")
    }

    @Test
    fun `Test k suffix getReadableString with decimal and no specifying digitToKeep`() {
        Truth.assertThat(1234f.getReadableStringWithUnit()).isEqualTo("1.234k")
    }

    @Test
    fun `Test M suffix getReadableString with no specifying digitToKeep`() {
        Truth.assertThat(1000000f.getReadableStringWithUnit()).isEqualTo("1M")
    }

    @Test
    fun `Test M suffix getReadableString with decimal and no specifying digitToKeep`() {
        Truth.assertThat(1234567f.getReadableStringWithUnit()).isEqualTo("1.235M")
    }

    @Test
    fun `Test k suffix getReadableString with minus value and no specifying digitToKeep`() {
        Truth.assertThat((-1000).toFloat().getReadableStringWithUnit()).isEqualTo("-1k")
    }

    @Test
    fun `Test k suffix getReadableString with minus value and decimal and no specifying digitToKeep`() {
        Truth.assertThat((-1234).toFloat().getReadableStringWithUnit()).isEqualTo("-1.234k")
    }

    @Test
    fun `Test M suffix getReadableString with minus value and no specifying digitToKeep`() {
        Truth.assertThat((-1000000).toFloat().getReadableStringWithUnit()).isEqualTo("-1M")
    }

    @Test
    fun `Test M suffix getReadableString with minus value and decimal and no specifying digitToKeep`() {
        Truth.assertThat((-1234567).toFloat().getReadableStringWithUnit()).isEqualTo("-1.235M")
    }

    @Test
    fun `Test m suffix getReadableString with no specifying digitToKeep`() {
        Truth.assertThat(0.001f.getReadableStringWithUnit()).isEqualTo("1m")
    }

    @Test
    fun `Test m suffix getReadableString with decimal and no specifying digitToKeep`() {
        Truth.assertThat(0.001234f.getReadableStringWithUnit()).isEqualTo("1.234m")
    }

    @Test
    fun `Test μ suffix getReadableString with no specifying digitToKeep`() {
        Truth.assertThat(0.000001f.getReadableStringWithUnit()).isEqualTo("1μ")
    }

    @Test
    fun `Test μ suffix getReadableString with decimal and no specifying digitToKeep`() {
        Truth.assertThat(0.000001234567f.getReadableStringWithUnit()).isEqualTo("1.235μ")
    }

    @Test
    fun `Test m suffix getReadableString with minus value and no specifying digitToKeep`() {
        Truth.assertThat((-0.001).toFloat().getReadableStringWithUnit()).isEqualTo("-1m")
    }

    @Test
    fun `Test m suffix getReadableString with minus value and decimal and no specifying digitToKeep`() {
        Truth.assertThat((-0.001234).toFloat().getReadableStringWithUnit()).isEqualTo("-1.234m")
    }

    @Test
    fun `Test μ suffix getReadableString with minus value and no specifying digitToKeep`() {
        Truth.assertThat((-0.000001).toFloat().getReadableStringWithUnit()).isEqualTo("-1μ")
    }

    @Test
    fun `Test μ suffix getReadableString with minus value and decimal and no specifying digitToKeep`() {
        Truth.assertThat((-0.000001234567).toFloat().getReadableStringWithUnit()).isEqualTo("-1.235μ")
    }

    @Test
    fun `Test hiraganized with all katakana String`() {
        val target = "キャラメルポップコーンフラッペ"
        Truth.assertThat(target.hiraganized)
            .isEqualTo("きゃらめるぽっぷこーんふらっぺ")
    }

    @Test
    fun `Test hiraganized with partial katakana String`() {
        val target = "私はキャラメルポップコーンフラッペが食べたい。poison"
        Truth.assertThat(target.hiraganized)
            .isEqualTo("私はきゃらめるぽっぷこーんふらっぺが食べたい。poison")
    }

    @Test
    fun `Test hiraganized with none katakana String`() {
        val target = "吾輩は猫である。poison"
        Truth.assertThat(target.hiraganized)
            .isEqualTo("吾輩は猫である。poison")
    }
}