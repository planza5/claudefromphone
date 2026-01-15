package com.example.runpodmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
        }
    }
}
