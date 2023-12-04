package com.geckour.q.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme

@Composable
fun Pay(onStartBilling: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.payment_desc),
            fontSize = 16.sp,
            color = QTheme.colors.colorTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStartBilling) {
            Text(
                text = stringResource(id = R.string.payment_action_pay),
                fontSize = 16.sp,
                color = QTheme.colors.colorTextPrimary
            )
        }
    }
}