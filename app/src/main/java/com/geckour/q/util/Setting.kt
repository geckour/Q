package com.geckour.q.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.geckour.q.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Pref<T>(protected val key: kotlin.String) : ReadWriteProperty<SharedPreferences, T> {

    class Boolean(key: kotlin.String, private val defaultValue: kotlin.Boolean) : Pref<kotlin.Boolean>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): kotlin.Boolean =
                thisRef.getBoolean(key, defaultValue)

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: kotlin.Boolean) {
            thisRef.edit().putBoolean(key, value).apply()
        }
    }

    class Int(key: kotlin.String, private val defaultValue: kotlin.Int) : Pref<kotlin.Int>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): kotlin.Int =
                thisRef.getInt(key, defaultValue)

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: kotlin.Int) {
            thisRef.edit().putInt(key, value).apply()
        }
    }

    class Enum<T : Enum.Content<*>>(key: kotlin.String, private val defaultValue: T) : Pref<T>(key) {

        sealed class Content<T>(val name: kotlin.String, val value: T) {

            class AppTheme(name: kotlin.String, value: Data) : Content<AppTheme.Data>(name, value) {
                data class Data(val stringResId: kotlin.Int, val styleResId: kotlin.Int)
            }

            class Screen(name: kotlin.String, value: Data) : Content<Screen.Data>(name, value) {
                data class Data(val stringResId: kotlin.Int, val navId: kotlin.Int)
            }
        }

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): T =
                when (defaultValue) {
                    is Content.AppTheme ->
                        appThemes[thisRef.getInt(key, appThemes.indexOf<Content.AppTheme>(defaultValue as Content.AppTheme))] as T
                    is Content.Screen ->
                        screens[thisRef.getInt(key, screens.indexOf<Content.Screen>(defaultValue as Content.Screen))] as T
                    else -> throw IllegalStateException()
                }

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: T) {
            when (defaultValue) {
                is Content.AppTheme ->
                    thisRef.edit().putInt(key, appThemes.indexOf<Content.AppTheme>(value as Content.AppTheme)).apply()
                is Content.Screen ->
                    thisRef.edit().putInt(key, screens.indexOf<Content.Screen>(value as Content.Screen)).apply()
                else -> throw IllegalStateException()
            }
        }

        companion object {
            internal val appThemes: List<Content.AppTheme> = listOf(
                    Content.AppTheme("LIGHT", Content.AppTheme.Data(R.string.app_theme_light, R.style.AppTheme)),
                    Content.AppTheme("DARK", Content.AppTheme.Data(R.string.app_theme_dark, R.style.AppTheme_Dark))
            )

            internal val screens: List<Content.Screen> = listOf(
                    Content.Screen("ARTIST", Content.Screen.Data(R.string.nav_artist, R.id.nav_artist)),
                    Content.Screen("ALBUM", Content.Screen.Data(R.string.nav_album, R.id.nav_album)),
                    Content.Screen("SONG", Content.Screen.Data(R.string.nav_song, R.id.nav_song)),
                    Content.Screen("GENRE", Content.Screen.Data(R.string.nav_genre, R.id.nav_genre)),
                    Content.Screen("PLAYLIST", Content.Screen.Data(R.string.nav_playlist, R.id.nav_playlist))
            )
        }
    }
}

sealed class NullablePref<T>(protected val key: kotlin.String) : ReadWriteProperty<SharedPreferences, T?> {

    class String(key: kotlin.String, private val defaultValue: kotlin.String?) : NullablePref<kotlin.String>(key) {

        override fun getValue(thisRef: SharedPreferences, property: KProperty<*>): kotlin.String? =
                thisRef.getString(key, defaultValue)

        override fun setValue(thisRef: SharedPreferences, property: KProperty<*>, value: kotlin.String?) {
            thisRef.edit().putString(key, value).apply()
        }
    }

    class Json<T>(key: kotlin.String, private val classOfValue: Class<T>) : NullablePref<T>(key) {

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

var SharedPreferences.appTheme by Pref.Enum("key_app_theme", Pref.Enum.appThemes[0])
var SharedPreferences.preferScreen by Pref.Enum("key_prefer_screen", Pref.Enum.screens[0])
var SharedPreferences.ducking by Pref.Boolean("key_ducking", false)
var SharedPreferences.patternFormatShareText by NullablePref.String("key_pattern_format_share_text", "#NowPlaying TI - AR (AL)")
var SharedPreferences.bundleArtwork by Pref.Boolean("key_bundle_artwork", true)
var SharedPreferences.showArtworkOnLockScreen by Pref.Boolean("key_show_artwork_on_lock_screen", false)
var SharedPreferences.equalizerEnabled by Pref.Boolean("key_equalizer_enabled", false)
var SharedPreferences.equalizerParams by NullablePref.Json("key_equalizer_params", EqualizerParams::class.java)
var SharedPreferences.equalizerSettings by NullablePref.Json("key_equalizer_settings", EqualizerSettings::class.java)
var SharedPreferences.ignoringEnabled by Pref.Boolean("key_enabled_ignoring", true)

fun SharedPreferences.setEqualizerLevel(bandNum: Int, level: Int) {
    equalizerSettings?.apply {
        equalizerSettings = EqualizerSettings(levels.toMutableList().apply { this[bandNum] = level })
    }
}

var Context.formatPattern: String
    get() = PreferenceManager.getDefaultSharedPreferences(this)
            .patternFormatShareText ?: this.getString(R.string.setting_default_sharing_text_pattern)
    set(value) {
        PreferenceManager.getDefaultSharedPreferences(this).patternFormatShareText = value
    }