package com.geckour.q.ui.license

import android.content.Context
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geckour.q.R
import com.geckour.q.domain.model.LicenseItem
import com.geckour.q.ui.compose.QTheme
import kotlinx.collections.immutable.persistentListOf

@Composable
fun Licenses(endItemMargin: Dp = 0.dp) {
    val context = LocalContext.current
    val ffmpegNotice = stringResource(id = R.string.license_text_ffmpeg)
    val ffmpegText = remember(context, ffmpegNotice) {
        "$ffmpegNotice\n\n${context.readRawText(R.raw.lgpl_2_1)}"
    }
    val items = persistentListOf(
        LicenseItem(
            stringResource(id = R.string.license_name_koin),
            stringResource(id = R.string.license_text_koin)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_coroutines),
            stringResource(id = R.string.license_text_coroutines)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_androidx),
            stringResource(id = R.string.license_text_androidx)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_binding),
            stringResource(id = R.string.license_text_binding)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_timber),
            stringResource(id = R.string.license_text_timber)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_json),
            stringResource(id = R.string.license_text_json)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_aac),
            stringResource(id = R.string.license_text_aac)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_permission),
            stringResource(id = R.string.license_text_permission)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_coil),
            stringResource(id = R.string.license_text_coil)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_exo),
            stringResource(id = R.string.license_text_exo)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_seek_bar),
            stringResource(id = R.string.license_text_seek_bar)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_dropbox),
            stringResource(id = R.string.license_text_dropbox)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_codec),
            stringResource(id = R.string.license_text_codec)
        ),
        LicenseItem(
            stringResource(id = R.string.license_name_ffmpeg),
            ffmpegText
        ),
    )
    val openNames = remember { mutableStateListOf<String>() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items) { item ->
            val isOpen = openNames.contains(item.name)

            Column(
                modifier = Modifier
                    .clickable {
                        if (isOpen) openNames.remove(item.name) else openNames.add(item.name)
                    }
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        color = QTheme.colors.colorTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isOpen) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = QTheme.colors.colorTextPrimary,
                    )
                }
                if (isOpen) {
                    Text(
                        text = item.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = QTheme.colors.colorTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = QTheme.colors.colorTextSecondary.copy(alpha = 0.2f))
        }
        item {
            Spacer(modifier = Modifier.height(endItemMargin))
        }
    }
}

private fun Context.readRawText(@RawRes resId: Int): String =
    resources.openRawResource(resId).bufferedReader().use { it.readText() }
