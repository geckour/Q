package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.geckour.q.R
import com.google.gson.Gson
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Pref<T>(protected val key: String) : ReadWriteProperty<SharedPreferences, T> {

    class PrefBoolean(key: String, private val defaultValue: Boolean) : Pref<Boolean>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): Boolean =
                thisRef.getBoolean(key, defaultValue)

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: Boolean) {
            thisRef.edit().putBoolean(key, value).apply()
        }
    }

    class PrefInt(key: String, private val defaultValue: Int) : Pref<Int>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): Int =
                thisRef.getInt(key, defaultValue)

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: Int) {
            thisRef.edit().putInt(key, value).apply()
        }
    }

    class PrefEnum<T : PrefEnum.Content<*>>(key: String, private val defaultValue: T) :
            Pref<T>(key) {

        sealed class Content<T>(val name: String, val value: T) {

            class Screen(name: String, value: Data) : Content<Screen.Data>(name, value) {
                data class Data(@StringRes val stringResId: Int, @IdRes val navId: Int)
            }
        }

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): T =
                when (defaultValue) {
                    is Content.Screen -> screens[thisRef.getInt(
                            key, screens.indexOf<Content.Screen>(
                            defaultValue as Content.Screen
                    )
                    )] as T
                    else -> throw IllegalStateException()
                }

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: T) {
            when (defaultValue) {
                is Content.Screen -> thisRef.edit().putInt(
                        key, screens.indexOf<Content.Screen>(value as Content.Screen)
                ).apply()
                else -> throw IllegalStateException()
            }
        }

        companion object {
            internal val screens: List<Content.Screen> = listOf(
                    Content.Screen("ARTIST", Content.Screen.Data(R.string.nav_artist, R.id.nav_artist)),
                    Content.Screen("ALBUM", Content.Screen.Data(R.string.nav_album, R.id.nav_album)),
                    Content.Screen("SONG", Content.Screen.Data(R.string.nav_song, R.id.nav_song)),
                    Content.Screen("GENRE", Content.Screen.Data(R.string.nav_genre, R.id.nav_genre)),
                    Content.Screen(
                            "PLAYLIST", Content.Screen.Data(R.string.nav_playlist, R.id.nav_playlist)
                    )
            )
        }
    }
}

sealed class NullablePref<T>(protected val key: String) : ReadWriteProperty<SharedPreferences, T?> {

    class PrefString(key: String, private val defaultValue: String?) : NullablePref<String>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): String? =
                thisRef.getString(key, defaultValue)

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: String?) {
            thisRef.edit().putString(key, value).apply()
        }
    }

    class PrefJson<T>(key: String, private val classOfValue: Class<T>) : NullablePref<T>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): T? =
                thisRef.getString(key, null)?.let {
                    Gson().fromJson<T>(it, classOfValue)
                }

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: T?) {
            if (value == null) thisRef.edit().remove(key).apply()
            else thisRef.edit().putString(key, Gson().toJson(value)).apply()
        }
    }
}

data class EqualizerParams(
        val levelRange: Pair<Int, Int>, val bands: List<Band>
) {

    data class Band(
            val freqRange: Pair<Int, Int>, val centerFreq: Int
    )
}

data class EqualizerSettings(
        val levels: List<Int>
)

var SharedPreferences.isNightMode by Pref.PrefBoolean("key_night-mode", false)
var SharedPreferences.preferScreen by Pref.PrefEnum("key_prefer_screen", Pref.PrefEnum.screens[0])
var SharedPreferences.ducking by Pref.PrefBoolean("key_ducking", false)
var SharedPreferences.patternFormatShareText by NullablePref.PrefString(
        "key_pattern_format_share_text", "#NowPlaying TI - AR (AL)"
)
var SharedPreferences.bundleArtwork by Pref.PrefBoolean("key_bundle_artwork", true)
var SharedPreferences.showArtworkOnLockScreen by Pref.PrefBoolean(
        "key_show_artwork_on_lock_screen", false
)
var SharedPreferences.equalizerEnabled by Pref.PrefBoolean("key_equalizer_enabled", false)
var SharedPreferences.equalizerParams by NullablePref.PrefJson(
        "key_equalizer_params", EqualizerParams::class.java
)
var SharedPreferences.equalizerSettings by NullablePref.PrefJson(
        "key_equalizer_settings", EqualizerSettings::class.java
)
var SharedPreferences.ignoringEnabled by Pref.PrefBoolean("key_enabled_ignoring", true)

fun SharedPreferences.setEqualizerLevel(bandNum: Int, level: Int) {
    equalizerSettings?.apply {
        equalizerSettings =
                EqualizerSettings(levels.toMutableList().apply { this[bandNum] = level })
    }
}

var Context.formatPattern: String
    get() = PreferenceManager.getDefaultSharedPreferences(this).patternFormatShareText
            ?: this.getString(R.string.setting_default_sharing_text_pattern)
    set(value) {
        PreferenceManager.getDefaultSharedPreferences(this).patternFormatShareText = value
    }