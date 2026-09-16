package com.geckour.q.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme

data class QOption(
    @StringRes val labelResId: Int,
    val onClick: () -> Unit,
)

@Composable
fun QOptionDialog(
    options: List<QOption>,
    onDismissRequest: () -> Unit,
    isFavoriteOnly: MutableState<Boolean>? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column {
                if (isFavoriteOnly != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = stringResource(id = R.string.dialog_switch_desc_filter_only_favorite)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        QSwitch(
                            checked = isFavoriteOnly.value,
                            onCheckedChange = { isFavoriteOnly.value = isFavoriteOnly.value.not() }
                        )
                    }
                }
                options.forEach { option ->
                    DialogListItem(onClick = option.onClick) {
                        Text(
                            text = stringResource(id = option.labelResId),
                            fontSize = 14.sp,
                            color = QTheme.colors.colorTextPrimary
                        )
                    }
                }
            }
        }
    }
}
