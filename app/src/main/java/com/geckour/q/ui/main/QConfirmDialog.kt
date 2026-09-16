package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme

@Composable
fun QConfirmDialog(
    message: String,
    onPositive: () -> Unit,
    onDismissRequest: () -> Unit,
    title: String? = null,
    onNegative: (() -> Unit)? = onDismissRequest,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = QTheme.colors.colorAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = message,
                    fontSize = 18.sp,
                    color = QTheme.colors.colorTextPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onNegative != null) {
                        TextButton(onClick = onNegative) {
                            Text(
                                text = stringResource(R.string.dialog_ng),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onPositive) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent
                        )
                    }
                }
            }
        }
    }
}
