package com.darney.bubblewatch.data

import android.content.Context
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
        // fenton's LAN IP + the bridge's default port. Editable on the Settings screen.
        const val DEFAULT_BASE_URL = "http://192.168.0.22:8793"
    }
}
