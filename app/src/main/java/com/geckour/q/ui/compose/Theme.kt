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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

private val lightColorPalette = QColors(
    colorBackground = ColorBackground,
    colorSurface = ColorSurface,
    colorPrimary = ColorPrimary,
    colorPrimaryStrong = ColorPrimaryDark,
    colorAccent = ColorAccent,
    colorTextPrimary = ColorTextPrimary,
    isLight = true
)

private val darkColorPalette = QColors(
    colorBackground = ColorBackgroundInverse,
    colorSurface = ColorSurfaceInverse,
    colorPrimary = ColorPrimaryInverse,
    colorPrimaryStrong = ColorPrimaryDarkInverse,
    colorAccent = ColorAccentInverse,
    colorTextPrimary = ColorTextPrimaryInverse,
    isLight = false
)

/**
 * The palette: five roles, plus a deeper primary color. The other colors are derived from them, so
 * that a palette built from an artwork stays consistent.
 */
@Immutable
data class QColors(
    /** The ground of the screens, dialogs and the drawer. */
    val colorBackground: Color,
    /** Panes over the background: the player sheet, the search bar and the snackbar. */
    val colorSurface: Color,
    /** Fills that carry the theme, such as the top bar and progress indicators. */
    val colorPrimary: Color,
    /** A deeper [colorPrimary] for list dividers and the tracks of progress indicators. */
    val colorPrimaryStrong: Color,
    /** Highlights: playback controls, confirmations and selected items. */
    val colorAccent: Color,
    /** Text and icons. */
    val colorTextPrimary: Color,
    /** Whether the palette is light, that is, carries dark text. */
    val isLight: Boolean,
) {

    /** Less prominent text. */
    val colorTextSecondary: Color
        get() = colorTextPrimary.copy(alpha = colorTextPrimary.alpha * 0.79f)

    /** Text and controls that are disabled or ignored. */
    val colorInactive: Color
        get() = colorTextPrimary.copy(alpha = colorTextPrimary.alpha * 0.25f)

    /** A veil over content that is unavailable for now. */
    val colorCoverInactive: Color
        get() = colorBackground.copy(alpha = 0.63f)

    /** The scrim behind a modal drawer, matching Material3's `DrawerDefaults.scrimColor`. */
    val colorDrawerScrim: Color
        get() = colorBackground.copy(alpha = 0.32f)

    /** Halfway from [colorSurface] to [colorBackground], for the search results. */
    val colorSurfaceDim: Color
        get() = lerp(colorSurface, colorBackground, 0.5f)

    /** A faint [colorPrimaryStrong] for the selected item and the dividers in the drawer. */
    val colorPrimaryWeak: Color
        get() = colorPrimaryStrong.copy(alpha = 0.5f).compositeOver(colorBackground)

    /** A faint [colorAccent] for the row of the playing track in the queue. */
    val colorAccentWeak: Color
        get() = colorAccent.copy(alpha = 0.4f).compositeOver(colorSurface)

    val asMaterialColorScheme = ColorScheme(
        primary = colorPrimary,
        onPrimary = colorTextPrimary,
        primaryContainer = colorPrimaryStrong,
        onPrimaryContainer = colorTextPrimary,
        inversePrimary = colorPrimaryStrong,
        secondary = colorPrimary,
        onSecondary = colorTextPrimary,
        secondaryContainer = colorPrimaryStrong,
        onSecondaryContainer = colorTextPrimary,
        tertiary = colorPrimary,
        onTertiary = colorTextPrimary,
        tertiaryContainer = colorPrimaryStrong,
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
        outlineVariant = colorAccentWeak,
        scrim = colorCoverInactive,
        surfaceBright = colorBackground,
        surfaceDim = colorBackground,
        surfaceContainer = colorBackground,
        surfaceContainerHigh = colorSurface,
        surfaceContainerHighest = colorSurface,
        surfaceContainerLow = colorBackground,
        surfaceContainerLowest = colorBackground,
        primaryFixed = colorPrimary,
        primaryFixedDim = colorPrimaryStrong,
        onPrimaryFixed = colorTextPrimary,
        onPrimaryFixedVariant = colorTextSecondary,
        secondaryFixed = colorPrimary,
        secondaryFixedDim = colorPrimaryStrong,
        onSecondaryFixed = colorTextPrimary,
        onSecondaryFixedVariant = colorTextSecondary,
        tertiaryFixed = colorPrimary,
        tertiaryFixedDim = colorPrimaryStrong,
        onTertiaryFixed = colorTextPrimary,
        onTertiaryFixedVariant = colorTextSecondary,
    )
}

private val LocalQColors = staticCompositionLocalOf<State<QColors>> {
    mutableStateOf(lightColorPalette)
}

/**
 * @param darkTheme Picks the built-in palette, which is used until [artworkColors] are given.
 * @param artworkColors The colors of the current track's artwork. When given, the palette is built
 * from them regardless of [darkTheme].
 */
@Composable
fun QTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    artworkColors: ArtworkColors? = null,
    content: @Composable () -> Unit
) {
    val basePalette = if (darkTheme) darkColorPalette else lightColorPalette
    // Remembered so that the Material color scheme keeps its identity across recompositions: a new
    // scheme instance would recompose the whole content.
    val target = remember(basePalette, artworkColors) {
        artworkColors?.toQColors() ?: basePalette
    }
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
    colorBackground = lerp(colorBackground, target.colorBackground, fraction),
    colorSurface = lerp(colorSurface, target.colorSurface, fraction),
    colorPrimary = lerp(colorPrimary, target.colorPrimary, fraction),
    colorPrimaryStrong = lerp(colorPrimaryStrong, target.colorPrimaryStrong, fraction),
    colorAccent = lerp(colorAccent, target.colorAccent, fraction),
    colorTextPrimary = lerp(colorTextPrimary, target.colorTextPrimary, fraction),
    isLight = target.isLight,
)
