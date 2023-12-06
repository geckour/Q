package com.geckour.q.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geckour.q.ui.compose.QTheme

@Composable
fun DialogListItem(onClick: () -> Unit, child: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(color = QTheme.colors.colorBackground)
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        child()
    }
}