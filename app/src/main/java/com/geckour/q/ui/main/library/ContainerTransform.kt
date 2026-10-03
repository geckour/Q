package com.geckour.q.ui.main.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

const val NAV_TRANSITION_MILLIS = 300

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

fun artistContainerKey(artistId: Long): String = "container-artist-$artistId"

fun albumContainerKey(albumId: Long): String = "container-album-$albumId"

fun genreContainerKey(genreName: String): String = "container-genre-$genreName"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(key: String?): Modifier {
    key ?: return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this

    return with(sharedTransitionScope) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> tween(NAV_TRANSITION_MILLIS) },
        )
    }
}
