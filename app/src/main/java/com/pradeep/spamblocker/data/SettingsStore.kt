package com.pradeep.spamblocker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pradeep.spamblocker.domain.BlockAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class Settings(
    val blockAllUnknown: Boolean = false,
    /** Applied to every automatic block and to numbers you block from now on. */
    val action: BlockAction = BlockAction.REJECT,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val unknown = booleanPreferencesKey("block_all_unknown")
        val action = stringPreferencesKey("block_action")
    }

    val flow: Flow<Settings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.unknown] = s.blockAllUnknown
            prefs[Keys.action] = s.action.name
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            blockAllUnknown = this[Keys.unknown] ?: d.blockAllUnknown,
            action = runCatching { BlockAction.valueOf(this[Keys.action] ?: "") }.getOrDefault(d.action),
        )
    }
}
