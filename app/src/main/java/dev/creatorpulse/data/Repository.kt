package dev.creatorpulse.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Orchestrates the API calls into one [Snapshot], and serves per-experience analytics on demand (cached). */
class Repository(
    @Suppress("unused") private val context: Context,
    val secure: SecureStore,
    val settings: Settings,
    val api: RobloxApi,
    val cache: AnalyticsCache,
) {
    private val refreshMutex = Mutex()

    /** How many experiences get analytics summaries during a refresh (widget ones first). */
    private val summaryBudget = 8

    /** Time frames fetched eagerly; Week/Year are loaded when the Revenue tab asks for them. */
    private val eagerTimeFrames = listOf("Day", "Month")

    suspend fun refreshAll(priorityUniverses: Set<Long> = emptySet()): Snapshot = refreshMutex.withLock {
        val previous = settings.snapshotNow()
        val warnings = ArrayList<String>()
        var user: UserInfo? = null
        var robux: Long? = null
        var groups: List<GroupInfo> = emptyList()
        val sources = LinkedHashMap<Long, String>()
        val devInfo = HashMap<Long, RobloxApi.DevUniverse>()

        if (secure.cookie != null) {
            try {
                user = api.authenticatedUser()
            } catch (e: AuthException) {
                warnings += "Roblox session expired. Sign in again in Settings."
            } catch (e: Exception) {
                warnings += "Could not reach Roblox: ${e.message}"
            }
            if (user != null) {
                runCatching { api.userUniverses() }
                    .onSuccess { list -> list.forEach { sources[it.id] = "user"; devInfo[it.id] = it } }
                    .onFailure { e ->
                        warnings += "Experiences: ${e.message}"
                        previous?.experiences?.filter { it.source == "user" }?.forEach { sources[it.universeId] = "user" }
                    }
                runCatching { api.userGroups(user.id) }
                    .onSuccess { groups = it; settings.initTrackedGroupsIfNeeded(it) }
                    .onFailure { warnings += "Groups: ${it.message}" }
                val tracked = settings.trackedGroups.first()
                for (g in groups.filter { it.id in tracked }) {
                    runCatching { api.groupUniverses(g.id) }
                        .onSuccess { list -> list.forEach { if (it.id !in sources) { sources[it.id] = "group:${g.id}"; devInfo[it.id] = it } } }
                        .onFailure { e ->
                            warnings += "${g.name}: ${e.message}"
                            previous?.experiences?.filter { it.source == "group:${g.id}" }?.forEach { if (it.universeId !in sources) sources[it.universeId] = it.source }
                        }
                }
                runCatching { api.userRobux() }.onSuccess { robux = it }.onFailure { warnings += "Robux balance: ${it.message}" }
            }
        }
        settings.manualUniverses.first().forEach { if (it !in sources) sources[it] = "manual" }

        // Public details for every experience. No fallback on purpose: if this fails we keep the old snapshot.
        val ids = sources.keys.toList()
        val details = api.gameDetails(ids)
        val votes = runCatching { api.votes(ids) }.getOrDefault(emptyMap())
        val prevById = previous?.experiences?.associateBy { it.universeId } ?: emptyMap()
        // Icons rarely change: only fetch for experiences we don't have one for yet (or every ~6h).
        val iconsStale = previous == null || System.currentTimeMillis() - previous.updatedAt > 6 * 3600_000L
        val needIcons = ids.filter { iconsStale || prevById[it]?.iconUrl == null }
        val icons = runCatching { api.icons(needIcons) }.getOrDefault(emptyMap())

        val experiences = ids.map { id ->
            val d = details[id]
            val dev = devInfo[id]
            val prev = prevById[id]
            Experience(
                universeId = id,
                rootPlaceId = d?.rootPlaceId?.takeIf { it != 0L } ?: dev?.rootPlaceId ?: prev?.rootPlaceId ?: 0L,
                name = d?.name?.takeIf { !d.isContentRestricted && it.isNotBlank() } ?: dev?.name ?: prev?.name ?: "Experience $id",
                creatorName = d?.creator?.name ?: prev?.creatorName ?: "",
                creatorType = d?.creator?.type ?: prev?.creatorType ?: "",
                creatorId = d?.creator?.id ?: prev?.creatorId ?: 0L,
                playing = d?.playing ?: 0L,
                visits = d?.visits ?: prev?.visits ?: 0L,
                favorites = d?.favoritedCount ?: prev?.favorites ?: 0L,
                maxPlayers = d?.maxPlayers ?: prev?.maxPlayers ?: 0,
                upVotes = votes[id]?.upVotes ?: prev?.upVotes ?: 0L,
                downVotes = votes[id]?.downVotes ?: prev?.downVotes ?: 0L,
                iconUrl = icons[id] ?: prev?.iconUrl,
                created = d?.created ?: prev?.created,
                updated = d?.updated ?: prev?.updated,
                price = d?.price,
                source = sources[id] ?: "manual",
                isActive = dev?.isActive ?: true,
            )
        }.sortedWith(compareByDescending<Experience> { it.playing }.thenByDescending { it.visits })

        // Revenue totals: Day + Month now, Week/Year when the Revenue tab asks (keeps request count low).
        val userTotals = HashMap<String, Map<String, Long>>()
        previous?.userTotals?.let { userTotals.putAll(it) }
        val groupSnaps = ArrayList<GroupSnapshot>()
        if (user != null) {
            for (tf in eagerTimeFrames) {
                runCatching { api.userTransactionTotals(user.id, tf) }.onSuccess { userTotals[tf] = it }
                    .onFailure { if (tf == "Day") warnings += "Transaction totals: ${it.message}" }
            }
            val tracked = settings.trackedGroups.first()
            val prevGroups = previous?.groups?.associateBy { it.id } ?: emptyMap()
            for (g in groups.filter { it.id in tracked }) {
                val gRobux = runCatching { api.groupRobux(g.id) }.getOrNull() ?: prevGroups[g.id]?.robux
                val totals = HashMap<String, Map<String, Long>>()
                prevGroups[g.id]?.totals?.let { totals.putAll(it) }
                for (tf in eagerTimeFrames) {
                    runCatching { api.groupRevenueSummary(g.id, tf) }.onSuccess { totals[tf] = it }
                        .onFailure { if (tf == "Day") warnings += "${g.name} revenue: ${it.message}" }
                }
                groupSnaps += GroupSnapshot(g.id, g.name, gRobux, totals)
            }
        }

        // Analytics summaries (Open Cloud key). Cached per metric, so this is cheap after the first run.
        val analytics = HashMap<Long, ExperienceAnalytics>()
        previous?.analytics?.let { analytics.putAll(it) }
        if (secure.apiKeys.isNotEmpty() && experiences.isNotEmpty()) {
            val ordered = experiences.sortedByDescending { if (it.universeId in priorityUniverses) Long.MAX_VALUE else it.playing }
                .take(summaryBudget)
            var rateLimited = false
            for (exp in ordered) {
                if (rateLimited) break
                try {
                    analytics[exp.universeId] = analyticsSummary(exp.universeId)
                } catch (e: RateLimitedException) {
                    rateLimited = true; warnings += "Analytics rate limit reached, summaries may be stale"
                } catch (e: NoApiKeyException) {
                    // key does not cover this experience; keep whatever we had
                } catch (e: Exception) {
                    warnings += "${exp.name} analytics: ${e.message}"
                }
            }
        }

        val snap = Snapshot(
            updatedAt = System.currentTimeMillis(),
            user = user ?: previous?.user?.takeIf { secure.cookie != null },
            robux = robux ?: previous?.robux?.takeIf { secure.cookie != null },
            experiences = experiences,
            userTotals = if (user != null || previous?.user != null) userTotals else emptyMap(),
            groups = if (user != null) groupSnaps else previous?.groups.orEmpty(),
            analytics = analytics.filterKeys { it in sources },
            warnings = warnings.distinct(),
        )
        settings.saveSnapshot(snap)
        settings.saveLastError(null)
        snap
    }

    /** Fetches a Week/Year (or any) time frame on demand and merges it into the saved snapshot. */
    suspend fun ensureTotals(scope: RevenueScope, timeFrame: String) {
        val snap = settings.snapshotNow() ?: return
        val user = snap.user ?: return
        val have = when (scope) {
            RevenueScope.User -> snap.userTotals[timeFrame]
            is RevenueScope.Group -> snap.groups.firstOrNull { it.id == scope.id }?.totals?.get(timeFrame)
        }
        if (have != null) return
        val totals = when (scope) {
            RevenueScope.User -> api.userTransactionTotals(user.id, timeFrame)
            is RevenueScope.Group -> api.groupRevenueSummary(scope.id, timeFrame)
        }
        val latest = settings.snapshotNow() ?: snap
        val updated = when (scope) {
            RevenueScope.User -> latest.copy(userTotals = latest.userTotals + (timeFrame to totals))
            is RevenueScope.Group -> latest.copy(groups = latest.groups.map { g -> if (g.id == scope.id) g.copy(totals = g.totals + (timeFrame to totals)) else g })
        }
        settings.saveSnapshot(updated)
    }

    // ---------- analytics ----------

    private suspend fun <T> withAnalyticsKey(universeId: Long, block: suspend (String) -> T): T {
        val keys = secure.apiKeys
        if (keys.isEmpty()) throw NoApiKeyException("Add an Open Cloud API key in Settings to see analytics")
        val preferred = settings.keyForUniverse.first()[universeId]
        val ordered = keys.sortedByDescending { it.id == preferred }
        var last: Exception? = null
        for (k in ordered) {
            try {
                val r = block(k.key)
                if (k.id != preferred) settings.rememberKeyForUniverse(universeId, k.id)
                return r
            } catch (e: RobloxApi.AnalyticsAuthException) {
                last = e
            }
        }
        throw NoApiKeyException("None of your API keys can read analytics for this experience" + (last?.message?.let { " ($it)" } ?: ""))
    }

    /** How long a cached series stays fresh. Hourly/realtime data: 10 min. Daily: 2 h, or a day once the range is in the past. */
    private fun ttlMs(granularity: String, endMs: Long): Long = when (granularity) {
        "OneMinute", "HalfHour", "OneHour" -> 10 * 60_000L
        else -> if (endMs < System.currentTimeMillis() - 2 * TimeUtil.DAY_MS) 24 * 3600_000L else 2 * 3600_000L
    }

    /**
     * One metric series, served from the on-disk cache when fresh. On a rate limit or transient error we
     * fall back to a stale cached copy (with a note) rather than showing nothing.
     */
    suspend fun series(universeId: Long, metric: String, granularity: String, start: Instant, end: Instant, maxAgeMs: Long? = null): Series {
        val key = AnalyticsCache.key(universeId, metric, granularity, start.toEpochMilli(), end.toEpochMilli())
        val cached = cache.get(key)
        val ttl = maxAgeMs ?: ttlMs(granularity, end.toEpochMilli())
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < ttl) {
            return Series(metric, granularity, cached.points, fromCache = true)
        }
        return try {
            val points = withAnalyticsKey(universeId) { k -> api.queryMetric(k, universeId, metric, granularity, start, end) }
            cache.put(key, points)
            Series(metric, granularity, points)
        } catch (e: NoApiKeyException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) Series(metric, granularity, cached.points, fromCache = true, note = "Showing data from ${Fmt.ago(cached.fetchedAt)} (${e.message})")
            else Series(metric, granularity, emptyList(), error = e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun analyticsSummary(universeId: Long): ExperienceAnalytics {
        val todayStart = Instant.ofEpochMilli(TimeUtil.startOfTodayUtcMillis())
        val start = todayStart.minusMillis(8 * TimeUtil.DAY_MS)
        val end = todayStart.plusMillis(TimeUtil.DAY_MS)
        fun failIfLimited(s: Series) { if (s.error != null && s.error.contains("rate limit", ignoreCase = true)) throw RateLimitedException() }
        val dau = series(universeId, "DailyActiveUsers", "OneDay", start, end).also(::failIfLimited)
        val revenue = series(universeId, "DailyRevenue", "OneDay", start, end).also(::failIfLimited)
        val ads = series(universeId, "AdsPublisherReportingTotalRevenueRobux", "OneDay", start, end)
        val spent = series(universeId, "ItemMonetizationRevenue", "OneHour", todayStart, end)
        return ExperienceAnalytics(
            universeId = universeId,
            fetchedAt = System.currentTimeMillis(),
            dau = dau.points,
            revenue = revenue.points,
            spentToday = if (spent.error == null) spent.points.sumOf { it.value } else null,
            adRevenue = ads.points,
        )
    }

    // ---------- ads (Ads Manager backend) ----------

    suspend fun adsOverview(scope: RevenueScope, timePeriod: Int): AdsOverview {
        val groupId = (scope as? RevenueScope.Group)?.id
        val key = when (scope) { RevenueScope.User -> "user"; is RevenueScope.Group -> "group:${scope.id}" }
        val campaigns = api.adCampaigns(timePeriod, groupId)
        val summary = runCatching { api.adAccountSummary(timePeriod, groupId) }.getOrNull()
        val credit = runCatching { api.adCreditBalanceMicro(groupId) }.getOrNull()
        val names = runCatching { api.advertisedUniverses(groupId) }.getOrDefault(emptyMap())
        val statuses = runCatching { api.adCampaignDisplayStatuses(campaigns.map { it.id }, groupId) }.getOrDefault(emptyMap())
        val snapNames = settings.snapshotNow()?.experiences?.associate { it.universeId to it.name } ?: emptyMap()
        return AdsOverview(
            scopeKey = key, timePeriod = timePeriod, fetchedAt = System.currentTimeMillis(),
            campaigns = campaigns.map { c -> statuses[c.id]?.let { c.copy(display_status = it) } ?: c },
            summary = summary, adCreditMicro = credit, universeNames = snapNames + names,
        )
    }

    // ---------- revenue ----------

    suspend fun transactions(scope: RevenueScope, cursor: String?): RobloxApi.TransactionPage {
        val userId = settings.snapshotNow()?.user?.id ?: api.authenticatedUser().id
        return api.transactions(scope, "Sale", cursor, userId)
    }

    // ---------- helpers ----------

    /** Accepts a universe id, a place id, or any roblox.com / create.roblox.com URL. */
    suspend fun resolveUniverseInput(raw: String): Long? {
        val text = raw.trim()
        Regex("experiences/(\\d+)").find(text)?.let { return it.groupValues[1].toLong() }
        Regex("universes/(\\d+)").find(text)?.let { return it.groupValues[1].toLong() }
        Regex("games/(\\d+)").find(text)?.let { return api.universeIdForPlace(it.groupValues[1].toLong()) }
        val n = text.toLongOrNull() ?: return null
        val asUniverse = runCatching { api.gameDetails(listOf(n)) }.getOrNull()?.get(n)
        if (asUniverse != null && asUniverse.id == n) return n
        return runCatching { api.universeIdForPlace(n) }.getOrNull() ?: n
    }
}
