package com.geckour.q.ui.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@Composable
fun QSwitch(
    checked: Boolean,
    onCheckedChange: (checked: Boolean) -> Unit,
    scale: Float = 0.8f,
    padding: PaddingValues = PaddingValues(0.dp)
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier
            .padding(padding)
            .scale(scale)
    )
}