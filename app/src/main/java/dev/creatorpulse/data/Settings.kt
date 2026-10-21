package dev.creatorpulse.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Non-secret preferences plus the cached Snapshot the widgets render from. */
class Settings(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val trackedGroups: Flow<Set<Long>> = context.dataStore.data.map { p -> (p[TRACKED_GROUPS] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet() }
    val groupsInitialized: Flow<Boolean> = context.dataStore.data.map { it[GROUPS_INIT] == 1 }
    val manualUniverses: Flow<List<Long>> = context.dataStore.data.map { p -> (p[MANUAL_UNIVERSES] ?: emptySet()).mapNotNull { it.toLongOrNull() }.sorted() }
    val refreshMinutes: Flow<Int> = context.dataStore.data.map { it[REFRESH_MINUTES] ?: 30 }
    val showAdMetrics: Flow<Boolean> = context.dataStore.data.map { it[SHOW_ADS] == 1 }
    val updateCheckedAt: Flow<Long> = context.dataStore.data.map { it[UPDATE_CHECKED_AT] ?: 0L }
    val snapshot: Flow<Snapshot?> = context.dataStore.data.map { p -> p[SNAPSHOT]?.let { decode(it) } }
    val lastError: Flow<String?> = context.dataStore.data.map { it[LAST_ERROR] }
    val keyForUniverse: Flow<Map<Long, String>> = context.dataStore.data.map { p ->
        (p[KEY_FOR_UNIVERSE] ?: emptySet()).mapNotNull { s -> s.split('=', limit = 2).takeIf { it.size == 2 }?.let { (u, k) -> u.toLongOrNull()?.let { it to k } } }.toMap()
    }
    val knownGroups: Flow<List<GroupInfo>> = context.dataStore.data.map { p ->
        p[KNOWN_GROUPS]?.let { runCatching { json.decodeFromString<List<GroupInfo>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun snapshotNow(): Snapshot? = snapshot.first()

    suspend fun saveSnapshot(s: Snapshot) = context.dataStore.edit { it[SNAPSHOT] = json.encodeToString(Snapshot.serializer(), s) }
    suspend fun saveLastError(msg: String?) = context.dataStore.edit { if (msg == null) it.remove(LAST_ERROR) else it[LAST_ERROR] = msg }

    suspend fun setGroupTracked(id: Long, tracked: Boolean) = context.dataStore.edit { p ->
        val cur = (p[TRACKED_GROUPS] ?: emptySet()).toMutableSet()
        if (tracked) cur += id.toString() else cur -= id.toString()
        p[TRACKED_GROUPS] = cur
        p[GROUPS_INIT] = 1
    }

    /** First time we learn the user's groups, auto-track the ones they own. */
    suspend fun initTrackedGroupsIfNeeded(groups: List<GroupInfo>) = context.dataStore.edit { p ->
        p[KNOWN_GROUPS] = json.encodeToString(groups)
        if (p[GROUPS_INIT] != 1) {
            p[TRACKED_GROUPS] = groups.filter { it.rank >= 255 }.map { it.id.toString() }.toSet()
            p[GROUPS_INIT] = 1
        }
    }

    suspend fun addManualUniverse(id: Long) = context.dataStore.edit { p -> p[MANUAL_UNIVERSES] = (p[MANUAL_UNIVERSES] ?: emptySet()) + id.toString() }
    suspend fun removeManualUniverse(id: Long) = context.dataStore.edit { p -> p[MANUAL_UNIVERSES] = (p[MANUAL_UNIVERSES] ?: emptySet()) - id.toString() }
    suspend fun setRefreshMinutes(m: Int) = context.dataStore.edit { it[REFRESH_MINUTES] = m }
    suspend fun setShowAdMetrics(on: Boolean) = context.dataStore.edit { it[SHOW_ADS] = if (on) 1 else 0 }
    suspend fun setUpdateCheckedAt(t: Long) = context.dataStore.edit { it[UPDATE_CHECKED_AT] = t }

    suspend fun rememberKeyForUniverse(universeId: Long, keyId: String) = context.dataStore.edit { p ->
        val cur = (p[KEY_FOR_UNIVERSE] ?: emptySet()).filterNot { it.startsWith("$universeId=") }.toMutableSet()
        cur += "$universeId=$keyId"
        p[KEY_FOR_UNIVERSE] = cur
    }
    suspend fun forgetKeyMappings(keyId: String) = context.dataStore.edit { p ->
        p[KEY_FOR_UNIVERSE] = (p[KEY_FOR_UNIVERSE] ?: emptySet()).filterNot { it.endsWith("=$keyId") }.toSet()
    }

    suspend fun clearAccountData() = context.dataStore.edit { p ->
        p.remove(SNAPSHOT); p.remove(TRACKED_GROUPS); p.remove(GROUPS_INIT); p.remove(KNOWN_GROUPS); p.remove(LAST_ERROR)
    }

    private fun decode(s: String): Snapshot? = runCatching { json.decodeFromString(Snapshot.serializer(), s) }.getOrNull()

    companion object {
        private val TRACKED_GROUPS = stringSetPreferencesKey("tracked_groups")
        private val GROUPS_INIT = intPreferencesKey("groups_init")
        private val KNOWN_GROUPS = stringPreferencesKey("known_groups")
        private val MANUAL_UNIVERSES = stringSetPreferencesKey("manual_universes")
        private val REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
        private val SHOW_ADS = intPreferencesKey("show_ad_metrics")
        private val UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
        private val SNAPSHOT = stringPreferencesKey("snapshot")
        private val LAST_ERROR = stringPreferencesKey("last_error")
        private val KEY_FOR_UNIVERSE = stringSetPreferencesKey("key_for_universe")
    }
}
