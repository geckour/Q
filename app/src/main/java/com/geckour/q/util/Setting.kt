package com.geckour.q.util

import android.content.SharedPreferences
import com.geckour.q.R

private const val KEY_PREFER_SCREEN = "key_prefer_screen"

enum class Screen(val displayNameResId: Int) {
    ARTIST(R.string.nav_artist),
    ALBUM(R.string.nav_album),
    SONG(R.string.nav_song),
    GENRE(R.string.nav_genre),
    PLAYLIST(R.string.nav_playlist)
}

fun SharedPreferences.getPreferScreen(): Screen =
        Screen.values()[getInt(KEY_PREFER_SCREEN, Screen.ARTIST.ordinal)]

fun SharedPreferences.setPreferScreen(screen: Screen) {
    edit().putInt(KEY_PREFER_SCREEN, screen.ordinal).apply()
}