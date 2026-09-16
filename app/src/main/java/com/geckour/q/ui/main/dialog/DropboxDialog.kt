package com.geckour.q.ui.main.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.R
import com.geckour.q.ui.component.QConfirmDialog
import com.geckour.q.ui.component.QSwitch
import com.geckour.q.ui.compose.QTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun DropboxDialog(
    state: DialogState.Dropbox,
    onDialogEvent: (event: DialogEvent) -> Unit,
) {
    if (state.hasAlreadyShownSyncAlert) {
        if (state.hasCredential.not()) {
            LaunchedEffect(Unit) {
                onDialogEvent(DialogEvent.StartDropboxAuth)
            }
        } else {
            if (state.itemList.first.isEmpty() && state.itemList.second.isEmpty()) {
                LaunchedEffect(Unit) {
                    onDialogEvent(DialogEvent.ShowDropboxFolder(null))
                }
                return
            }
            var selectedHistory by remember {
                mutableStateOf<ImmutableList<FolderMetadata>>(persistentListOf())
            }
            Dialog(onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) }) {
                var needDownloaded by remember { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors()
                        .copy(containerColor = QTheme.colors.colorBackground),
                    modifier = Modifier.heightIn(max = 800.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.dialog_title_dropbox_choose_folder),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = QTheme.colors.colorAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.dialog_desc_dropbox_choose_folder),
                            fontSize = 18.sp,
                            color = QTheme.colors.colorTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.dialog_switch_need_downloaded),
                                fontSize = 18.sp,
                                color = QTheme.colors.colorTextPrimary,
                            )
                            QSwitch(
                                checked = needDownloaded,
                                onCheckedChange = {
                                    needDownloaded =
                                        needDownloaded.not()
                                })
                        }
                        Text(
                            text = state.itemList.first,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = QTheme.colors.colorAccent
                        )
                        if (state.itemList.second.isEmpty() && state.itemList.third.isEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.dialog_desc_dropbox_empty_folder
                                ),
                                fontSize = 16.sp,
                                color = QTheme.colors.colorAccent
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                items(state.itemList.second) {
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                selectedHistory = (selectedHistory.toList() + it)
                                                    .toImmutableList()
                                                onDialogEvent(DialogEvent.ShowDropboxFolder(it))
                                            }
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 12.dp
                                            )
                                            .fillMaxWidth()
                                    ) {
                                        val iconId = "id-icon"
                                        Text(
                                            text = buildAnnotatedString {
                                                appendInlineContent(iconId, "Icon")
                                                append(" ${it.name}")
                                            },
                                            inlineContent = mapOf(
                                                iconId to InlineTextContent(
                                                    Placeholder(
                                                        20.sp,
                                                        20.sp,
                                                        PlaceholderVerticalAlign.Center,
                                                    )
                                                ) {
                                                    Icon(
                                                        contentDescription = stringResource(R.string.content_description_folder),
                                                        tint = QTheme.colors.colorTextPrimary,
                                                        imageVector = Icons.Default.Folder,
                                                    )
                                                },
                                            ),
                                            fontSize = 20.sp,
                                            color = QTheme.colors.colorTextPrimary,
                                        )
                                    }
                                }
                                items(state.itemList.third) {
                                    Row(
                                        modifier = Modifier
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 12.dp
                                            )
                                            .fillMaxWidth()
                                    ) {
                                        val iconId = "id-icon"
                                        Text(
                                            text = buildAnnotatedString {
                                                appendInlineContent(iconId, "Icon")
                                                append(" ${it.name}")
                                            },
                                            inlineContent = mapOf(
                                                iconId to InlineTextContent(
                                                    Placeholder(
                                                        20.sp,
                                                        20.sp,
                                                        PlaceholderVerticalAlign.Center,
                                                    )
                                                ) {
                                                    Icon(
                                                        contentDescription = stringResource(R.string.content_description_folder),
                                                        tint = QTheme.colors.colorTextSecondary,
                                                        imageVector = Icons.Default.FilePresent,
                                                    )
                                                },
                                            ),
                                            fontSize = 20.sp,
                                            color = QTheme.colors.colorTextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val prev = {
                                selectedHistory =
                                    selectedHistory.dropLast(1).toImmutableList()
                                onDialogEvent(DialogEvent.ShowDropboxFolder(selectedHistory.lastOrNull()))
                            }
                            BackHandler(selectedHistory.isNotEmpty()) {
                                prev()
                            }
                            if (selectedHistory.isNotEmpty()) {
                                TextButton(onClick = prev) {
                                    Text(
                                        text = stringResource(R.string.dialog_prev),
                                        fontSize = 16.sp,
                                        color = QTheme.colors.colorTextPrimary
                                    )
                                }
                            }
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            TextButton(onClick = { onDialogEvent(DialogEvent.Dismiss) }) {
                                Text(
                                    text = stringResource(R.string.dialog_ng),
                                    fontSize = 16.sp,
                                    color = QTheme.colors.colorTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    onDialogEvent(
                                        DialogEvent.StartDropboxSync(
                                            selectedHistory.lastOrNull()?.pathLower,
                                            needDownloaded
                                        )
                                    )
                                    onDialogEvent(DialogEvent.Dismiss)
                                }
                            ) {
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
    } else {
        QConfirmDialog(
            title = stringResource(id = R.string.dialog_title_dropbox_sync_caution),
            message = stringResource(id = R.string.dialog_desc_dropbox_sync_caution),
            onPositive = { onDialogEvent(DialogEvent.AcknowledgeDropboxSyncAlert) },
            onDismissRequest = { onDialogEvent(DialogEvent.Dismiss) },
        )
    }
}
