package dev.creatorpulse.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk cache for Analytics Query API results so reopening a screen, re-rendering a widget or a
 * background refresh does not re-spend the ~30 queries/minute budget. Entries carry their fetch time;
 * the caller decides how old is acceptable.
 */
class AnalyticsCache(context: Context) {
    @Serializable
    data class Entry(val points: List<Point>, val fetchedAt: Long)

    @Serializable
    private data class Store(val entries: Map<String, Entry> = emptyMap())

    private val file = File(context.cacheDir, "analytics_cache.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded = false
    private val entries = LinkedHashMap<String, Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private suspend fun ensureLoaded() {
        if (loaded) return
        runCatching {
            if (file.exists()) json.decodeFromString(Store.serializer(), file.readText()).entries.forEach { (k, v) -> entries[k] = v }
        }
        loaded = true
    }

    suspend fun get(key: String): Entry? = mutex.withLock { ensureLoaded(); entries[key] }

    suspend fun put(key: String, points: List<Point>) {
        mutex.withLock {
            ensureLoaded()
            entries.remove(key)
            entries[key] = Entry(points, System.currentTimeMillis())
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
        scheduleSave()
    }

    suspend fun clear() { mutex.withLock { ensureLoaded(); entries.clear() }; scheduleSave() }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            val snapshot = mutex.withLock { Store(entries.toMap()) }
            runCatching { file.writeText(json.encodeToString(Store.serializer(), snapshot)) }
        }
    }

    companion object {
        private const val MAX_ENTRIES = 600
        fun key(universeId: Long, metric: String, granularity: String, startMs: Long, endMs: Long) = "$universeId|$metric|$granularity|$startMs|$endMs"
    }
}
