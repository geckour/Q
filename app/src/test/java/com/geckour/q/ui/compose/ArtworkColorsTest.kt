package com.geckour.q.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth
import org.junit.Test

class ArtworkColorsTest {

    private val darkBlue = Color(0xff2f4a6b)
    private val paleYellow = Color(0xffeedd99)

    @Test
    fun `A dark key color carries light text`() {
        val colors = ArtworkColors(key = darkBlue, accent = null).toQColors()

        Truth.assertThat(colors.isLight).isFalse()
        Truth.assertThat(colors.colorTextPrimary).isEqualTo(ColorTextPrimaryInverse)
    }

    @Test
    fun `A light key color carries dark text`() {
        val colors = ArtworkColors(key = paleYellow, accent = null).toQColors()

        Truth.assertThat(colors.isLight).isTrue()
        Truth.assertThat(colors.colorTextPrimary).isEqualTo(ColorTextPrimary)
    }

    @Test
    fun `The surface is the key color itself`() {
        val colors = ArtworkColors(key = darkBlue, accent = null).toQColors()

        Truth.assertThat(colors.colorSurface).isEqualTo(darkBlue)
    }

    @Test
    fun `The background moves away from the text and the primary colors toward it`() {
        listOf(darkBlue, paleYellow).forEach { key ->
            val colors = ArtworkColors(key = key, accent = null).toQColors()
            val text = colors.colorTextPrimary.copy(alpha = 1f).luminance()
            fun distanceFromText(color: Color) = kotlin.math.abs(color.luminance() - text)

            Truth.assertThat(distanceFromText(colors.colorBackground))
                .isGreaterThan(distanceFromText(colors.colorSurface))
            Truth.assertThat(distanceFromText(colors.colorPrimary))
                .isLessThan(distanceFromText(colors.colorSurface))
            Truth.assertThat(distanceFromText(colors.colorPrimaryStrong))
                .isLessThan(distanceFromText(colors.colorPrimary))
        }
    }

    @Test
    fun `The accent falls back to the opaque text color`() {
        val colors = ArtworkColors(key = darkBlue, accent = null).toQColors()

        Truth.assertThat(colors.colorAccent).isEqualTo(ColorTextPrimaryInverse.copy(alpha = 1f))
    }

    @Test
    fun `The colors for a missing artwork carry light text on a fixed gray`() {
        val colors = DefaultArtworkColors.toQColors()

        Truth.assertThat(colors.isLight).isFalse()
        Truth.assertThat(colors.colorSurface).isEqualTo(Color(0xff505050))
        Truth.assertThat(colors.colorAccent).isEqualTo(Color(0xffffffff))
    }

    @Test
    fun `The accent of the artwork is used when there is one`() {
        val accent = Color(0xffff6f61)
        val colors = ArtworkColors(key = darkBlue, accent = accent).toQColors()

        Truth.assertThat(colors.colorAccent).isEqualTo(accent)
    }
}
