package com.geckour.q.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme

@Composable
fun QSnackBar(message: String?, onCancelProgress: (() -> Unit)?) {
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
                backgroundColor = QTheme.colors.colorBackgroundProgress
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom
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
            }
        }
    }
}