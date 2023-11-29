package com.geckour.q.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val lightColorPalette = QColors(
    colorPrimary = ColorPrimary,
    colorPrimaryDark = ColorPrimaryDark,
    colorAccent = ColorAccent,
    colorWeekAccent = ColorWeakAccent,
    colorBackground = ColorBackground,
    colorTextPrimary = ColorTextPrimary,
    colorInactive = ColorInactive,
    colorCoverInactive = ColorCoverInactive,
    colorButtonNormal = ColorPrimary,
    colorTextSettingNormal = ColorPrimary,
    colorBackgroundBottomSheet = ColorBackgroundBottomSheet,
)

private val darkColorPalette = QColors(
    colorPrimary = ColorPrimaryInverse,
    colorPrimaryDark = ColorPrimaryDarkInverse,
    colorAccent = ColorAccentInverse,
    colorWeekAccent = ColorWeakAccentInverse,
    colorBackground = ColorBackgroundInverse,
    colorTextPrimary = ColorTextPrimaryInverse,
    colorInactive = ColorInactiveInverse,
    colorCoverInactive = ColorCoverInactiveInverse,
    colorButtonNormal = ColorStrong,
    colorTextSettingNormal = ColorTextStrong,
    colorBackgroundBottomSheet = ColorBackgroundBottomSheetInverse,
)

@Immutable
data class QColors(
    val colorPrimary: Color,
    val colorPrimaryDark: Color,
    val colorAccent: Color,
    val colorWeekAccent: Color,
    val colorBackground: Color,
    val colorTextPrimary: Color,
    val colorInactive: Color,
    val colorCoverInactive: Color,
    val colorButtonNormal: Color,
    val colorTextSettingNormal: Color,
    val colorBackgroundBottomSheet: Color,
)

val LocalQColors = staticCompositionLocalOf {
    QColors(
        colorPrimary = Color.Unspecified,
        colorPrimaryDark = Color.Unspecified,
        colorAccent = Color.Unspecified,
        colorWeekAccent = Color.Unspecified,
        colorBackground = Color.Unspecified,
        colorTextPrimary = Color.Unspecified,
        colorInactive = Color.Unspecified,
        colorCoverInactive = Color.Unspecified,
        colorButtonNormal = Color.Unspecified,
        colorTextSettingNormal = Color.Unspecified,
        colorBackgroundBottomSheet = Color.Unspecified,
    )
}

@Composable
fun QTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val qColors = if (darkTheme) {
        darkColorPalette
    } else {
        lightColorPalette
    }

    CompositionLocalProvider(LocalQColors provides qColors) {
        MaterialTheme(
            content = content
        )
    }
}

object QTheme {
    val colors: QColors
        @Composable
        get() = LocalQColors.current
}