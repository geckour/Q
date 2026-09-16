package com.geckour.q.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.geckour.q.R
import com.geckour.q.domain.model.UiSavedQueue
import com.geckour.q.domain.model.UiTrack
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType

@Composable
fun SaveQueueDialog(
    nextId: Long,
    onCancel: () -> Unit,
    onPositive: (title: String) -> Unit,
) {
    var title by remember { mutableStateOf<String?>(null) }
    val defaultTitle = stringResource(R.string.dialog_title_save_queue, nextId)
    Dialog(onDismissRequest = onCancel) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 16.dp,
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_message_save_queue),
                    fontSize = 18.sp,
                    color = QTheme.colors.colorTextPrimary,
                )
                TextField(
                    title.orEmpty(),
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            defaultTitle,
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextSecondary,
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onPositive(title ?: defaultTitle) }) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedQueueOptionDialog(
    uiSavedQueue: UiSavedQueue,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    fun option(@StringRes labelResId: Int, event: DialogEvent) = QOption(labelResId) {
        onDialogEvent(event)
        onDialogEvent(DialogEvent.Dismiss)
    }

    fun newQueue(@StringRes labelResId: Int, actionType: InsertActionType) = option(
        labelResId,
        DialogEvent.NewQueue(
            uiSavedQueue.queue.map { it.track.sourcePath },
            actionType,
            OrientedClassType.TRACK,
            false,
        )
    )

    QOptionDialog(
        options = listOf(
            newQueue(R.string.menu_insert_next, InsertActionType.NEXT),
            newQueue(R.string.menu_insert_last, InsertActionType.LAST),
            newQueue(R.string.menu_override, InsertActionType.OVERRIDE),
            option(
                R.string.menu_delete_from_device,
                DialogEvent.DeleteSavedQueue(uiSavedQueue.savedQueueSummary.savedQueue.id)
            ),
        ),
        onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
    )
}

@Composable
fun SavedQueueModifyDialog(
    uiSavedQueue: UiSavedQueue,
    currentQueue: List<UiTrack>,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    val newTitle =
        rememberTextFieldState(initialText = uiSavedQueue.savedQueueSummary.savedQueue.title)
    var overrideWithCurrentQueue by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
        Card(
            colors = CardDefaults.cardColors()
                .copy(containerColor = QTheme.colors.colorBackground)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 24.dp,
                    vertical = 16.dp
                )
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_title_saved_queue_modify),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = QTheme.colors.colorAccent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    state = newTitle,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextPrimary,
                    ),
                    label = {
                        Text(
                            stringResource(R.string.dialog_label_saved_queue_modify_title),
                            fontSize = 10.sp,
                            color = QTheme.colors.colorTextSecondary,
                        )
                    },
                    placeholder = {
                        Text(
                            newTitle.text.toString(),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextSecondary,
                        )
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                newTitle.setTextAndPlaceCursorAtEnd(
                                    uiSavedQueue.savedQueueSummary.savedQueue.title,
                                )
                            }
                        ) {
                            Text(
                                stringResource(R.string.text_edit_reset),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorPrimary,
                            )
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight,
                        )
                        .fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.dialog_saved_queue_modify_switch_title),
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = overrideWithCurrentQueue,
                        onCheckedChange = { overrideWithCurrentQueue = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp),
                ) {
                    TextButton(onClick = { onDialogEvent(DialogEvent.Dismiss) }) {
                        Text(
                            text = stringResource(R.string.dialog_ng),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorTextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onDialogEvent(
                                DialogEvent.ModifySavedQueue(
                                    uiSavedQueue.savedQueueSummary.savedQueue.id,
                                    newTitle.text.toString()
                                        .ifEmpty { uiSavedQueue.savedQueueSummary.savedQueue.title },
                                    if (overrideWithCurrentQueue) currentQueue.map { it.id }
                                    else uiSavedQueue.queue.map { it.track.id },
                                )
                            )
                            onDialogEvent(DialogEvent.Dismiss)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_ok),
                            fontSize = 16.sp,
                            color = QTheme.colors.colorAccent,
                        )
                    }
                }
            }
        }
    }
}
