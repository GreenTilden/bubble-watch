package com.darney.bubblewatch.data

import android.content.Context
import com.darney.bubblewatch.BuildConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clawatch_settings")

/** Persists the bridge base URL + bearer token via DataStore. */
class SettingsStore(private val context: Context) {

    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyToken = stringPreferencesKey("token")

    val configFlow: Flow<BridgeConfig> = context.dataStore.data.map { prefs ->
        BridgeConfig(
            baseUrl = prefs[keyBaseUrl] ?: DEFAULT_BASE_URL,
            token = prefs[keyToken] ?: "",
        )
    }

    suspend fun setConfig(baseUrl: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[keyBaseUrl] = baseUrl.trim()
            prefs[keyToken] = token.trim()
        }
    }

    companion object {
        // Per-install bridge address, supplied at build time from local.properties
        // (DEFAULT_BRIDGE_URL) — see app/build.gradle.kts. Empty on a fresh clone;
        // the Settings screen is the place to enter it. This was a hard-coded LAN
        // address until 2026-08-07, in a repo anyone can read without logging in.
        val DEFAULT_BASE_URL: String = BuildConfig.DEFAULT_BRIDGE_URL
    }
}
