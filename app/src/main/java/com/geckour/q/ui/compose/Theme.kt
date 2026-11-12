package com.geckour.q.ui.compose

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
    colorTextSettingNormal = ColorTextStrong,
    colorBackgroundSelected = ColorBackgroundSelected,
    colorBackgroundBottomSheet = ColorBackgroundBottomSheet,
    colorBackgroundProgress = ColorBackgroundProgress,
    colorBackgroundSearch = ColorBackgroundSearch,
    colorDivider = ColorDivider,
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
    colorTextSettingNormal = ColorTextStrongInverse,
    colorBackgroundSelected = ColorBackgroundSelectedInverse,
    colorBackgroundBottomSheet = ColorBackgroundBottomSheetInverse,
    colorBackgroundProgress = ColorBackgroundProgressInverse,
    colorBackgroundSearch = ColorBackgroundSearchInverse,
    colorDivider = ColorDividerInverse,
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
    val colorBackgroundSelected: Color,
    val colorBackgroundBottomSheet: Color,
    val colorBackgroundProgress: Color,
    val colorBackgroundSearch: Color,
    val colorDivider: Color,
    val isLight: Boolean,
) {

    /** The scrim behind a modal drawer, matching Material3's `DrawerDefaults.scrimColor`. */
    val colorDrawerScrim: Color
        get() = colorCoverInactive.copy(alpha = 0.32f)

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
        surfaceBright = colorBackground,
        surfaceDim = colorBackground,
        surfaceContainer = colorBackground,
        surfaceContainerHigh = colorBackgroundBottomSheet,
        surfaceContainerHighest = colorBackgroundBottomSheet,
        surfaceContainerLow = colorBackground,
        surfaceContainerLowest = colorBackground,
        primaryFixed = colorPrimary,
        primaryFixedDim = colorPrimaryDark,
        onPrimaryFixed = colorTextPrimary,
        onPrimaryFixedVariant = colorTextSecondary,
        secondaryFixed = colorPrimary,
        secondaryFixedDim = colorPrimaryDark,
        onSecondaryFixed = colorTextPrimary,
        onSecondaryFixedVariant = colorTextSecondary,
        tertiaryFixed = colorPrimary,
        tertiaryFixedDim = colorPrimaryDark,
        onTertiaryFixed = colorTextPrimary,
        onTertiaryFixedVariant = colorTextSecondary,
    )
}

private val LocalQColors = staticCompositionLocalOf<State<QColors>> {
    mutableStateOf(lightColorPalette)
}

@Composable
fun QTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val target = if (darkTheme) darkColorPalette else lightColorPalette
    val qColors = rememberTransitioningQColors(target)

    CompositionLocalProvider(LocalQColors provides qColors) {
        // The Material theme switches in one step. Material3 components that rely on its default
        // colors follow the transition only when wrapped with [ProvideQMaterialColors].
        MaterialTheme(
            colorScheme = target.asMaterialColorScheme,
            content = content
        )
    }
}

object QTheme {
    /**
     * The current palette. While it is transitioning, only the composables that read it are
     * recomposed on each frame.
     */
    val colors: QColors
        @Composable
        @ReadOnlyComposable
        get() = LocalQColors.current.value
}

/**
 * Re-provides the Material theme with the current, possibly mid-transition, palette so that the
 * Material3 components inside follow the transition with their default colors. Everything inside is
 * recomposed on each frame of a transition, so wrap only small components with it and give
 * containers explicit colors from [QTheme.colors] instead.
 */
@Composable
fun ProvideQMaterialColors(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QTheme.colors.asMaterialColorScheme, content = content)
}

/**
 * The background of a whole screen. It provides the content color that text and icons without an
 * explicit color fall back to, and reads the palette in its own scope so that a transition does not
 * recompose the caller.
 */
@Composable
fun QBackgroundSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = QTheme.colors.colorBackground,
        contentColor = QTheme.colors.colorTextPrimary,
        content = content,
    )
}

private const val COLOR_TRANSITION_DURATION_MILLIS = 500

/**
 * Holds the palette in a single state and fades it toward [target] whenever [target] changes. The
 * state object itself is never replaced, so a transition does not invalidate the whole content the
 * way providing a new value through [LocalQColors] would. [QColors.isLight] is not a color and flips
 * on the first frame of the transition.
 */
@Composable
private fun rememberTransitioningQColors(target: QColors): State<QColors> {
    val colors = remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val from = colors.value
        if (from == target) return@LaunchedEffect
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = COLOR_TRANSITION_DURATION_MILLIS),
        ) { fraction, _ ->
            colors.value = from.lerpTo(target, fraction)
        }
        // Interpolation can round off the last frame; land exactly on the palette so that Material's
        // contentColorFor() recognizes its colors again.
        colors.value = target
    }
    return colors
}

private fun QColors.lerpTo(target: QColors, fraction: Float) = QColors(
    colorPrimary = lerp(colorPrimary, target.colorPrimary, fraction),
    colorPrimaryDark = lerp(colorPrimaryDark, target.colorPrimaryDark, fraction),
    colorAccent = lerp(colorAccent, target.colorAccent, fraction),
    colorWeekAccent = lerp(colorWeekAccent, target.colorWeekAccent, fraction),
    colorBackground = lerp(colorBackground, target.colorBackground, fraction),
    colorTextPrimary = lerp(colorTextPrimary, target.colorTextPrimary, fraction),
    colorTextSecondary = lerp(colorTextSecondary, target.colorTextSecondary, fraction),
    colorInactive = lerp(colorInactive, target.colorInactive, fraction),
    colorCoverInactive = lerp(colorCoverInactive, target.colorCoverInactive, fraction),
    colorButtonNormal = lerp(colorButtonNormal, target.colorButtonNormal, fraction),
    colorTextSettingNormal = lerp(colorTextSettingNormal, target.colorTextSettingNormal, fraction),
    colorBackgroundSelected = lerp(colorBackgroundSelected, target.colorBackgroundSelected, fraction),
    colorBackgroundBottomSheet = lerp(colorBackgroundBottomSheet, target.colorBackgroundBottomSheet, fraction),
    colorBackgroundProgress = lerp(colorBackgroundProgress, target.colorBackgroundProgress, fraction),
    colorBackgroundSearch = lerp(colorBackgroundSearch, target.colorBackgroundSearch, fraction),
    colorDivider = lerp(colorDivider, target.colorDivider, fraction),
    isLight = target.isLight,
)