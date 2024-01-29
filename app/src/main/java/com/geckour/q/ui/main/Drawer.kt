package com.geckour.q.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.DrawerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.geckour.q.R
import com.geckour.q.domain.model.Nav
import com.geckour.q.ui.compose.ColorStrong
import com.geckour.q.ui.compose.QTheme
import com.geckour.q.util.EqualizerParams
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerHeader(openQzi: () -> Unit) {
    val fontProvider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    val fontName = GoogleFont("Josefin Sans")
    Row(
        Modifier
            .background(color = ColorStrong)
            .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Image(
            painter = painterResource(id = R.drawable.head_icon),
            contentDescription = null,
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = openQzi,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = false)
                )
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = R.string.nav_header_desc),
            fontFamily = FontFamily(Font(googleFont = fontName, fontProvider = fontProvider)),
            color = Color.White
        )
    }
}

@Composable
fun DrawerItem(
    @DrawableRes iconResId: Int,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(color = if (isSelected) QTheme.colors.colorPrimaryDark else QTheme.colors.colorBackground)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = if (isSelected) QTheme.colors.colorAccent else QTheme.colors.colorTextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = if (isSelected) QTheme.colors.colorAccent else QTheme.colors.colorTextPrimary
        )
    }
}

@Composable
fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        color = QTheme.colors.colorTextPrimary,
        modifier = Modifier.padding(16.dp)
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Drawer(
    drawerState: DrawerState,
    navController: NavHostController,
    selectedNav: Nav?,
    equalizerParams: EqualizerParams?,
    onSelectNav: (nav: Nav?) -> Unit,
    onShowDropboxDialog: () -> Unit,
    onRetrieveMedia: (onlyAdded: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier
            .background(color = QTheme.colors.colorBackground)
            .fillMaxSize()
    ) {
        item {
            BackHandler(drawerState.isOpen) {
                coroutineScope.launch { drawerState.close() }
            }
        }

        item {
            DrawerHeader(
                openQzi = {
                    navController.navigate("qzi")
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            DrawerSectionHeader(title = stringResource(id = R.string.nav_category_library))
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_artist,
                title = stringResource(id = R.string.nav_artist),
                isSelected = selectedNav == Nav.ARTIST,
                onClick = {
                    navController.navigate("artists")
                    onSelectNav(Nav.ARTIST)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_album,
                title = stringResource(id = R.string.nav_album),
                isSelected = selectedNav == Nav.ALBUM,
                onClick = {
                    navController.navigate("albums")
                    onSelectNav(Nav.ALBUM)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_track,
                title = stringResource(id = R.string.nav_track),
                isSelected = selectedNav == Nav.TRACK,
                onClick = {
                    navController.navigate("tracks")
                    onSelectNav(Nav.TRACK)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_genre,
                title = stringResource(id = R.string.nav_genre),
                isSelected = selectedNav == Nav.GENRE,
                onClick = {
                    navController.navigate("genres")
                    onSelectNav(Nav.GENRE)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            Divider(color = QTheme.colors.colorPrimaryDark)
        }
        item {
            DrawerSectionHeader(title = stringResource(id = R.string.nav_category_others))
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_dropbox,
                title = stringResource(id = R.string.nav_dropbox_sync),
                isSelected = selectedNav == Nav.DROPBOX_SYNC,
                onClick = {
                    onShowDropboxDialog()
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_sync,
                title = stringResource(id = R.string.nav_sync),
                isSelected = selectedNav == Nav.SYNC,
                onClick = {
                    onRetrieveMedia(false)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        item {
            DrawerItem(
                iconResId = R.drawable.ic_motive,
                title = stringResource(id = R.string.nav_pay),
                isSelected = selectedNav == Nav.PAY,
                onClick = {
                    navController.navigate("pay")
                    onSelectNav(Nav.PAY)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
        if (equalizerParams != null) {
            item {
                DrawerItem(
                    iconResId = R.drawable.ic_spectrum,
                    title = stringResource(id = R.string.nav_equalizer),
                    isSelected = selectedNav == Nav.EQUALIZER,
                    onClick = {
                        navController.navigate("equalizer")
                        onSelectNav(Nav.EQUALIZER)
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    }
}