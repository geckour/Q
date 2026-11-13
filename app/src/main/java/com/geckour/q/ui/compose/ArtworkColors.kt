package com.geckour.q.ui.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pixels more transparent than this are not part of the artwork's colors. */
private const val MIN_SAMPLE_ALPHA = 0x80

/** Artworks are scaled down to about this many pixels before their colors are counted. */
private const val SAMPLE_AREA = 112 * 112

/** The WCAG contrast ratio that a control needs against its background. */
private const val MIN_ACCENT_CONTRAST = 3f

/** How far [QColors.colorBackground] is moved from the key color, away from the text color. */
private const val BACKGROUND_SHIFT = 0.3f

/** How far [QColors.colorPrimary] is moved from the key color, toward the text color. */
private const val PRIMARY_SHIFT = 0.15f

/** How far [QColors.colorPrimaryStrong] is moved from the key color, toward the text color. */
private const val PRIMARY_STRONG_SHIFT = 0.3f

/** The colors picked from an artwork. [toQColors] derives a whole palette from them. */
@Immutable
data class ArtworkColors(
    /** The most common color of the artwork. */
    val key: Color,
    /** A vivid color of the artwork that stands out against [key], if there is one. */
    val accent: Color?,
) {

    /** Whether [key] is light enough that dark text reads better on it than light text. */
    val isLight: Boolean
        get() = key.contrastAgainst(ColorTextPrimary.copy(alpha = 1f)) >=
                key.contrastAgainst(ColorTextPrimaryInverse.copy(alpha = 1f))
}

/**
 * The colors for a track whose artwork is unavailable. They are fixed rather than taken from the
 * default artwork, whose gray would vanish against a surface of its own color.
 */
internal val DefaultArtworkColors = ArtworkColors(
    key = Color(0xff505050),
    accent = Color(0xffffffff),
)

/**
 * Builds the palette around the key color. The surface is the key color itself. The background is
 * moved away from the text color and the primary colors toward it, so that one text color reads on
 * all of them.
 */
internal fun ArtworkColors.toQColors(): QColors {
    val text = if (isLight) ColorTextPrimary else ColorTextPrimaryInverse
    val opaqueText = text.copy(alpha = 1f)
    val awayFromText = if (isLight) Color.White else Color.Black
    return QColors(
        colorBackground = lerp(key, awayFromText, BACKGROUND_SHIFT),
        colorSurface = key,
        colorPrimary = lerp(key, opaqueText, PRIMARY_SHIFT),
        colorPrimaryStrong = lerp(key, opaqueText, PRIMARY_STRONG_SHIFT),
        colorAccent = accent ?: opaqueText,
        colorTextPrimary = text,
        isLight = isLight,
    )
}

/**
 * Picks the colors of an artwork, ignoring its transparent parts. Returns null when the artwork has
 * no opaque pixels.
 *
 * This scans the pixels, so call it off the main thread.
 */
internal fun Bitmap.extractArtworkColors(): ArtworkColors? {
    val pixels = sampleOpaquePixels()
    if (pixels.isEmpty()) return null
    // Palette counts every pixel it is given and ignores their alpha, so hand it only the opaque
    // ones, lined up in a strip that it must not scale.
    val strip = Bitmap.createBitmap(pixels, pixels.size, 1, Bitmap.Config.ARGB_8888)
    try {
        // The default filter drops near-black, near-white and a band of orange to brown hues, which
        // would hide the most common color of many artworks.
        val palette = Palette.from(strip).clearFilters().resizeBitmapArea(0).generate()
        val key = palette.dominantSwatch?.let { Color(it.rgb) } ?: return null
        val accent = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch,
        )
            .map { Color(it.rgb) }
            .firstOrNull { it.contrastAgainst(key) >= MIN_ACCENT_CONTRAST }
        return ArtworkColors(key = key, accent = accent)
    } finally {
        strip.recycle()
    }
}

/**
 * Picks the colors of an encoded image, such as the artwork embedded in an audio file. Returns null
 * when the image cannot be decoded or has no opaque pixels.
 *
 * This decodes the image, so call it off the main thread.
 */
internal fun ByteArray.extractArtworkColors(): ArtworkColors? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // An embedded artwork can take tens of megabytes at its full size, so it is decoded at the
    // smallest size that still keeps twice the sample's width and height.
    val area = bounds.outWidth.toLong() * bounds.outHeight
    var inSampleSize = 1
    while (area / (4L * inSampleSize * inSampleSize) >= 4L * SAMPLE_AREA) inSampleSize *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        this,
        0,
        size,
        BitmapFactory.Options().also { it.inSampleSize = inSampleSize },
    ) ?: return null
    return try {
        bitmap.extractArtworkColors()
    } finally {
        bitmap.recycle()
    }
}

/** The opaque pixels of a scaled-down copy, made fully opaque. */
private fun Bitmap.sampleOpaquePixels(): IntArray {
    // A hardware bitmap does not allow reading its pixels.
    val readable =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) ?: return IntArray(0)
        else this
    val scale = sqrt(SAMPLE_AREA.toDouble() / (width * height)).coerceAtMost(1.0)
    val sample = Bitmap.createScaledBitmap(
        readable,
        max(1, (width * scale).roundToInt()),
        max(1, (height * scale).roundToInt()),
        true,
    )
    try {
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        return pixels
            .filter { it ushr 24 >= MIN_SAMPLE_ALPHA }
            .map { it or (0xff shl 24) }
            .toIntArray()
    } finally {
        if (sample !== readable) sample.recycle()
        if (readable !== this) readable.recycle()
    }
}

/** The WCAG contrast ratio between two colors. */
private fun Color.contrastAgainst(other: Color): Float {
    val l1 = luminance()
    val l2 = other.luminance()
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
}
