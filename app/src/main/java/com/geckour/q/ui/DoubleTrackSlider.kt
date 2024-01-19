package com.geckour.q.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DoubleTrackSlider(
    modifier: Modifier = Modifier,
    progressFraction: Float,
    subProgressFraction: Float? = null,
    thickness: Dp = 4.dp,
    thumbRadius: Dp = 6.dp,
    thumbElevation: Dp = 4.dp,
    baseTrackColor: Color = MaterialTheme.colors.surface,
    activeTrackColor: Color = MaterialTheme.colors.primary,
    subTrackColor: Color = MaterialTheme.colors.secondary,
    thumbColor: Color = activeTrackColor,
    onSeek: ((newProgressFraction: Float) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(0.dp) }
    var offsetX by remember { mutableStateOf(0.dp) }
    LaunchedEffect(progressFraction) {
        offsetX = (trackWidth - thickness) * progressFraction
    }

    Box(
        modifier = modifier,
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
        if (subProgressFraction != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = thumbRadius - thickness / 2)
                    .width(trackWidth * subProgressFraction.coerceAtMost(1f))
                    .height(thickness)
                    .background(
                        color = subTrackColor,
                        shape = RoundedCornerShape(thickness / 2)
                    )
            )
        }
        Box(
            modifier = Modifier
                .padding(horizontal = thumbRadius - thickness / 2)
                .width(trackWidth * progressFraction.coerceAtMost(1f))
                .height(thickness)
                .background(
                    color = activeTrackColor,
                    shape = RoundedCornerShape(thickness / 2)
                )
        )
        Box(
            modifier = Modifier
                .size(thumbRadius * 2)
                .offset(x = offsetX)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX += with(density) { delta.toDp() }
                        if (offsetX < 0.dp) offsetX = 0.dp
                        if (offsetX > trackWidth - thickness) offsetX = trackWidth - thickness
                        onSeek?.invoke(offsetX / trackWidth)
                    }
                )
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
        subProgressFraction = 0.3f,
    )
}