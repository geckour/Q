package com.geckour.q.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs

@Composable
fun DoubleTrackSlider(
    modifier: Modifier = Modifier,
    key: Any? = null,
    primaryProgressFraction: Float,
    secondaryProgressFraction: Float? = null,
    steps: Int = 0,
    thickness: Dp = 4.dp,
    thumbRadius: Dp = 6.dp,
    thumbElevation: Dp = 1.dp,
    primaryTrackColor: Color = MaterialTheme.colors.primary,
    secondaryTrackColor: Color = primaryTrackColor.copy(alpha = 0.48f),
    baseTrackColor: Color = primaryTrackColor.copy(alpha = 0.24f),
    thumbColor: Color = primaryTrackColor,
    onSeek: ((newProgressFraction: Float) -> Unit)? = null,
    onSeekEnded: ((newProgressFraction: Float) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(0.dp) }
    var innerProgressFraction by remember(primaryProgressFraction) {
        mutableFloatStateOf(primaryProgressFraction)
    }
    val tickFractions = remember(steps) {
        stepsToTickFractions(steps)
    }

    Box(
        modifier = modifier
            .pointerInput(key ?: Unit) {
                detectTapGestures {
                    val newValue = it.x
                        .coerceIn(0f..(trackWidth - thickness).toPx())
                        .toDp() / (trackWidth - thickness)
                    val resolvedValue = if (steps > 0) {
                        tickFractions.minBy { abs(it - newValue) }
                    } else {
                        newValue
                    }
                    innerProgressFraction = resolvedValue
                    onSeek?.invoke(resolvedValue)
                }
            }
            .pointerInput(key ?: Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        val newValue = change.position.x
                            .coerceIn(0f..(trackWidth - thickness).toPx())
                            .toDp() / (trackWidth - thickness)
                        val resolvedValue = if (steps > 0) {
                            tickFractions.minBy { abs(it - newValue) }
                        } else {
                            newValue
                        }
                        innerProgressFraction = resolvedValue
                        onSeek?.invoke(resolvedValue)
                    },
                    onDragEnd = {
                        onSeekEnded?.invoke(innerProgressFraction)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = thumbRadius - thickness / 2)
                .fillMaxWidth()
                .height(thickness)
                .background(
                    color = baseTrackColor,
                    shape = RoundedCornerShape(thickness / 2)
                )
                .onGloballyPositioned {
                    trackWidth = with(density) { it.size.width.toDp() }
                }
        )
        if (secondaryProgressFraction != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = thumbRadius - thickness / 2)
                    .width(trackWidth * secondaryProgressFraction.coerceAtMost(1f))
                    .height(thickness)
                    .background(
                        color = secondaryTrackColor,
                        shape = RoundedCornerShape(thickness / 2)
                    )
            )
        }
        Box(
            modifier = Modifier
                .padding(horizontal = thumbRadius - thickness / 2)
                .width(trackWidth * innerProgressFraction.coerceAtMost(1f))
                .height(thickness)
                .background(
                    color = primaryTrackColor,
                    shape = RoundedCornerShape(thickness / 2)
                )
        )
        Surface(
            modifier = Modifier.offset(x = (trackWidth - thickness) * innerProgressFraction),
            shape = CircleShape,
            color = thumbColor,
            elevation = thumbElevation
        ) {
            Box(modifier = Modifier.size(thumbRadius * 2))
        }
    }
}

private fun stepsToTickFractions(steps: Int): ImmutableList<Float> {
    return if (steps == 0) persistentListOf() else List(steps + 2) { it.toFloat() / (steps + 1) }.toImmutableList()
}

@Composable
@Preview
fun SliderPreview() {
    DoubleTrackSlider(
        primaryProgressFraction = 0.1f,
        secondaryProgressFraction = 0.3f,
    )
}