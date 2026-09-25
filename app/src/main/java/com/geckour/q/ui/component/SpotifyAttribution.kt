package com.geckour.q.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geckour.q.R
import com.geckour.q.ui.compose.QTheme

private val SpotifyLogoClearSpace = 4.dp

@Composable
fun SpotifyAttribution(modifier: Modifier = Modifier) {
    SpotifyLogo(
        modifier = modifier
            .wrapContentWidth(align = Alignment.End)
            .padding(SpotifyLogoClearSpace)
    )
}

@Composable
fun SpotifyLogo(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
) {
    Image(
        painter = painterResource(
            id = if (QTheme.colors.isLight) R.drawable.spotify_logo_black
            else R.drawable.spotify_logo_white
        ),
        contentDescription = stringResource(id = R.string.spotify_attribution),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier)
    )
}

@Preview
@Composable
fun SpotifyLogoPreview() {
    Column {
        SpotifyLogo(height = 24.dp)
        SpotifyLogo(height = 56.dp)
        SpotifyLogo(width = 40.dp)
        SpotifyLogo(width = 100.dp)
        SpotifyLogo(width = 40.dp, height = 200.dp)
    }
}