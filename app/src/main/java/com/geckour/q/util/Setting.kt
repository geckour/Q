package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.geckour.q.R
import com.google.gson.Gson
import timber.log.Timber

private const val KEY_PREFER_SCREEN = "key_prefer_screen"
private const val KEY_DUCKING = "key_ducking"
private const val KEY_PATTERN_FORMAT_SHARE_TEXT = "key_pattern_format_share_text"
private const val KEY_BUNDLE_ARTWORK = "key_bundle_artwork"
private const val KEY_EQUALIZER_ENABLED = "key_equalizer_enabled"
private const val KEY_EQUALIZER_PARAMS = "key_equalizer_params"
private const val KEY_EQUALIZER_SETTINGS = "key_equalizer_settings"

enum class Screen(val displayNameResId: Int) {
    ARTIST(R.string.nav_artist),
    ALBUM(R.string.nav_album),
    SONG(R.string.nav_song),
    GENRE(R.string.nav_genre),
    PLAYLIST(R.string.nav_playlist)
}

data class EqualizerParams(
        val levelRange: Pair<Int, Int>,
        val bands: List<Band>
) {
    data class Band(
            val freqRange: Pair<Int, Int>,
            val centerFreq: Int
    )
}

data class EqualizerSettings(
        val levels: List<Int>
)

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

var SharedPreferences.equalizerEnabled: Boolean
    get() = getBoolean(KEY_EQUALIZER_ENABLED, false)
    set(value) = edit().putBoolean(KEY_EQUALIZER_ENABLED, value).apply()

var SharedPreferences.equalizerParams: EqualizerParams?
    get() = getString(KEY_EQUALIZER_PARAMS, null)
            ?.let { Gson().fromJson(it, EqualizerParams::class.java) }
    set(value) = edit().putString(KEY_EQUALIZER_PARAMS, value?.let { Gson().toJson(it) }).apply()

var SharedPreferences.equalizerSettings: EqualizerSettings?
    get() = getString(KEY_EQUALIZER_SETTINGS, null)
            ?.let { Gson().fromJson(it, EqualizerSettings::class.java) }
    set(value) = edit().putString(KEY_EQUALIZER_SETTINGS, value?.let { Gson().toJson(it) }).apply()

fun SharedPreferences.setEqualizerLevel(bandNum: Int, level: Int) {
    equalizerSettings?.apply {
        this@setEqualizerLevel.equalizerSettings =
                EqualizerSettings(levels.toMutableList().apply { this[bandNum] = level })
    }
}