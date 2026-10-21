package dev.creatorpulse.data

import kotlinx.serialization.Serializable

/** Data that the whole app (and the widgets) reads. Persisted as JSON in DataStore. */
@Serializable
data class Snapshot(
    val updatedAt: Long = 0L,
    val user: UserInfo? = null,
    val robux: Long? = null,
    val experiences: List<Experience> = emptyList(),
    /** timeFrame (Day/Week/Month/Year) -> field -> amount, for the signed-in user */
    val userTotals: Map<String, Map<String, Long>> = emptyMap(),
    val groups: List<GroupSnapshot> = emptyList(),
    /** universeId -> short analytics summary (needs an Open Cloud key) */
    val analytics: Map<Long, ExperienceAnalytics> = emptyMap(),
    val warnings: List<String> = emptyList(),
) {
    val onlineNow: Long get() = experiences.sumOf { it.playing }
    fun experience(id: Long): Experience? = experiences.firstOrNull { it.universeId == id }
}

@Serializable
data class UserInfo(val id: Long, val name: String, val displayName: String)

@Serializable
data class Experience(
    val universeId: Long,
    val rootPlaceId: Long,
    val name: String,
    val creatorName: String = "",
    val creatorType: String = "",
    val creatorId: Long = 0,
    val playing: Long = 0,
    val visits: Long = 0,
    val favorites: Long = 0,
    val maxPlayers: Int = 0,
    val upVotes: Long = 0,
    val downVotes: Long = 0,
    val iconUrl: String? = null,
    val created: String? = null,
    val updated: String? = null,
    val price: Long? = null,
    /** "user", "group:<id>" or "manual" */
    val source: String = "manual",
    val isActive: Boolean = true,
) {
    val ratingPercent: Int? get() {
        val total = upVotes + downVotes
        return if (total == 0L) null else ((upVotes * 100.0) / total).toInt()
    }
}

@Serializable
data class GroupInfo(val id: Long, val name: String, val roleName: String = "", val rank: Int = 0)

@Serializable
data class GroupSnapshot(
    val id: Long,
    val name: String,
    val robux: Long? = null,
    /** timeFrame -> field -> amount (from /v1/groups/{id}/revenue/summary/{timeFrame}) */
    val totals: Map<String, Map<String, Long>> = emptyMap(),
)

@Serializable
data class Point(val time: Long, val value: Double)

@Serializable
data class Series(val metric: String, val granularity: String, val points: List<Point>, val error: String? = null, val fromCache: Boolean = false, val note: String? = null)

@Serializable
data class ExperienceAnalytics(
    val universeId: Long,
    val fetchedAt: Long,
    val dau: List<Point> = emptyList(),
    val revenue: List<Point> = emptyList(),
    /** Gross Robux spent today (UTC) from the realtime ItemMonetizationRevenue metric. */
    val spentToday: Double? = null,
    /** Daily publisher ad revenue (immersive / rewarded video ads), last ~8 days. */
    val adRevenue: List<Point> = emptyList(),
) {
    val adRevenue7d: Double get() {
        val todayStart = TimeUtil.startOfTodayUtcMillis()
        return adRevenue.filter { it.time < todayStart && it.time >= todayStart - 7 * TimeUtil.DAY_MS }.sumOf { it.value }
    }
    val latestDau: Point? get() = dau.maxByOrNull { it.time }
    val revenue7d: Double get() {
        val todayStart = TimeUtil.startOfTodayUtcMillis()
        return revenue.filter { it.time < todayStart && it.time >= todayStart - 7 * TimeUtil.DAY_MS }.sumOf { it.value }
    }
}

@Serializable
data class Transaction(
    val id: Long,
    val created: String,
    val isPending: Boolean,
    val agentName: String,
    val itemName: String,
    val itemType: String,
    val placeName: String? = null,
    val universeId: Long? = null,
    val amount: Long,
    val currencyType: String = "Robux",
)

@Serializable
data class ApiKeyEntry(val id: String, val label: String, val key: String)

/** Who the revenue screen is looking at. */
sealed class RevenueScope {
    data object User : RevenueScope()
    data class Group(val id: Long, val name: String) : RevenueScope()
}

object TimeUtil {
    const val DAY_MS = 86_400_000L
    fun startOfTodayUtcMillis(): Long {
        val now = System.currentTimeMillis()
        return now - (now % DAY_MS)
    }
}

val TIME_FRAMES = listOf("Day", "Week", "Month", "Year")

/** Human labels for the flat totals objects Roblox returns. Unknown keys fall back to a humanized key. */
object TotalsLabels {
    private val labels = mapOf(
        // users/{id}/transaction-totals
        "salesTotal" to "Sales",
        "purchasesTotal" to "Purchases",
        "affiliateSalesTotal" to "Affiliate sales",
        "groupPayoutsTotal" to "Group payouts",
        "currencyPurchasesTotal" to "Robux purchased",
        "premiumStipendsTotal" to "Premium stipends",
        "tradeSystemEarningsTotal" to "Trade earnings",
        "tradeSystemCostsTotal" to "Trade costs",
        "premiumPayoutsTotal" to "Premium payouts",
        "groupPremiumPayoutsTotal" to "Group premium payouts",
        "adSpendTotal" to "Ad spend",
        "developerExchangeTotal" to "DevEx",
        "pendingRobuxTotal" to "Pending Robux",
        "incomingRobuxTotal" to "Incoming",
        "outgoingRobuxTotal" to "Outgoing",
        "individualToGroupTotal" to "Sent to groups",
        "csAdjustmentTotal" to "Adjustments",
        "adsRevsharePayoutsTotal" to "Immersive ads",
        "groupAdsRevsharePayoutsTotal" to "Group immersive ads",
        "subscriptionsRevshareTotal" to "Subscriptions",
        "groupSubscriptionsRevshareTotal" to "Group subscriptions",
        "subscriptionsRevshareOutgoingTotal" to "Subscription clawbacks",
        "groupSubscriptionsRevshareOutgoingTotal" to "Group subscription clawbacks",
        "publishingAdvanceRebatesTotal" to "Publishing advance rebates",
        "affiliatePayoutTotal" to "Affiliate payouts",
        // groups/{id}/revenue/summary
        "recurringRobuxStipend" to "Recurring stipend",
        "itemSaleRobux" to "Item sales",
        "purchasedRobux" to "Robux purchased",
        "tradeSystemRobux" to "Trades",
        "pendingRobux" to "Pending Robux",
        "groupPayoutRobux" to "Payouts",
        "individualToGroupRobux" to "Funds received",
        "premiumPayouts" to "Premium payouts",
        "groupPremiumPayouts" to "Group premium payouts",
        "adjustmentRobux" to "Adjustments",
        "immersiveAdPayouts" to "Immersive ads",
        "subscriptionPayouts" to "Subscriptions",
        "subscriptionClawbacks" to "Subscription clawbacks",
        "commissionRobux" to "Commissions",
        "publishingAdvanceRebates" to "Publishing advance rebates",
        "affiliatePayoutRobux" to "Affiliate payouts",
    )

    fun label(key: String): String = labels[key] ?: key
        .removeSuffix("Total").removeSuffix("Robux")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase().replaceFirstChar { it.uppercase() }

    /** Keys that represent money earned, used for the headline number. */
    val userIncomeKeys = listOf("salesTotal")
    val groupIncomeKeys = listOf("itemSaleRobux")
    val pendingKeys = listOf("pendingRobuxTotal", "pendingRobux")
    /** Robux earned from showing ads (immersive / rewarded video). */
    val adPayoutKeys = listOf("adsRevsharePayoutsTotal", "groupAdsRevsharePayoutsTotal", "immersiveAdPayouts")
    /** Robux spent on sponsoring / advertising. */
    val adSpendKeys = listOf("adSpendTotal")
}
