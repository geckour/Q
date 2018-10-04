package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.geckour.q.R

private const val KEY_PREFER_SCREEN = "key_prefer_screen"
private const val KEY_DUCKING = "key_ducking"
private const val KEY_PATTERN_FORMAT_SHARE_TEXT = "key_pattern_format_share_text"
private const val KEY_BUNDLE_ARTWORK = "key_bundle_artwork"

enum class Screen(val displayNameResId: Int) {
    ARTIST(R.string.nav_artist),
    ALBUM(R.string.nav_album),
    SONG(R.string.nav_song),
    GENRE(R.string.nav_genre),
    PLAYLIST(R.string.nav_playlist)
}

var SharedPreferences.preferScreen: Screen
    get() = Screen.values()[getInt(KEY_PREFER_SCREEN, Screen.ARTIST.ordinal)]
    set(value) = edit().putInt(KEY_PREFER_SCREEN, value.ordinal).apply()

var SharedPreferences.ducking: Boolean
    get() = getBoolean(KEY_DUCKING, false)
    set(value) = edit().putBoolean(KEY_DUCKING, value).apply()

var Context.formatPattern: String
    get() = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(KEY_PATTERN_FORMAT_SHARE_TEXT, null)
            ?: this.getString(R.string.setting_default_sharing_text_pattern)
    set(value) = PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(KEY_PATTERN_FORMAT_SHARE_TEXT, value).apply()

var SharedPreferences.bundleArtwork: Boolean
    get() = getBoolean(KEY_BUNDLE_ARTWORK, true)
    set(value) = edit().putBoolean(KEY_BUNDLE_ARTWORK, value).apply()