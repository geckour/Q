package com.geckour.q.ui.main

sealed interface PendingResumeAction {

    data class StartSpotifyTrackRadio(val trackUri: String) : PendingResumeAction

    data object StoreDropboxToken : PendingResumeAction
}
