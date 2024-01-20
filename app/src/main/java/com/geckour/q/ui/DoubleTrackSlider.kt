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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DoubleTrackSlider(
    modifier: Modifier = Modifier,
    key: Any? = null,
    progressFraction: Float,
    secondaryProgressFraction: Float? = null,
    thickness: Dp = 4.dp,
    thumbRadius: Dp = 6.dp,
    thumbElevation: Dp = 4.dp,
    activeTrackColor: Color = MaterialTheme.colors.primary,
    baseTrackColor: Color = activeTrackColor.copy(alpha = 0.24f),
    secondaryTrackColor: Color = MaterialTheme.colors.secondary,
    thumbColor: Color = activeTrackColor,
    onSeek: ((newProgressFraction: Float) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(0.dp) }
    var innerProgressFraction by remember { mutableFloatStateOf(progressFraction) }
    LaunchedEffect(progressFraction) {
        innerProgressFraction = progressFraction
    }

    Box(
        modifier = modifier
            .pointerInput(key ?: Unit) {
                detectTapGestures {
                    innerProgressFraction = it.x
                        .coerceIn(0f..(trackWidth - thickness).toPx())
                        .toDp() / (trackWidth - thickness)
                    onSeek?.invoke(innerProgressFraction)
                }
            }
            .pointerInput(key ?: Unit) {
                detectHorizontalDragGestures { change, _ ->
                    innerProgressFraction = change.position.x
                        .coerceIn(0f..(trackWidth - thickness).toPx())
                        .toDp() / (trackWidth - thickness)
                    onSeek?.invoke(innerProgressFraction)
                }
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
                    color = activeTrackColor,
                    shape = RoundedCornerShape(thickness / 2)
                )
        )
        Box(
            modifier = Modifier
                .size(thumbRadius * 2)
                .offset(x = (trackWidth - thickness) * innerProgressFraction)
                .shadow(elevation = thumbElevation)
                .background(
                    color = thumbColor,
                    shape = CircleShape
                )
        )
    }
}

@Composable
@Preview
fun SliderPreview() {
    DoubleTrackSlider(
        progressFraction = 0.1f,
        secondaryProgressFraction = 0.3f,
    )
}