package com.geckour.q.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import com.geckour.q.R
import com.geckour.q.ui.compose.ColorStrong
import com.geckour.q.ui.compose.QTheme

@Composable
fun DrawerHeader() {
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