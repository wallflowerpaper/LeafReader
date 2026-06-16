package com.leaf.reader.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Scoped to this file to avoid conflicts with the library DataStore extension. */
private val Context.settingsStore: DataStore<Preferences>
    by preferencesDataStore(name = "reader_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_DARK_MODE  = booleanPreferencesKey("dark_mode")
        private val KEY_TEXT_SIZE  = intPreferencesKey("text_size")
        private val KEY_FONT       = stringPreferencesKey("font")
    }

    val settingsFlow: Flow<ReaderSettings> = context.settingsStore.data.map { prefs ->
        ReaderSettings(
            isDarkMode = prefs[KEY_DARK_MODE] ?: false,
            textSizeSp = prefs[KEY_TEXT_SIZE]  ?: 19,
            font       = prefs[KEY_FONT]?.let { name ->
                ReaderFont.entries.find { it.name == name }
            } ?: ReaderFont.GEORGIA
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setTextSize(size: Int) {
        context.settingsStore.edit { it[KEY_TEXT_SIZE] = size }
    }

    suspend fun setFont(font: ReaderFont) {
        context.settingsStore.edit { it[KEY_FONT] = font.name }
    }
}
