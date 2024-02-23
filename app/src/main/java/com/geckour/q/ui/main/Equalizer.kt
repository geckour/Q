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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.AudioDeviceEqualizerInfo
import com.geckour.q.data.db.model.EqualizerLevelRatio
import com.geckour.q.data.db.model.EqualizerPreset
import com.geckour.q.domain.model.EqualizerParams
import com.geckour.q.domain.model.QAudioDeviceInfo
import com.geckour.q.ui.DoubleTrackSlider
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.getEqualizerEnabled
import com.geckour.q.util.getEqualizerParams
import com.geckour.q.util.getSelectedEqualizerPresetId
import com.geckour.q.util.setEqualizerEnabled
import com.geckour.q.util.setSelectedEqualizerPresetId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

@Composable
fun Equalizer(routeInfo: QAudioDeviceInfo?) {
    val context = LocalContext.current
    val db = DB.getInstance(context)
    val equalizerEnabled by context.getEqualizerEnabled()
        .collectAsState(initial = false)
    val equalizerParams by context.getEqualizerParams().collectAsState(initial = null)
    val equalizerPresetsMap by db.equalizerPresetDao()
        .getEqualizerPresets()
        .collectAsState(initial = null)
    val audioDeviceEqualizerInfo by db.audioDeviceEqualizerInfoDao()
        .getAsFlow(
            routeId = routeInfo?.routeId.orEmpty(),
            deviceId = routeInfo?.audioDeviceId ?: -1,
            deviceAddress = routeInfo?.address,
            deviceName = routeInfo?.audioDeviceName.orEmpty()
        ).collectAsState(initial = null)
    val presetsLazyListState = rememberLazyListState()
    val centerVisiblePresetIndex by remember {
        derivedStateOf {
            presetsLazyListState.layoutInfo
                .let { layoutInfo ->
                    val completelyVisibleItemsInfo =
                        layoutInfo.visibleItemsInfo.filter {
                            it.offset >= layoutInfo.viewportStartOffset &&
                                    it.offset + it.size <= layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset
                        }
                    when {
                        equalizerPresetsMap?.size == 1 -> 0
                        completelyVisibleItemsInfo.isEmpty() -> layoutInfo.visibleItemsInfo.firstOrNull()?.index
                            ?: -1

                        else -> completelyVisibleItemsInfo[completelyVisibleItemsInfo.size / 2].index
                    }
                }
        }
    }
    val normalizedCenterVisiblePresetIndex by remember {
        derivedStateOf {
            if (presetsLazyListState.layoutInfo.totalItemsCount > 2 &&
                centerVisiblePresetIndex !in 1 until presetsLazyListState.layoutInfo.totalItemsCount - 1
            ) {
                centerVisiblePresetIndex.coerceIn(1 until presetsLazyListState.layoutInfo.totalItemsCount - 1)
            } else centerVisiblePresetIndex
        }
    }
    val selectedPreset by remember {
        derivedStateOf {
            equalizerPresetsMap?.entries
                ?.toList()
                ?.getOrNull(normalizedCenterVisiblePresetIndex - 1)
        }
    }
    var selectedPresetCache by remember { mutableStateOf(selectedPreset) }

    val params = equalizerParams ?: return
    val presetsMap = equalizerPresetsMap ?: return

    val presetItemOffset = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp.toPx() * -0.25).toInt()
    }

    LaunchedEffect(presetsMap, equalizerParams) {
        if (presetsMap.isEmpty()) {
            db.equalizerPresetDao()
                .addEqualizerPreset(
                    equalizerPreset = EqualizerPreset(
                        id = 0,
                        label = "Preset 1"
                    ),
                    equalizerLevelRatios = params.bands.map {
                        EqualizerLevelRatio(
                            id = 0,
                            presetId = 0,
                            centerFrequency = it.centerFreq,
                            ratio = 0.5f
                        )
                    },
                    overrideLabelById = { id -> "Preset $id" }
                )
        }
    }

    LaunchedEffect(selectedPreset) {
        if (centerVisiblePresetIndex < 1) return@LaunchedEffect
        selectedPreset?.let {
            selectedPresetCache = it
            context.setSelectedEqualizerPresetId(it.key.id)
        }
    }

    LaunchedEffect(Unit) {
        presetsLazyListState.scrollToItem(
            index = presetsMap.keys
                .indexOfFirst {
                    it.id == context.getSelectedEqualizerPresetId().take(1).lastOrNull()
                }
                .coerceAtLeast(0) + 1,
            scrollOffset = presetItemOffset,
        )
        return@LaunchedEffect
    }

    LaunchedEffect(presetsMap.size) {
        presetsLazyListState.animateScrollToItem(
            index = normalizedCenterVisiblePresetIndex,
            scrollOffset = presetItemOffset
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        TopController(
            db = db,
            equalizerEnabled = equalizerEnabled,
            routeInfo = routeInfo,
            audioDeviceEqualizerInfo = audioDeviceEqualizerInfo,
            selectedPreset = selectedPreset
        )
        Spacer(modifier = Modifier.height(20.dp))
        EqualizerSubstance(
            db = db,
            equalizerEnabled = equalizerEnabled,
            equalizerParams = params,
            selectedPresetLevelRatios = selectedPreset?.value,
            getLatestSelectedPresetLevelRatios = { selectedPreset?.value }
        )
        Spacer(modifier = Modifier.height(20.dp))
        BottomController(
            db = db,
            equalizerParams = params,
            equalizerPresetsSize = presetsMap.size,
            selectedPreset = selectedPreset
        )
        Spacer(modifier = Modifier.height(20.dp))
        EqualizerPresetSelector(
            db = db,
            presetsLazyListState = presetsLazyListState,
            equalizerPresetsMap = presetsMap,
            selectedPresetId = selectedPreset?.key?.id ?: selectedPresetCache?.key?.id
        )
    }
}

@Composable
fun TopController(
    db: DB,
    equalizerEnabled: Boolean,
    routeInfo: QAudioDeviceInfo?,
    audioDeviceEqualizerInfo: AudioDeviceEqualizerInfo?,
    selectedPreset: Map.Entry<EqualizerPreset, List<EqualizerLevelRatio>>?,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            modifier = Modifier.alpha(if (routeInfo == null) 0f else 1f),
            onClick = {
                routeInfo ?: return@Button
                selectedPreset ?: return@Button
                coroutineScope.launch {
                    if (audioDeviceEqualizerInfo?.defaultEqualizerPresetId == null || audioDeviceEqualizerInfo.defaultEqualizerPresetId != selectedPreset.key.id) {
                        db.audioDeviceEqualizerInfoDao().upsertByCustomConflictDetection(
                            AudioDeviceEqualizerInfo(
                                id = 0,
                                routeId = routeInfo.routeId,
                                deviceAddress = routeInfo.address,
                                deviceId = routeInfo.audioDeviceId,
                                deviceName = routeInfo.audioDeviceName,
                                defaultEqualizerPresetId = selectedPreset.key.id
                            )
                        )
                    } else {
                        db.audioDeviceEqualizerInfoDao()
                            .deleteBy(
                                routeId = routeInfo.routeId,
                                deviceAddress = routeInfo.address,
                                deviceId = routeInfo.audioDeviceId,
                            )
                    }
                }
            }
        ) {
            Text(
                text = stringResource(
                    id = if (audioDeviceEqualizerInfo?.defaultEqualizerPresetId == null || audioDeviceEqualizerInfo.defaultEqualizerPresetId != selectedPreset?.key?.id) {
                        R.string.equalizer_set_as_default_for_the_device
                    } else R.string.equalizer_clear_default_for_the_device,
                    routeInfo?.audioDeviceName.orEmpty()
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(id = if (equalizerEnabled) R.string.equalizer_switch_enabled else R.string.equalizer_switch_disabled),
            fontSize = 16.sp,
            color = QTheme.colors.colorTextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        QSwitch(
            checked = equalizerEnabled,
            onCheckedChange = { coroutineScope.launch { context.setEqualizerEnabled(it) } }
        )
    }
}

@Composable
fun ColumnScope.EqualizerSubstance(
    db: DB,
    equalizerEnabled: Boolean,
    equalizerParams: EqualizerParams,
    selectedPresetLevelRatios: List<EqualizerLevelRatio>?,
    getLatestSelectedPresetLevelRatios: () -> List<EqualizerLevelRatio>?
) {
    val coroutineScope = rememberCoroutineScope()
    var updateEqualizerPresetJob by remember { mutableStateOf<Job>(Job()) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    var boxHeight by remember { mutableIntStateOf(screenWidth) }
    var labelHeight by remember { mutableIntStateOf(0) }
    var labelWidthMap by remember { mutableStateOf(emptyMap<Int, Int>()) }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .weight(1f)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { boxHeight = it.height }
            ) {
                Column(
                    Modifier
                        .matchParentSize()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(5) {
                        HorizontalDivider(color = QTheme.colors.colorPrimary)
                    }
                }
                if (selectedPresetLevelRatios?.isNotEmpty() == true) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.matchParentSize()
                    ) {
                        equalizerParams.bands.indices.forEach { index ->
                            Box(contentAlignment = Alignment.Center) {
                                Spacer(
                                    modifier = Modifier.width(
                                        with(LocalDensity.current) {
                                            labelWidthMap.maxOfOrNull { it.value }?.toDp()
                                        } ?: 0.dp
                                    )
                                )
                                DoubleTrackSlider(
                                    key = index,
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
                                        .width(with(LocalDensity.current) { boxHeight.toDp() }),
                                    primaryProgressFraction = selectedPresetLevelRatios[index].ratio,
                                    steps = equalizerParams.levelRange.second - equalizerParams.levelRange.first,
                                    primaryTrackColor = QTheme.colors.colorButtonNormal,
                                    onSeekEnded = { newRatio ->
                                        updateEqualizerPresetJob.cancel()
                                        updateEqualizerPresetJob = coroutineScope.launch {
                                            getLatestSelectedPresetLevelRatios()
                                                ?.getOrNull(index)
                                                ?.let { equalizerLevelRatio ->
                                                    db.equalizerPresetDao()
                                                        .upsertEqualizerLevelRatio(
                                                            equalizerLevelRatio.copy(ratio = newRatio)
                                                        )
                                                }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Box {
                Spacer(
                    modifier = Modifier
                        .height(with(LocalDensity.current) { labelHeight.toDp() })
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    equalizerParams.bands.forEachIndexed { index, band ->
                        Box(contentAlignment = Alignment.Center) {
                            Spacer(
                                modifier = Modifier.width(
                                    with(LocalDensity.current) {
                                        labelWidthMap
                                            .maxOfOrNull { it.value }
                                            ?.toDp()
                                    } ?: 0.dp
                                )
                            )
                            Text(
                                text = "${(band.centerFreq / 1000)}kHz",
                                fontSize = 12.sp,
                                color = QTheme.colors.colorTextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.onSizeChanged {
                                    labelHeight = it.height
                                    labelWidthMap = labelWidthMap.toMutableMap()
                                        .apply { this[index] = it.width }
                                }
                            )
                        }
                    }
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
}

@Composable
private fun ColumnScope.BottomController(
    db: DB,
    equalizerParams: EqualizerParams,
    equalizerPresetsSize: Int,
    selectedPreset: Map.Entry<EqualizerPreset, List<EqualizerLevelRatio>>?,
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp)) {
            Spacer(modifier = Modifier.size(24.dp))
            androidx.compose.animation.AnimatedVisibility(
                visible = equalizerPresetsSize > 1,
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
                        equalizerLevelRatios = selectedPreset?.value ?: equalizerParams.bands.map {
                            EqualizerLevelRatio(
                                id = 0,
                                presetId = 0,
                                centerFrequency = it.centerFreq,
                                ratio = 0.5f
                            )
                        },
                        overrideLabelById = { id -> "Preset $id" }
                    )
                }
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to presets"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EqualizerPresetSelector(
    db: DB,
    presetsLazyListState: LazyListState,
    equalizerPresetsMap: Map<EqualizerPreset, List<EqualizerLevelRatio>>,
    selectedPresetId: Long?
) {
    var textFieldHeight by remember { mutableIntStateOf(0) }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = presetsLazyListState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = presetsLazyListState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        item {
            Spacer(modifier = Modifier.width((LocalConfiguration.current.screenWidthDp * 0.5).dp))
        }
        items(equalizerPresetsMap.entries.toList()) { equalizerPresetEntry ->
            Box(contentAlignment = Alignment.Center) {
                val isSelected = equalizerPresetEntry.key.id == selectedPresetId

                Spacer(
                    modifier = Modifier.height(
                        with(LocalDensity.current) { textFieldHeight.toDp() }
                    )
                )
                if (isSelected) {
                    EqualizerPresetNameTextField(
                        modifier = Modifier.onSizeChanged { textFieldHeight = it.height },
                        db = db,
                        equalizerPreset = equalizerPresetEntry.key
                    )
                } else {
                    Text(
                        text = equalizerPresetEntry.key.label,
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextPrimary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width((LocalConfiguration.current.screenWidthDp * 0.5).dp)
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.width((LocalConfiguration.current.screenWidthDp * 0.5).dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EqualizerPresetNameTextField(
    modifier: Modifier = Modifier,
    db: DB,
    equalizerPreset: EqualizerPreset
) {
    val coroutineScope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = equalizerPreset.label,
                selection = TextRange(equalizerPreset.label.length)
            )
        )
    }
    val onSave: () -> Unit = {
        if (editing) {
            coroutineScope.launch {
                db.equalizerPresetDao().upsertEqualizerPreset(
                    equalizerPreset.copy(label = textFieldValue.text)
                )
            }
        }
        editing = editing.not()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width((LocalConfiguration.current.screenWidthDp * 0.5).dp)
            .padding(horizontal = 4.dp)
    ) {

        if (editing) {
            val textFieldFocusRequester by remember { mutableStateOf(FocusRequester()) }

            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = QTheme.colors.colorTextPrimary
                ),
                cursorBrush = SolidColor(value = QTheme.colors.colorTextPrimary),
                decorationBox = {
                    Column {
                        it()
                        HorizontalDivider(
                            color = QTheme.colors.colorPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                },
                singleLine = true,
                keyboardActions = KeyboardActions(
                    onDone = { onSave() },
                    onGo = { onSave() },
                    onNext = { onSave() },
                    onSend = { onSave() }
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(textFieldFocusRequester)
            )

            LaunchedEffect(editing) {
                textFieldFocusRequester.requestFocus()
            }
        } else {
            Text(
                text = equalizerPreset.label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = QTheme.colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(
            onClick = onSave
        ) {
            Icon(
                imageVector = if (editing) Icons.Default.Done else Icons.Default.Edit,
                contentDescription = stringResource(
                    id = if (editing) R.string.equalizer_preset_label_confirm else R.string.equalizer_preset_label_edit
                ),
                modifier = Modifier.size(20.dp),
                tint = QTheme.colors.colorTextPrimary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopControllerPreview() {
    TopController(
        db = DB.getInstance(LocalContext.current),
        equalizerEnabled = true,
        routeInfo = null,
        audioDeviceEqualizerInfo = null,
        selectedPreset = null
    )
}