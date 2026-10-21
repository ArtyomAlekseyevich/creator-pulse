package dev.creatorpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.creatorpulse.App
import dev.creatorpulse.data.GroupInfo
import dev.creatorpulse.data.NoApiKeyException
import dev.creatorpulse.data.RevenueScope
import dev.creatorpulse.data.Series
import dev.creatorpulse.data.Snapshot
import dev.creatorpulse.data.ThemeSpec
import dev.creatorpulse.data.TimeUtil
import dev.creatorpulse.data.Transaction
import dev.creatorpulse.data.UpdateInfo
import dev.creatorpulse.widget.RefreshScheduler
import dev.creatorpulse.widget.WidgetData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant

enum class ChartKind { LINE, BAR }
enum class ValueKind { COUNT, ROBUX, MINUTES, PERCENT }

data class MetricSpec(val metric: String, val title: String, val kind: ChartKind, val value: ValueKind, val granularity: String = "OneDay", val realtime: Boolean = false)

val DETAIL_METRICS = listOf(
    MetricSpec("PeakConcurrentPlayers", "Concurrent players, last 24h", ChartKind.LINE, ValueKind.COUNT, "OneHour", realtime = true),
    MetricSpec("ItemMonetizationRevenue", "Robux spent, last 24h (realtime)", ChartKind.BAR, ValueKind.ROBUX, "OneHour", realtime = true),
    MetricSpec("DailyActiveUsers", "Daily active users", ChartKind.LINE, ValueKind.COUNT),
    MetricSpec("DailyRevenue", "Revenue", ChartKind.BAR, ValueKind.ROBUX),
    MetricSpec("Visits", "Visits", ChartKind.BAR, ValueKind.COUNT),
    MetricSpec("PayingUsers", "Paying users", ChartKind.BAR, ValueKind.COUNT),
    MetricSpec("AveragePlayTimeMinutesPerDAU", "Avg playtime per DAU", ChartKind.LINE, ValueKind.MINUTES),
    MetricSpec("AverageSessionLengthMinutes", "Avg session length", ChartKind.LINE, ValueKind.MINUTES),
    MetricSpec("ForwardD1Retention", "Day 1 retention", ChartKind.LINE, ValueKind.PERCENT),
    MetricSpec("ForwardD7Retention", "Day 7 retention", ChartKind.LINE, ValueKind.PERCENT),
)

/** Publisher-side advertising metrics (immersive / rewarded video ads shown inside the experience). */
val AD_METRICS = listOf(
    MetricSpec("AdsPublisherReportingTotalRevenueRobux", "Ad revenue (all formats)", ChartKind.BAR, ValueKind.ROBUX),
    MetricSpec("AdsPublisherReportingTotalImpressions", "Ad impressions", ChartKind.BAR, ValueKind.COUNT),
    MetricSpec("AdsPublisherReportingVideo2DRevenueRobux", "Rewarded video earnings", ChartKind.BAR, ValueKind.ROBUX),
    MetricSpec("AdsPublisherReportingVideo2DImpressions", "Rewarded video impressions", ChartKind.BAR, ValueKind.COUNT),
    MetricSpec("AdsPublisherReportingVideo2DEpmNoUnvalidatedPc", "Rewarded video EPM (Robux per 1000)", ChartKind.LINE, ValueKind.ROBUX),
    MetricSpec("AdsPublisherReportingVideo2DDailyUniqueViewer", "Rewarded video unique viewers", ChartKind.LINE, ValueKind.COUNT),
)

data class DetailState(
    val universeId: Long,
    val days: Int,
    val withAds: Boolean = false,
    val series: Map<String, Series> = emptyMap(),
    val loading: Boolean = false,
    val noKeyMessage: String? = null,
)

data class UpdateState(
    val checking: Boolean = false,
    val available: UpdateInfo? = null,
    val upToDate: Boolean = false,
    val downloading: Int? = null,
    val error: String? = null,
    val checkedAt: Long = 0L,
)

data class TxState(val items: List<Transaction> = emptyList(), val cursor: String? = null, val loading: Boolean = false, val error: String? = null, val loaded: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = App.get(app)
    private val repo = application.repo
    private val settings = application.settings
    private val secure = application.secure
    private val themeStore = application.themeStore
    private val updater = application.updater

    val snapshot: StateFlow<Snapshot?> = settings.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val cookie: StateFlow<String?> = secure.cookieFlow
    val sessionExpired: StateFlow<Boolean> = secure.sessionExpired
    val apiKeys = secure.apiKeysFlow
    val trackedGroups: StateFlow<Set<Long>> = settings.trackedGroups.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val knownGroups: StateFlow<List<GroupInfo>> = settings.knownGroups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val manualUniverses: StateFlow<List<Long>> = settings.manualUniverses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val refreshMinutes: StateFlow<Int> = settings.refreshMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    val showAdMetrics: StateFlow<Boolean> = settings.showAdMetrics.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private var refreshJob: Job? = null
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val snap = repo.refreshAll(WidgetData.widgetUniverseIds(getApplication()))
                WidgetData.updateAllWidgets(getApplication())
                snap.warnings.firstOrNull()?.let { _message.value = it }
            } catch (e: Exception) {
                _message.value = e.message ?: "Refresh failed"
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Auto refresh when the app opens and the data is older than 5 minutes (or missing). */
    fun refreshIfStale() {
        val s = snapshot.value
        if (s == null || System.currentTimeMillis() - s.updatedAt > 5 * 60_000L) refresh()
    }

    // ---------- experience detail ----------

    private val _details = MutableStateFlow<Map<Long, DetailState>>(emptyMap())
    val details: StateFlow<Map<Long, DetailState>> = _details.asStateFlow()
    private val detailJobs = HashMap<Long, Job>()
    private val analyticsGate = Semaphore(2)

    fun loadDetail(universeId: Long, days: Int, force: Boolean = false, withAds: Boolean = showAdMetrics.value) {
        val cur = _details.value[universeId]
        val same = cur != null && cur.days == days && cur.withAds == withAds
        if (!force && same && (cur.loading || cur.series.isNotEmpty() || cur.noKeyMessage != null)) return
        // Switching ads on keeps the core charts we already have and only loads the ad ones.
        val keep = if (!force && cur != null && cur.days == days) cur.series else emptyMap()
        detailJobs[universeId]?.cancel()
        _details.update { it + (universeId to DetailState(universeId, days, withAds, series = keep, loading = true)) }
        detailJobs[universeId] = viewModelScope.launch {
            val todayStart = Instant.ofEpochMilli(TimeUtil.startOfTodayUtcMillis())
            // Round "now" to 10 minutes so realtime queries hit the cache instead of a new query per open.
            val now = Instant.ofEpochMilli(System.currentTimeMillis() / 600_000L * 600_000L)
            val specs = (DETAIL_METRICS + if (withAds) AD_METRICS else emptyList()).filter { force || it.metric !in keep }
            val jobs = specs.map { spec ->
                launch {
                    analyticsGate.withPermit {
                        val (start, end) = if (spec.realtime) {
                            now.minusMillis(TimeUtil.DAY_MS) to now
                        } else {
                            todayStart.minusMillis(days.toLong() * TimeUtil.DAY_MS) to todayStart.plusMillis(TimeUtil.DAY_MS)
                        }
                        try {
                            val s = repo.series(universeId, spec.metric, spec.granularity, start, end, maxAgeMs = if (force) 0L else null)
                            _details.update { m -> m[universeId]?.let { d -> m + (universeId to d.copy(series = d.series + (spec.metric to s))) } ?: m }
                        } catch (e: NoApiKeyException) {
                            _details.update { m -> m[universeId]?.let { d -> m + (universeId to d.copy(noKeyMessage = e.message)) } ?: m }
                            detailJobs[universeId]?.cancel()
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            _details.update { m -> m[universeId]?.let { d -> m + (universeId to d.copy(loading = false)) } ?: m }
        }.also { job -> job.invokeOnCompletion { _details.update { m -> m[universeId]?.let { d -> m + (universeId to d.copy(loading = false)) } ?: m } } }
    }

    fun setShowAdMetrics(on: Boolean) = viewModelScope.launch { settings.setShowAdMetrics(on) }

    // ---------- revenue ----------

    private val _tx = MutableStateFlow<Map<String, TxState>>(emptyMap())
    val transactions: StateFlow<Map<String, TxState>> = _tx.asStateFlow()

    fun scopeKey(scope: RevenueScope) = when (scope) { RevenueScope.User -> "user"; is RevenueScope.Group -> "group:${scope.id}" }

    fun loadTransactions(scope: RevenueScope, more: Boolean = false) {
        val key = scopeKey(scope)
        val cur = _tx.value[key] ?: TxState()
        if (cur.loading) return
        if (!more && cur.loaded) return
        if (more && cur.cursor == null) return
        _tx.update { it + (key to cur.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val page = repo.transactions(scope, if (more) cur.cursor else null)
                _tx.update { it + (key to TxState(items = if (more) cur.items + page.items else page.items, cursor = page.nextCursor, loaded = true)) }
            } catch (e: Exception) {
                _tx.update { it + (key to cur.copy(loading = false, error = e.message ?: "Failed", loaded = true)) }
            }
        }
    }

    /** Week/Year totals are fetched lazily to keep the request count down. */
    fun ensureTotals(scope: RevenueScope, timeFrame: String) = viewModelScope.launch {
        runCatching { repo.ensureTotals(scope, timeFrame) }.onFailure { _message.value = "Totals: ${it.message}" }
    }

    fun reloadTransactions(scope: RevenueScope) {
        _tx.update { it - scopeKey(scope) }
        loadTransactions(scope)
    }

    // ---------- updates ----------

    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    val currentVersion: String get() = updater.currentVersion
    val releasesUrl: String get() = updater.releasesUrl

    fun checkForUpdates(manual: Boolean = false) {
        if (_update.value.checking) return
        viewModelScope.launch {
            if (!manual) {
                val last = settings.updateCheckedAt.first()
                if (System.currentTimeMillis() - last < 24 * 3600_000L) return@launch
            }
            _update.update { it.copy(checking = true, error = null) }
            try {
                val info = updater.check()
                settings.setUpdateCheckedAt(System.currentTimeMillis())
                _update.update { it.copy(checking = false, available = info, upToDate = info == null, checkedAt = System.currentTimeMillis()) }
            } catch (e: Exception) {
                _update.update { it.copy(checking = false, error = e.message ?: "Update check failed") }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val info = _update.value.available ?: return
        if (_update.value.downloading != null) return
        viewModelScope.launch {
            _update.update { it.copy(downloading = 0, error = null) }
            try {
                val file = updater.download(info) { p -> _update.update { it.copy(downloading = p) } }
                _update.update { it.copy(downloading = null) }
                updater.install(file)
            } catch (e: Exception) {
                _update.update { it.copy(downloading = null, error = e.message ?: "Download failed") }
            }
        }
    }

    fun dismissUpdate() = _update.update { it.copy(available = null) }

    // ---------- theme ----------

    val theme: StateFlow<ThemeSpec> = themeStore.spec
    private var widgetThemeJob: Job? = null

    fun updateTheme(transform: (ThemeSpec) -> ThemeSpec) {
        themeStore.update(transform)
        // Widgets re-render with the new colors; debounce so slider drags don't spam them.
        widgetThemeJob?.cancel()
        widgetThemeJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            WidgetData.updateAllWidgets(getApplication())
        }
    }

    // ---------- settings ----------

    fun setCookie(cookie: String?) {
        viewModelScope.launch {
            secure.setCookie(cookie)
            if (cookie == null) {
                settings.clearAccountData()
                _tx.value = emptyMap()
                WidgetData.updateAllWidgets(getApplication())
            } else {
                _message.value = "Signed in. Loading your experiences…"
                refresh()
            }
        }
    }

    fun setGroupTracked(id: Long, tracked: Boolean) = viewModelScope.launch { settings.setGroupTracked(id, tracked) }

    fun addExperience(input: String, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val id = repo.resolveUniverseInput(input)
            if (id == null) onResult("Enter a universe id, a place id or a Roblox link") else {
                settings.addManualUniverse(id); onResult("Added experience $id"); refresh()
            }
        } catch (e: Exception) { onResult(e.message ?: "Could not resolve that") }
    }

    fun removeExperience(id: Long) = viewModelScope.launch { settings.removeManualUniverse(id); refresh() }

    fun addApiKey(label: String, key: String) {
        secure.addApiKey(label, key)
        _details.value = emptyMap()
        refresh()
    }

    fun removeApiKey(id: String) = viewModelScope.launch {
        secure.removeApiKey(id); settings.forgetKeyMappings(id); _details.value = emptyMap()
    }

    fun setRefreshMinutes(m: Int) = viewModelScope.launch {
        settings.setRefreshMinutes(m); RefreshScheduler.schedule(getApplication(), m, replace = true)
    }
}
