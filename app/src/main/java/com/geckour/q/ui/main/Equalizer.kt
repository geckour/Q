package com.geckour.q.ui.main

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.AudioDeviceEqualizerInfo
import com.geckour.q.data.db.model.EqualizerLevelRatio
import com.geckour.q.data.db.model.EqualizerPreset
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.ui.DoubleTrackSlider
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getEqualizerEnabled
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.setEqualizerEnabled
import com.geckour.q.util.setSelectedEqualizerPresetId
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Equalizer(routeInfo: QAudioDeviceInfo?) {
    val context = LocalContext.current
    val db = DB.getInstance(context)
    val coroutineScope = rememberCoroutineScope()
    val equalizerEnabled by context.getEqualizerEnabled()
        .collectAsState(initial = false)
    val equalizerParams by context.getEqualizerParams().collectAsState(initial = null)
    val equalizerPresets by db.equalizerPresetDao()
        .getEqualizerPresets()
        .collectAsState(initial = null)
    var updateEqualizerPresetJob by remember { mutableStateOf<Job>(Job()) }
    var sliderHeight by remember { mutableIntStateOf(0) }
    var labelHeight by remember { mutableIntStateOf(0) }
    val audioDeviceEqualizerInfo by db.audioDeviceEqualizerInfoDao()
        .get(
            routeId = routeInfo?.routeId ?: "",
            deviceId = routeInfo?.id ?: 0
        ).collectAsState(initial = null)
    val presetsLazyListState by
    remember {
        derivedStateOf {
            val equalizerInfo = audioDeviceEqualizerInfo
            LazyListState(
                firstVisibleItemIndex = if (equalizerInfo?.defaultEqualizerPresetId != null) {
                    equalizerPresets?.entries
                        ?.indexOfFirst { it.key.id == equalizerInfo.defaultEqualizerPresetId }
                        ?: 0
                } else 0
            )
        }
    }
    val firstVisiblePresetIndex by remember {
        derivedStateOf {
            presetsLazyListState.layoutInfo
                .let { layoutInfo ->
                    val completelyVisibleItemsInfo =
                        layoutInfo.visibleItemsInfo.filter {
                            it.offset >= layoutInfo.viewportStartOffset &&
                                    it.offset + it.size <= layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset
                        }
                    when {
                        equalizerPresets?.size == 1 -> 0
                        completelyVisibleItemsInfo.isEmpty() -> layoutInfo.visibleItemsInfo.firstOrNull()?.index
                            ?: -1

                        else -> completelyVisibleItemsInfo[completelyVisibleItemsInfo.size / 2].index
                    }
                }
        }
    }
    val selectedPreset by remember {
        derivedStateOf {
            equalizerPresets?.entries?.toList()?.getOrNull(firstVisiblePresetIndex)
        }
    }

    val params = equalizerParams ?: return
    val presetMap = equalizerPresets ?: return

    LaunchedEffect(audioDeviceEqualizerInfo) {
        Timber.d("qgeck audioDeviceEqualizerInfo: $audioDeviceEqualizerInfo")
    }

    LaunchedEffect(equalizerPresets, equalizerParams) {
        if (presetMap.isEmpty()) {
            db.equalizerPresetDao()
                .addEqualizerPreset(
                    equalizerPreset = EqualizerPreset(
                        id = 0,
                        label = "temporary 1"
                    ),
                    equalizerLevelRatios = params.bands.map {
                        EqualizerLevelRatio(
                            id = 0,
                            presetId = 0,
                            centerFrequency = it.centerFreq,
                            ratio = params.toRatio(it.level)
                        )
                    },
                    overrideLabelById = { id -> "temporary $id" }
                )
        }
    }

    LaunchedEffect(selectedPreset) {
        selectedPreset?.let {
            context.setSelectedEqualizerPresetId(it.key.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            routeInfo?.let {
                val preset = selectedPreset ?: return@let
                val info = audioDeviceEqualizerInfo
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (info?.defaultEqualizerPresetId == null || info.defaultEqualizerPresetId != selectedPreset?.key?.id) {
                                db.audioDeviceEqualizerInfoDao().upsert(
                                    info?.copy(defaultEqualizerPresetId = preset.key.id)
                                        ?: AudioDeviceEqualizerInfo(
                                            id = 0,
                                            routeId = it.routeId,
                                            deviceAddress = it.address,
                                            deviceId = it.id,
                                            defaultEqualizerPresetId = preset.key.id
                                        )
                                )
                            } else {
                                db.audioDeviceEqualizerInfoDao()
                                    .upsert(info.copy(defaultEqualizerPresetId = null))
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(
                            id = if (info?.defaultEqualizerPresetId == null || info.defaultEqualizerPresetId != selectedPreset?.key?.id) {
                                R.string.equalizer_set_as_default_for_the_device
                            } else R.string.equalizer_clear_default_for_the_device,
                            it.name
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
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
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .weight(1f)
                .onGloballyPositioned { sliderHeight = it.size.height }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { (sliderHeight - labelHeight).toDp() })
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(5) {
                    Divider(color = QTheme.colors.colorPrimary)
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val ratios by remember {
                    derivedStateOf {
                        selectedPreset?.value
                            ?.map { it.ratio }
                            ?: List(params.bands.size) { 0.5f }
                    }
                }
                params.bands.forEachIndexed { index, band ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoubleTrackSlider(
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
                                .width(
                                    with(LocalDensity.current) {
                                        (sliderHeight - labelHeight).toDp()
                                    }
                                ),
                            primaryProgressFraction = ratios.getOrNull(index) ?: 0.5f,
                            steps = params.levelRange.second - params.levelRange.first,
                            primaryTrackColor = QTheme.colors.colorButtonNormal,
                            onSeekEnded = { newRatio ->
                                updateEqualizerPresetJob.cancel()
                                updateEqualizerPresetJob = coroutineScope.launch {
                                    val preset = selectedPreset ?: return@launch
                                    preset.value.getOrNull(index)?.let { equalizerLevelRatio ->
                                        db.equalizerPresetDao().upsertEqualizerLevelRatio(
                                            equalizerLevelRatio.copy(ratio = newRatio)
                                        )
                                    }
                                }
                            }
                        )
                        Text(
                            text = "${(band.centerFreq / 1000)}kHz",
                            fontSize = 12.sp,
                            color = QTheme.colors.colorTextPrimary,
                            modifier = Modifier.onGloballyPositioned {
                                labelHeight = it.size.height
                            }
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
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp)) {
                Spacer(modifier = Modifier.size(24.dp))
                androidx.compose.animation.AnimatedVisibility(
                    visible = presetMap.size > 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            selectedPreset?.let {
                                coroutineScope.launch {
                                    db.equalizerPresetDao()
                                        .deleteEqualizerPresetRecursively(it.key)
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Remove from presets"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    val preset = selectedPreset ?: return@Button
                    coroutineScope.launch {
                        preset.value.forEach {
                            db.equalizerPresetDao().upsertEqualizerLevelRatio(
                                it.copy(ratio = 0.5f)
                            )
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(id = R.string.equalizer_flatten),
                    fontSize = 16.sp,
                    color = QTheme.colors.colorTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        db.equalizerPresetDao().addEqualizerPreset(
                            equalizerPreset = EqualizerPreset(
                                id = 0,
                                label = ""
                            ),
                            equalizerLevelRatios = selectedPreset?.value ?: params.bands.map {
                                EqualizerLevelRatio(
                                    id = 0,
                                    presetId = 0,
                                    centerFrequency = it.centerFreq,
                                    ratio = 0.5f
                                )
                            },
                            overrideLabelById = { id -> "temporary $id" }
                        )
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add to presets")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = presetsLazyListState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = presetsLazyListState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            itemsIndexed(presetMap.entries.toList()) { index, equalizerPreset ->
                val isSelected = equalizerPreset.key.id == selectedPreset?.key?.id
                if (isSelected) {
                    Row {
                        Spacer(
                            modifier = Modifier.width(
                                if (presetMap.size > 1 && index == 0) (LocalConfiguration.current.screenWidthDp * 0.3).dp
                                else 0.dp
                            )
                        )
                        Text(
                            text = equalizerPreset.key.label,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = QTheme.colors.colorTextPrimary,
                            modifier = Modifier
                                .width((LocalConfiguration.current.screenWidthDp * 0.4).dp)
                                .padding(horizontal = 4.dp)
                        )
                        Spacer(
                            modifier = Modifier.width(
                                if (presetMap.size > 1 && index == presetMap.size - 1) (LocalConfiguration.current.screenWidthDp * 0.3).dp
                                else 0.dp
                            )
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Spacer(modifier = Modifier.height(with(LocalDensity.current) { 33.sp.toDp() }))
                        Row {
                            Spacer(
                                modifier = Modifier.width(
                                    if (presetMap.size > 1 && index == 0) (LocalConfiguration.current.screenWidthDp * 0.3).dp
                                    else 0.dp
                                )
                            )
                            Text(
                                text = equalizerPreset.key.label,
                                fontSize = 16.sp,
                                color = QTheme.colors.colorTextPrimary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .width((LocalConfiguration.current.screenWidthDp * 0.4).dp)
                                    .padding(horizontal = 4.dp)
                            )
                            Spacer(
                                modifier = Modifier.width(
                                    if (presetMap.size > 1 && index == presetMap.size - 1) (LocalConfiguration.current.screenWidthDp * 0.3).dp
                                    else 0.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}