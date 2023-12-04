package com.geckour.q.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getEqualizerEnabled
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.setEqualizerEnabled
import com.geckour.q.util.setEqualizerParams
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun Equalizer() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val equalizerEnabled by LocalContext.current.getEqualizerEnabled()
        .collectAsState(initial = false)
    val equalizerParams by LocalContext.current.getEqualizerParams()
        .collectAsState(initial = null)

    LaunchedEffect(equalizerEnabled) {
        context.setEqualizerParams(equalizerParams)
    }

    equalizerParams?.let { params ->
        Timber.d("qgeck params: $params")
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = if (equalizerEnabled) R.string.equalizer_switch_enabled else R.string.equalizer_switch_disabled),
                    fontSize = 16.sp,
                    color = QTheme.colors.colorTextPrimary
                )
                Switch(
                    checked = equalizerEnabled,
                    onCheckedChange = { coroutineScope.launch { context.setEqualizerEnabled(it) } }
                )
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(5) {
                        Divider(color = QTheme.colors.colorButtonNormal)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    var levels by remember { mutableStateOf(params.bands.map { it.level }) }
                    LaunchedEffect(params) {
                        levels = params.bands.map { it.level }
                    }
                    params.bands.forEachIndexed { index, band ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Slider(
                                modifier = Modifier
                                    .padding(0.dp)
                                    .graphicsLayer {
                                        rotationZ = 270f
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(
                                            Constraints(
                                                minWidth = constraints.minHeight,
                                                maxWidth = constraints.maxHeight,
                                                minHeight = constraints.minWidth,
                                                maxHeight = constraints.maxHeight,
                                            )
                                        )
                                        layout(placeable.height, placeable.width) {
                                            placeable.place(-placeable.width, 0)
                                        }
                                    }
                                    .width(300.dp),
                                value = levels.getOrNull(index)?.toFloat() ?: 0f,
                                onValueChange = {
                                    levels =
                                        levels.toMutableList().apply { this[index] = it.toInt() }
                                },
                                valueRange = params.levelRange.first.toFloat()..params.levelRange.second.toFloat(),
                                steps = params.levelRange.second - params.levelRange.first,
                                onValueChangeFinished = {
                                    coroutineScope.launch {
                                        equalizerParams?.let { params ->
                                            context.setEqualizerParams(
                                                params.copy(
                                                    bands = params.bands.mapIndexed { index, band ->
                                                        band.copy(
                                                            level = levels.getOrNull(index) ?: 0
                                                        )
                                                    }
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                            Text(
                                text = "${(band.centerFreq / 1000)}kHz",
                                fontSize = 12.sp,
                                color = QTheme.colors.colorTextPrimary
                            )
                        }
                    }
                }
                if (equalizerEnabled.not()) {
                    Box(
                        modifier = Modifier
                            .clickable(enabled = false, onClick = {})
                            .background(color = QTheme.colors.colorCoverInactive)
                            .matchParentSize()
                    )
                }
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        context.setEqualizerParams(
                            params.copy(bands = params.bands.map { it.copy(level = 0) })
                        )
                    }
                },
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(id = R.string.equalizer_flatten),
                    fontSize = 16.sp,
                    color = QTheme.colors.colorTextPrimary
                )
            }
        }
    }
}