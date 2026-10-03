package com.geckour.q.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.min

private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private val PredictiveBackMaxScaleXDistance = 48.dp

private val PredictiveBackMaxScaleYDistance = 24.dp

@Composable
fun PredictiveBackProgressHandler(
    enabled: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { backEvents ->
        try {
            backEvents.collect { progress.snapTo(PredictiveBackEasing.transform(it.progress)) }
            currentOnBack()
            coroutineScope.launch { progress.animateTo(0f) }
        } catch (e: CancellationException) {
            coroutineScope.launch { progress.animateTo(0f) }
            throw e
        }
    }
}

fun Modifier.predictiveBackScale(
    progress: () -> Float,
    transformOrigin: TransformOrigin,
): Modifier = graphicsLayer {
    val fraction = progress()
    if (fraction == 0f || size.width == 0f || size.height == 0f) return@graphicsLayer

    scaleX = 1f - lerp(0f, min(PredictiveBackMaxScaleXDistance.toPx(), size.width), fraction) /
            size.width
    scaleY = 1f - lerp(0f, min(PredictiveBackMaxScaleYDistance.toPx(), size.height), fraction) /
            size.height
    this.transformOrigin = transformOrigin
}
