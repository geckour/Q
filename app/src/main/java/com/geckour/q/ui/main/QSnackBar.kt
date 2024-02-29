package com.geckour.q.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme

@Composable
fun QSnackBar(message: String?, progress: Float?, onCancelProgress: (() -> Unit)?) {
    AnimatedVisibility(
        visible = message != null
    ) {
        Row(
            modifier = Modifier
                .background(color = QTheme.colors.colorBackground)
                .padding(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors()
                    .copy(containerColor = QTheme.colors.colorBackgroundProgress),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.orEmpty(),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        onCancelProgress?.let {
                            TextButton(onClick = it) {
                                Text(
                                    text = stringResource(R.string.button_cancel),
                                    fontSize = 16.sp,
                                    color = QTheme.colors.colorAccent
                                )
                            }
                        }
                    }
                    progress?.let {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = Color.Transparent,
                            progress = { it }
                        )
                    }
                }
            }
        }
    }
}