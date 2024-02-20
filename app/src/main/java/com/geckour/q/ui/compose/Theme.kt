package com.geckour.q.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
    colorTextSecondary = ColorTextSecondary,
    colorInactive = ColorInactive,
    colorCoverInactive = ColorCoverInactive,
    colorButtonNormal = ColorPrimary,
    colorTextSettingNormal = ColorPrimary,
    colorBackgroundBottomSheet = ColorBackgroundBottomSheet,
    colorBackgroundProgress = ColorBackgroundProgress,
    colorBackgroundSearch = ColorBackgroundSearch,
    isLight = true
)

private val darkColorPalette = QColors(
    colorPrimary = ColorPrimaryInverse,
    colorPrimaryDark = ColorPrimaryDarkInverse,
    colorAccent = ColorAccentInverse,
    colorWeekAccent = ColorWeakAccentInverse,
    colorBackground = ColorBackgroundInverse,
    colorTextPrimary = ColorTextPrimaryInverse,
    colorTextSecondary = ColorTextSecondaryInverse,
    colorInactive = ColorInactiveInverse,
    colorCoverInactive = ColorCoverInactiveInverse,
    colorButtonNormal = ColorStrong,
    colorTextSettingNormal = ColorTextStrong,
    colorBackgroundBottomSheet = ColorBackgroundBottomSheetInverse,
    colorBackgroundProgress = ColorBackgroundProgressInverse,
    colorBackgroundSearch = ColorBackgroundSearchInverse,
    isLight = false
)

@Immutable
data class QColors(
    val colorPrimary: Color,
    val colorPrimaryDark: Color,
    val colorAccent: Color,
    val colorWeekAccent: Color,
    val colorBackground: Color,
    val colorTextPrimary: Color,
    val colorTextSecondary: Color,
    val colorInactive: Color,
    val colorCoverInactive: Color,
    val colorButtonNormal: Color,
    val colorTextSettingNormal: Color,
    val colorBackgroundBottomSheet: Color,
    val colorBackgroundProgress: Color,
    val colorBackgroundSearch: Color,
    val isLight: Boolean,
) {

    val asMaterialColorScheme = ColorScheme(
        primary = colorPrimary,
        onPrimary = colorTextPrimary,
        primaryContainer = colorPrimaryDark,
        onPrimaryContainer = colorTextPrimary,
        inversePrimary = colorPrimaryDark,
        secondary = colorPrimary,
        onSecondary = colorTextPrimary,
        secondaryContainer = colorPrimaryDark,
        onSecondaryContainer = colorTextPrimary,
        tertiary = colorPrimary,
        onTertiary = colorTextPrimary,
        tertiaryContainer = colorPrimaryDark,
        onTertiaryContainer = colorTextPrimary,
        background = colorBackground,
        onBackground = colorTextPrimary,
        surface = colorBackground,
        onSurface = colorTextPrimary,
        surfaceVariant = colorBackground,
        onSurfaceVariant = colorTextPrimary,
        surfaceTint = colorBackground,
        inverseSurface = colorTextPrimary,
        inverseOnSurface = colorBackground,
        error = colorAccent,
        onError = colorTextPrimary,
        errorContainer = colorBackground,
        onErrorContainer = colorAccent,
        outline = colorTextSecondary,
        outlineVariant = colorWeekAccent,
        scrim = colorCoverInactive,
    )
}

val LocalQColors = staticCompositionLocalOf {
    QColors(
        colorPrimary = Color.Unspecified,
        colorPrimaryDark = Color.Unspecified,
        colorAccent = Color.Unspecified,
        colorWeekAccent = Color.Unspecified,
        colorBackground = Color.Unspecified,
        colorTextPrimary = Color.Unspecified,
        colorTextSecondary = Color.Unspecified,
        colorInactive = Color.Unspecified,
        colorCoverInactive = Color.Unspecified,
        colorButtonNormal = Color.Unspecified,
        colorTextSettingNormal = Color.Unspecified,
        colorBackgroundBottomSheet = Color.Unspecified,
        colorBackgroundProgress = Color.Unspecified,
        colorBackgroundSearch = Color.Unspecified,
        isLight = true
    )
}

@Composable
fun QTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val qColors = if (darkTheme) {
        darkColorPalette
    } else {
        lightColorPalette
    }

    CompositionLocalProvider(LocalQColors provides qColors) {
        MaterialTheme(
            colorScheme = qColors.asMaterialColorScheme,
            content = content
        )
    }
}

object QTheme {
    val colors: QColors
        @Composable
        get() = LocalQColors.current
}