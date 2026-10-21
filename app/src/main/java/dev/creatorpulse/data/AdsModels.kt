package dev.creatorpulse.data

import kotlinx.serialization.Serializable

/**
 * Models for Roblox's Ads Manager backend (apis.roblox.com/ads-management-api), the same calls the
 * create.roblox.com/advertise site makes with the session cookie. Field names mirror the JSON (snake_case).
 */
@Serializable
data class AdPerformance(
    val click_count: Double? = null,
    val cost_per_play_usd: Double? = null,
    /** micro-USD */
    val display_spending_usd: Double? = null,
    val impression: Double? = null,
    val play_count: Double? = null,
    val total_play_time_hours_7d: Double? = null,
    val total_robux_revenue_30d: Double? = null,
    val fifteen_sec_video_view_count: Double? = null,
    val spend_micro_usd: Double? = null,
    val spend_before_today_micro_usd: Double? = null,
    val cost_per_millie_usd: Double? = null,
) {
    val spendUsd: Double get() = ((spend_micro_usd ?: display_spending_usd) ?: 0.0) / 1_000_000.0
    val cpm: Double? get() = cost_per_millie_usd ?: impression?.takeIf { it > 0 }?.let { spendUsd / it * 1000.0 }
    val ctr: Double? get() = impression?.takeIf { it > 0 }?.let { (click_count ?: 0.0) / it }
}

@Serializable
data class AdBudget(
    val lifetime_budget_micro_usd: Double? = null,
    val daily_budget_micro_usd: Double? = null,
    val budget_type: Int? = null,
)

@Serializable
data class AdCampaign(
    val id: String = "",
    val name: String = "",
    val status: Int? = null,
    val objective: Int? = null,
    val payment_type: Int? = null,
    val universe_id: Long? = null,
    val budget: AdBudget? = null,
    val performance: AdPerformance? = null,
    val start_timestamp_ms: Long? = null,
    val end_timestamp_ms: Long? = null,
    val created_timestamp_ms: Long? = null,
    val updated_timestamp_ms: Long? = null,
    val is_off_platform_request: Boolean? = null,
    val is_reporting_enabled: Boolean? = null,
    /** CAMPAIGN_DISPLAY_STATUS_* from the status endpoint (what the Ads Manager shows), when available. */
    val display_status: Int? = null,
) {
    val statusLabel: String get() = when (display_status) {
        1 -> "Paused"; 2 -> "Scheduled"; 3 -> "Inactive"; 4 -> "Error"; 5 -> "Completed"; 6 -> "In review"; 7 -> "Active"
        else -> when (status) {
            2 -> "Active"; 3 -> "Stopped"; 4 -> "Archived"; 5 -> "Cancelled"; 6 -> "Paused (no credit)"; else -> "Unknown"
        }
    }
    val isLive: Boolean get() = if (display_status != null) display_status == 7 else status == 2
    val isAttention: Boolean get() = display_status == 4 || status == 6
    val objectiveLabel: String get() = when (objective) {
        1 -> "Awareness"; 2 -> "Visits"; 3 -> "Video views"; 4 -> "Retention"; 5 -> "Reactivation"
        6 -> "New users"; 7 -> "Reach"; 8 -> "Spend"; 9 -> "Engaged plays"; else -> "Objective $objective"
    }
    val paymentLabel: String get() = when (payment_type) {
        1 -> "Card"; 2 -> "Ad credit"; 3 -> "Invoice"; 4 -> "Group ad credit"; else -> ""
    }
    val budgetLabel: String? get() {
        val b = budget ?: return null
        b.daily_budget_micro_usd?.takeIf { it > 0 }?.let { return "${Fmt.usd(it / 1e6)}/day" }
        b.lifetime_budget_micro_usd?.takeIf { it > 0 }?.let { return "${Fmt.usd(it / 1e6)} lifetime" }
        return null
    }
}

@Serializable
data class AdAccountSummary(
    val total_ad_spend_usd: Double? = null,
    val total_clicks: Double? = null,
    val total_impressions: Double? = null,
    val total_play_time_hours_7d: Double? = null,
    val total_plays: Double? = null,
    val total_robux_revenue_30d: Double? = null,
) {
    val spendUsd: Double get() = (total_ad_spend_usd ?: 0.0) / 1_000_000.0
}

/** Everything the Ads screen shows for one scope (you or a group) and time period. */
@Serializable
data class AdsOverview(
    val scopeKey: String,
    val timePeriod: Int,
    val fetchedAt: Long,
    val campaigns: List<AdCampaign> = emptyList(),
    val summary: AdAccountSummary? = null,
    val adCreditMicro: Double? = null,
    val universeNames: Map<Long, String> = emptyMap(),
    val error: String? = null,
) {
    /** Summary from the campaigns when the account summary endpoint is unavailable. */
    val totals: AdAccountSummary get() = summary ?: AdAccountSummary(
        total_ad_spend_usd = campaigns.sumOf { it.performance?.display_spending_usd ?: 0.0 },
        total_clicks = campaigns.sumOf { it.performance?.click_count ?: 0.0 },
        total_impressions = campaigns.sumOf { it.performance?.impression ?: 0.0 },
        total_play_time_hours_7d = campaigns.sumOf { it.performance?.total_play_time_hours_7d ?: 0.0 },
        total_plays = campaigns.sumOf { it.performance?.play_count ?: 0.0 },
        total_robux_revenue_30d = campaigns.sumOf { it.performance?.total_robux_revenue_30d ?: 0.0 },
    )
}

/** DATE_FILTERING_TIME_PERIOD_* values the Ads Manager uses. */
data class AdTimePeriod(val value: Int, val label: String)
val AD_TIME_PERIODS = listOf(
    AdTimePeriod(1, "Today"), AdTimePeriod(2, "Yesterday"), AdTimePeriod(3, "7 days"), AdTimePeriod(4, "30 days"),
    AdTimePeriod(5, "This month"), AdTimePeriod(6, "Last month"), AdTimePeriod(7, "Year to date"),
)
