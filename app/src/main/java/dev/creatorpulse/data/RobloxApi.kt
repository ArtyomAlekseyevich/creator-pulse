package dev.creatorpulse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String, val url: String = "") : IOException(message)
class AuthException(message: String = "Not signed in or session expired") : IOException(message)
class NoApiKeyException(message: String = "No Open Cloud API key can read analytics for this experience") : IOException(message)
class RateLimitedException(val retryAfterMs: Long = 60_000L) : IOException("Roblox rate limit hit, retry in ${(retryAfterMs / 1000).coerceAtLeast(1)}s")

/**
 * Thin client over the Roblox web APIs the Creator Hub itself uses (cookie auth) plus the
 * official Open Cloud Analytics Query API (API key auth).
 */
class RobloxApi(private val secure: SecureStore) {

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    /** Analytics Query API allows ~30 POSTs/min per key owner; stay comfortably under it, per key. */
    private val analyticsBuckets = java.util.concurrent.ConcurrentHashMap<String, RateBucket>()
    private fun bucketFor(apiKey: String) = analyticsBuckets.getOrPut(apiKey.hashCode().toString()) { RateBucket(ANALYTICS_PER_MINUTE, 60_000L) }
    /** Cookie/web endpoints: generous, but widgets + app + worker can pile up; cap bursts. */
    private val webBucket = RateBucket(WEB_PER_MINUTE, 60_000L)

    private fun retryAfterMs(resp: okhttp3.Response, default: Long): Long =
        resp.header("Retry-After")?.trim()?.toLongOrNull()?.let { it * 1000L } ?: default

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val b = original.newBuilder()
                .header("User-Agent", UA)
                .header("Accept", "application/json")
            val cookie = secure.cookie
            if (cookie != null && original.url.host.endsWith("roblox.com") && original.header("x-api-key") == null) {
                b.header("Cookie", ".ROBLOSECURITY=$cookie")
            }
            chain.proceed(b.build())
        }
        .build()

    // ---------- low level ----------

    private suspend fun get(url: String, requiresCookie: Boolean = false): String = withContext(Dispatchers.IO) {
        if (requiresCookie && secure.cookie == null) throw AuthException()
        var attempt = 0
        while (true) {
            webBucket.acquire()
            val req = Request.Builder().url(url).get().build()
            val result = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> body
                    requiresCookie && resp.code == 401 -> { secure.markSessionExpired(); throw AuthException() }
                    resp.code == 429 -> {
                        val wait = retryAfterMs(resp, 5_000L)
                        webBucket.cooldown(wait.coerceAtMost(20_000L))
                        if (attempt == 0 && wait <= 20_000L) null else throw RateLimitedException(wait)
                    }
                    else -> throw ApiException(resp.code, errorMessage(resp.code, body), url)
                }
            }
            if (result != null) return@withContext result
            attempt++
            kotlinx.coroutines.delay(webBucket.cooldownRemainingMs.coerceAtLeast(1_000L))
        }
        @Suppress("UNREACHABLE_CODE") ""
    }

    @Volatile private var csrfToken: String? = null

    /** POST with Roblox's CSRF dance: a 403 carrying x-csrf-token means "retry with this token". */
    private suspend fun postJson(url: String, body: String, requiresCookie: Boolean = true): String = withContext(Dispatchers.IO) {
        if (requiresCookie && secure.cookie == null) throw AuthException()
        var attempt = 0
        while (true) {
            webBucket.acquire()
            val rb = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType()))
            csrfToken?.let { rb.header("x-csrf-token", it) }
            val result = client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val newToken = resp.header("x-csrf-token")
                when {
                    resp.isSuccessful -> text
                    resp.code == 403 && newToken != null && attempt == 0 -> { csrfToken = newToken; null }
                    requiresCookie && resp.code == 401 -> { secure.markSessionExpired(); throw AuthException() }
                    resp.code == 429 -> throw RateLimitedException(retryAfterMs(resp, 5_000L))
                    else -> throw ApiException(resp.code, errorMessage(resp.code, text), url)
                }
            }
            if (result != null) return@withContext result
            attempt++
        }
        @Suppress("UNREACHABLE_CODE") ""
    }

    private fun errorMessage(code: Int, body: String): String {
        val fromBody = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["errors"]?.let { errs -> errs.toString().let { Regex("\"message\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) } }
                ?: obj["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return fromBody ?: when (code) {
            401 -> "Unauthorized"
            403 -> "Forbidden (no permission)"
            404 -> "Not found"
            else -> "HTTP $code"
        }
    }

    private inline fun <reified T> parse(body: String): T = json.decodeFromString(body)

    // ---------- public, no auth ----------

    @Serializable private data class GamesResponse(val data: List<GameDetail> = emptyList())
    @Serializable data class GameCreator(val id: Long = 0, val name: String = "", val type: String = "")
    @Serializable data class GameDetail(
        val id: Long = 0, val rootPlaceId: Long = 0, val name: String = "", val creator: GameCreator? = null,
        val price: Long? = null, val playing: Long = 0, val visits: Long = 0, val maxPlayers: Int = 0,
        val created: String? = null, val updated: String? = null, val favoritedCount: Long = 0,
        val isContentRestricted: Boolean = false,
    )

    suspend fun gameDetails(universeIds: List<Long>): Map<Long, GameDetail> {
        if (universeIds.isEmpty()) return emptyMap()
        val out = HashMap<Long, GameDetail>()
        universeIds.chunked(50).forEach { chunk ->
            val body = get("https://games.roblox.com/v1/games?universeIds=${chunk.joinToString(",")}")
            parse<GamesResponse>(body).data.forEach { out[it.id] = it }
        }
        return out
    }

    @Serializable private data class VotesResponse(val data: List<Vote> = emptyList())
    @Serializable data class Vote(val id: Long = 0, val upVotes: Long = 0, val downVotes: Long = 0)

    suspend fun votes(universeIds: List<Long>): Map<Long, Vote> {
        if (universeIds.isEmpty()) return emptyMap()
        val out = HashMap<Long, Vote>()
        universeIds.chunked(50).forEach { chunk ->
            val body = get("https://games.roblox.com/v1/games/votes?universeIds=${chunk.joinToString(",")}")
            parse<VotesResponse>(body).data.forEach { out[it.id] = it }
        }
        return out
    }

    @Serializable private data class ThumbResponse(val data: List<Thumb> = emptyList())
    @Serializable private data class Thumb(val targetId: Long = 0, val state: String = "", val imageUrl: String? = null)

    suspend fun icons(universeIds: List<Long>, size: String = "150x150"): Map<Long, String> {
        if (universeIds.isEmpty()) return emptyMap()
        val out = HashMap<Long, String>()
        universeIds.chunked(50).forEach { chunk ->
            val body = get("https://thumbnails.roblox.com/v1/games/icons?universeIds=${chunk.joinToString(",")}&size=$size&format=Png")
            parse<ThumbResponse>(body).data.forEach { if (it.state == "Completed" && it.imageUrl != null) out[it.targetId] = it.imageUrl }
        }
        return out
    }

    @Serializable private data class UniverseOfPlace(val universeId: Long? = null)
    suspend fun universeIdForPlace(placeId: Long): Long? =
        parse<UniverseOfPlace>(get("https://apis.roblox.com/universes/v1/places/$placeId/universe")).universeId

    // ---------- cookie auth ----------

    suspend fun authenticatedUser(): UserInfo = parse(get("https://users.roblox.com/v1/users/authenticated", requiresCookie = true))

    @Serializable private data class Currency(val robux: Long = 0)
    suspend fun userRobux(): Long = parse<Currency>(get("https://economy.roblox.com/v1/user/currency", requiresCookie = true)).robux
    suspend fun groupRobux(groupId: Long): Long = parse<Currency>(get("https://economy.roblox.com/v1/groups/$groupId/currency", requiresCookie = true)).robux

    @Serializable data class DevUniverse(val id: Long = 0, val name: String = "", val rootPlaceId: Long = 0, val isArchived: Boolean = false, val isActive: Boolean = true, val privacyType: String? = null)
    @Serializable private data class DevUniversePage(val nextPageCursor: String? = null, val data: List<DevUniverse> = emptyList())

    private suspend fun universePages(base: String): List<DevUniverse> {
        val out = ArrayList<DevUniverse>()
        var cursor: String? = null
        var guard = 0
        do {
            val url = base + "?isArchived=false&limit=100&sortOrder=Desc" + (cursor?.let { "&cursor=$it" } ?: "")
            val page = parse<DevUniversePage>(get(url, requiresCookie = true))
            out += page.data
            cursor = page.nextPageCursor
        } while (!cursor.isNullOrEmpty() && ++guard < 20)
        return out
    }

    suspend fun userUniverses(): List<DevUniverse> = universePages("https://develop.roblox.com/v1/user/universes")
    suspend fun groupUniverses(groupId: Long): List<DevUniverse> = universePages("https://develop.roblox.com/v1/groups/$groupId/universes")

    @Serializable private data class GroupRolesResponse(val data: List<GroupRoleEntry> = emptyList())
    @Serializable private data class GroupRoleEntry(val group: GroupRef? = null, val role: RoleRef? = null)
    @Serializable private data class GroupRef(val id: Long = 0, val name: String = "")
    @Serializable private data class RoleRef(val name: String = "", val rank: Int = 0)

    suspend fun userGroups(userId: Long): List<GroupInfo> =
        parse<GroupRolesResponse>(get("https://groups.roblox.com/v1/users/$userId/groups/roles", requiresCookie = true)).data
            .mapNotNull { e -> e.group?.let { GroupInfo(it.id, it.name, e.role?.name.orEmpty(), e.role?.rank ?: 0) } }

    /** Flat {"salesTotal": 123, ...} objects -> map of the numeric fields. */
    private fun flatTotals(body: String): Map<String, Long> =
        json.parseToJsonElement(body).jsonObject.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }.toMap()

    suspend fun userTransactionTotals(userId: Long, timeFrame: String): Map<String, Long> =
        flatTotals(get("https://economy.roblox.com/v2/users/$userId/transaction-totals?timeFrame=$timeFrame&transactionType=summary", requiresCookie = true))

    suspend fun groupRevenueSummary(groupId: Long, timeFrame: String): Map<String, Long> =
        flatTotals(get("https://economy.roblox.com/v1/groups/$groupId/revenue/summary/$timeFrame", requiresCookie = true))

    @Serializable private data class TxPage(val nextPageCursor: String? = null, val data: List<JsonObject> = emptyList())
    data class TransactionPage(val items: List<Transaction>, val nextCursor: String?)

    suspend fun transactions(scope: RevenueScope, type: String = "Sale", cursor: String? = null, userId: Long? = null): TransactionPage {
        val base = when (scope) {
            is RevenueScope.User -> "https://economy.roblox.com/v2/users/${userId ?: error("userId needed")}/transactions"
            is RevenueScope.Group -> "https://economy.roblox.com/v2/groups/${scope.id}/transactions"
        }
        val url = "$base?transactionType=$type&limit=25" + (cursor?.let { "&cursor=$it" } ?: "")
        val page = parse<TxPage>(get(url, requiresCookie = true))
        val items = page.data.mapNotNull { o ->
            runCatching {
                val details = o["details"] as? JsonObject
                val place = details?.get("place") as? JsonObject
                val currency = o["currency"] as? JsonObject
                val agent = o["agent"] as? JsonObject
                Transaction(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    created = o["created"]?.jsonPrimitive?.content ?: "",
                    isPending = o["isPending"]?.jsonPrimitive?.content == "true",
                    agentName = agent?.get("name")?.jsonPrimitive?.content ?: "",
                    itemName = details?.get("name")?.jsonPrimitive?.content ?: "",
                    itemType = details?.get("type")?.jsonPrimitive?.content ?: "",
                    placeName = place?.get("name")?.jsonPrimitive?.content,
                    universeId = place?.get("universeId")?.jsonPrimitive?.longOrNull,
                    amount = currency?.get("amount")?.jsonPrimitive?.longOrNull ?: 0L,
                    currencyType = currency?.get("type")?.jsonPrimitive?.content ?: "Robux",
                )
            }.getOrNull()
        }
        return TransactionPage(items, page.nextPageCursor)
    }

    // ---------- Ads Manager backend (cookie auth; what create.roblox.com/advertise calls) ----------

    @Serializable private data class AdCampaignPage(val campaigns: List<AdCampaign> = emptyList(), val next_cursor: String? = null)
    @Serializable private data class AdvertisedUniverses(val advertised_universes: List<AdvertisedUniverse> = emptyList())
    @Serializable private data class AdvertisedUniverse(val universe_id: Long = 0, val universe_name: String = "")
    @Serializable private data class AdCredit(val ad_credit_balance_in_micro: Double? = null, val is_account_activated: Boolean? = null)

    private fun adsQuery(timePeriod: Int, groupId: Long?): String {
        val ts = java.net.URLEncoder.encode(Instant.now().toString(), "UTF-8")
        return "request_timestamp=$ts&time_period=$timePeriod&reporting_view=1" + (groupId?.let { "&groupId=$it" } ?: "")
    }

    /** All campaigns with performance for the period; the backend paginates with a cursor. */
    suspend fun adCampaigns(timePeriod: Int, groupId: Long?): List<AdCampaign> {
        val out = ArrayList<AdCampaign>()
        var cursor: String? = null
        var guard = 0
        do {
            val url = "$ADS_API/v3/native/campaigns/dateFilter?${adsQuery(timePeriod, groupId)}&include_performance=true&page_size=100" +
                (cursor?.let { "&cursor=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
            val page = parse<AdCampaignPage>(get(url, requiresCookie = true))
            out += page.campaigns
            cursor = page.next_cursor
        } while (!cursor.isNullOrEmpty() && ++guard < 20)
        return out
    }

    suspend fun adAccountSummary(timePeriod: Int, groupId: Long?): AdAccountSummary =
        parse(get("$ADS_API/v2/native/adAccountSummary/dateFilter?${adsQuery(timePeriod, groupId)}", requiresCookie = true))

    suspend fun adCreditBalanceMicro(groupId: Long?): Double? =
        parse<AdCredit>(get("$ADS_API/v1/adCreditBalance" + (groupId?.let { "?groupId=$it" } ?: ""), requiresCookie = true)).ad_credit_balance_in_micro

    /** Display statuses (Active / Paused / Scheduled / Completed / In review…) as shown in the Ads Manager. */
    suspend fun adCampaignDisplayStatuses(ids: List<String>, groupId: Long?): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val url = "$ADS_API/v3/native/campaigns/status" + (groupId?.let { "?groupId=$it" } ?: "")
        val body = """{"campaign_ids":[${ids.joinToString(",") { "\"$it\"" }}]}"""
        val root = json.parseToJsonElement(postJson(url, body))
        val out = HashMap<String, Int>()
        fun scan(el: kotlinx.serialization.json.JsonElement) {
            when (el) {
                is kotlinx.serialization.json.JsonArray -> el.forEach(::scan)
                is JsonObject -> {
                    val id = el["id"]?.jsonPrimitive?.content ?: el["campaign_id"]?.jsonPrimitive?.content
                    val st = (el["display_status"] ?: el["status"])?.jsonPrimitive?.content?.toIntOrNull()
                    if (id != null && st != null) out[id] = st else el.values.forEach(::scan)
                }
                else -> {}
            }
        }
        scan(root)
        return out
    }

    suspend fun advertisedUniverses(groupId: Long?): Map<Long, String> =
        parse<AdvertisedUniverses>(get("$ADS_API/v2/native/advertisedUniverses" + (groupId?.let { "?groupId=$it" } ?: ""), requiresCookie = true))
            .advertised_universes.associate { it.universe_id to it.universe_name }

    // ---------- Open Cloud: Analytics Query API ----------

    @Serializable private data class AnalyticsOp(val path: String? = null, val done: Boolean = false, val response: AnalyticsResp? = null, val error: AnalyticsErr? = null)
    @Serializable private data class AnalyticsResp(val values: List<AnalyticsSeries> = emptyList())
    @Serializable private data class AnalyticsSeries(val breakdowns: List<Breakdown> = emptyList(), val dataPoints: List<DataPoint> = emptyList())
    @Serializable private data class Breakdown(val dimension: String = "", val value: String = "", val displayValue: String? = null)
    @Serializable private data class DataPoint(val time: String = "", val value: Double? = null, val status: String? = null)
    @Serializable private data class AnalyticsErr(val code: Int = 0, val message: String = "")

    class AnalyticsAuthException(message: String) : IOException(message)

    /**
     * POST /analytics-query-api/v1/universes/{id}/metrics with one key. Throws AnalyticsAuthException on 401/403
     * so the caller can try another key.
     */
    suspend fun queryMetric(apiKey: String, universeId: Long, metric: String, granularity: String, start: Instant, end: Instant): List<Point> = withContext(Dispatchers.IO) {
        val bodyJson = """{"metric":"$metric","granularity":"$granularity","startTime":"$start","endTime":"$end"}"""
        val req = Request.Builder()
            .url("https://apis.roblox.com/analytics-query-api/v1/universes/$universeId/metrics")
            .header("x-api-key", apiKey)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        val bucket = bucketFor(apiKey)
        var op: AnalyticsOp? = null
        var attempt = 0
        while (op == null) {
            bucket.acquire()
            op = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 || resp.code == 403 -> throw AnalyticsAuthException(errorMessage(resp.code, body))
                    resp.code == 429 -> {
                        // Code 3000 = data point budget (query too big), not a rate limit: don't retry.
                        if (body.contains("3000")) throw ApiException(429, "Query too large for Roblox's data point budget")
                        val wait = retryAfterMs(resp, 20_000L)
                        bucket.cooldown(wait.coerceAtMost(45_000L))
                        if (attempt == 0 && wait <= 45_000L) null else throw RateLimitedException(wait)
                    }
                    !resp.isSuccessful && resp.code != 202 -> throw ApiException(resp.code, errorMessage(resp.code, body))
                    else -> parse<AnalyticsOp>(body)
                }
            }
            attempt++
        }
        var polls = 0
        while (!op!!.done && op.path != null && polls < 20) {
            kotlinx.coroutines.delay(if (polls < 3) 700L else 1500L)
            polls++
            val pollReq = Request.Builder()
                .url("https://apis.roblox.com/analytics-query-api/" + op.path!!.trimStart('/'))
                .header("x-api-key", apiKey).get().build()
            op = client.newCall(pollReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 429) { kotlinx.coroutines.delay(retryAfterMs(resp, 3_000L).coerceAtMost(10_000L)); op } else {
                    if (!resp.isSuccessful && resp.code != 202) throw ApiException(resp.code, errorMessage(resp.code, body))
                    parse<AnalyticsOp>(body)
                }
            }
        }
        val finalOp = op!!
        finalOp.error?.let { throw ApiException(it.code, it.message) }
        if (!finalOp.done) throw ApiException(504, "Analytics query timed out")
        val series = finalOp.response?.values?.firstOrNull { it.breakdowns.isEmpty() } ?: finalOp.response?.values?.firstOrNull()
        series?.dataPoints?.mapNotNull { dp ->
            val v = dp.value ?: return@mapNotNull null
            val t = runCatching { Instant.parse(dp.time).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            Point(t, v)
        }?.sortedBy { it.time } ?: emptyList()
    }

    companion object {
        const val ADS_API = "https://apis.roblox.com/ads-management-api"
        const val ANALYTICS_PER_MINUTE = 20
        const val WEB_PER_MINUTE = 90
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 CreatorPulse/1.0"
    }
}
