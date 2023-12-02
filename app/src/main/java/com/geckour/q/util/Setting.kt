package com.geckour.q.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class EqualizerParams(
    val levelRange: Pair<Int, Int>, val bands: List<Band>
) {

    fun normalizedLevel(ratio: Float): Int =
        levelRange.first + ((levelRange.second - levelRange.first) * ratio).toInt()

    data class Band(
        val freqRange: Pair<Int, Int>, val centerFreq: Int
    )
}

data class EqualizerSettings(
    val levels: List<Int>
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val isNightModeKey = booleanPreferencesKey("key_night-mode")
private val shouldShowCurrentRemainKey = booleanPreferencesKey("key_show_current_remain")
private val hasAlreadyShownDropboxSyncAlertKey =
    booleanPreferencesKey("key_has_already_shown_dropbox_sync_alert")
private val dropboxCredentialKey = stringPreferencesKey("key_dropbox_credential")

fun Context.getIsNightMode(): Flow<Boolean> = dataStore.data.map {
    it[isNightModeKey] ?: false
}

suspend fun Context.setIsNightMode(isNightMode: Boolean) {
    dataStore.edit { it[isNightModeKey] = isNightMode }
}

fun Context.getShouldShowCurrentRemain(): Flow<Boolean> = dataStore.data.map {
    it[shouldShowCurrentRemainKey] ?: false
}

suspend fun Context.setShouldShowCurrentRemain(shouldShowCurrentRemain: Boolean) {
    dataStore.edit { it[shouldShowCurrentRemainKey] = shouldShowCurrentRemain }
}

fun Context.getHasAlreadyShownDropboxSyncAlert(): Flow<Boolean> = dataStore.data.map {
    it[hasAlreadyShownDropboxSyncAlertKey] ?: false
}

suspend fun Context.setHasAlreadyShownDropboxSyncAlert(shouldShowCurrentRemain: Boolean) {
    dataStore.edit { it[hasAlreadyShownDropboxSyncAlertKey] = shouldShowCurrentRemain }
}

fun Context.getDropboxCredential(): Flow<String?> = dataStore.data.map {
    it[dropboxCredentialKey]
}

suspend fun Context.setDropboxCredential(newCredential: String) {
    dataStore.edit { it[dropboxCredentialKey] = newCredential }
}