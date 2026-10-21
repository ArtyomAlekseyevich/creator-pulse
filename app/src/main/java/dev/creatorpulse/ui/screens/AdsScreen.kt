package dev.creatorpulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.data.AD_TIME_PERIODS
import dev.creatorpulse.data.AdCampaign
import dev.creatorpulse.data.AdsOverview
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.data.RevenueScope
import dev.creatorpulse.ui.MainViewModel
import dev.creatorpulse.ui.components.InfoCard
import dev.creatorpulse.ui.components.SectionTitle
import dev.creatorpulse.ui.components.StatTile
import dev.creatorpulse.ui.theme.ChartColors

const val ADS_MANAGER_URL = "https://create.roblox.com/advertise/manage"

/** Native view of the Roblox Ads Manager: account totals and every campaign with its performance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsScreen(vm: MainViewModel, onOpenWeb: (String) -> Unit, onLogin: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val cookie by vm.cookie.collectAsStateWithLifecycle()
    val ads by vm.ads.collectAsStateWithLifecycle()
    val loading by vm.adsLoading.collectAsStateWithLifecycle()
    val errors by vm.adsErrors.collectAsStateWithLifecycle()
    var scopeKey by rememberSaveable { mutableStateOf("user") }
    var periodIndex by rememberSaveable { mutableIntStateOf(3) }
    val period = AD_TIME_PERIODS[periodIndex]

    val scopes: List<RevenueScope> = listOf(RevenueScope.User) + (snapshot?.groups?.map { RevenueScope.Group(it.id, it.name) } ?: emptyList())
    val scope = scopes.firstOrNull { vm.scopeKey(it) == scopeKey } ?: RevenueScope.User
    val key = vm.adsKey(scope, period.value)
    val overview = ads[key]
    val isLoading = key in loading
    val signedIn = cookie != null

    LaunchedEffect(key, signedIn) { if (signedIn) vm.loadAds(scope, period.value) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ads", fontWeight = FontWeight.Bold)
                        overview?.let { Text("Updated ${Fmt.ago(it.fetchedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { vm.loadAds(scope, period.value, force = true) }, enabled = signedIn && !isLoading) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { onOpenWeb(ADS_MANAGER_URL) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open Ads Manager website") }
                },
            )
        },
    ) { padding ->
        if (!signedIn) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                InfoCard(
                    title = "Sign in to see your ad campaigns",
                    body = "Campaign spend, impressions, plays and status come from the same backend the Roblox Ads Manager uses, with your account.",
                    actionLabel = "Sign in with Roblox", onAction = onLogin,
                )
            }
            return@Scaffold
        }
        PullToRefreshBox(isRefreshing = isLoading, onRefresh = { vm.loadAds(scope, period.value, force = true) }, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scopes.size > 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            scopes.forEach { s ->
                                val label = when (s) { RevenueScope.User -> snapshot?.user?.displayName ?: "You"; is RevenueScope.Group -> s.name }
                                FilterChip(selected = vm.scopeKey(s) == scopeKey, onClick = { scopeKey = vm.scopeKey(s) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AD_TIME_PERIODS.forEachIndexed { i, p -> FilterChip(selected = periodIndex == i, onClick = { periodIndex = i }, label = { Text(p.label) }) }
                    }
                }
                errors[key]?.let { err ->
                    item { InfoCard(title = "Couldn't load ads", body = err, actionLabel = "Open Ads Manager website", onAction = { onOpenWeb(ADS_MANAGER_URL) }) }
                }
                if (overview != null) {
                    item { SummaryTiles(overview) }
                    val live = overview.campaigns.count { it.isLive }
                    item { SectionTitle("Campaigns (${overview.campaigns.size}${if (live > 0) ", $live active" else ""})") }
                    if (overview.campaigns.isEmpty()) {
                        item { InfoCard(title = "No campaigns in this period", body = "Campaigns that ran in the selected time period show up here with their spend and results.", actionLabel = "Create one on the website", onAction = { onOpenWeb(ADS_MANAGER_URL) }) }
                    }
                    items(overview.campaigns.sortedWith(compareByDescending<AdCampaign> { it.isLive }.thenByDescending { it.performance?.spendUsd ?: 0.0 }), key = { it.id }) { c ->
                        CampaignCard(c, overview.universeNames[c.universe_id ?: -1L])
                    }
                    item {
                        Text(
                            "Spend is in USD (ad credit shown at its USD value). Robux revenue is attributed over 30 days and playtime over 7 days, as in the Ads Manager. Source: Roblox Ads Manager backend.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (!isLoading && errors[key] == null) {
                    item { Text("Loading campaigns…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryTiles(o: AdsOverview) {
    val t = o.totals
    val plays = t.total_plays ?: 0.0
    val cpp = if (plays > 0) t.spendUsd / plays else null
    val ctr = t.total_impressions?.takeIf { it > 0 }?.let { (t.total_clicks ?: 0.0) / it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Spend", Fmt.usd(t.spendUsd), Modifier.weight(1f), accent = ChartColors.retention, sub = if (o.summary == null) "sum of campaigns" else "account total")
            StatTile("Plays", Fmt.compact(plays), Modifier.weight(1f), accent = ChartColors.players, sub = cpp?.let { "${Fmt.usd(it)} per play" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Impressions", Fmt.compact(t.total_impressions ?: 0.0), Modifier.weight(1f), accent = ChartColors.visits, sub = ctr?.let { "CTR ${Fmt.percent(it * 100, 2)}" })
            StatTile("Clicks", Fmt.compact(t.total_clicks ?: 0.0), Modifier.weight(1f), accent = ChartColors.time)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Robux revenue, 30d", Fmt.robuxCompact(t.total_robux_revenue_30d ?: 0.0), Modifier.weight(1f), accent = ChartColors.revenue, sub = "from acquired players")
            StatTile("Playtime, 7d", "${Fmt.compact(t.total_play_time_hours_7d ?: 0.0)} h", Modifier.weight(1f), accent = ChartColors.paying)
        }
        o.adCreditMicro?.let { StatTile("Ad credit balance", Fmt.usd(it / 1e6), Modifier.fillMaxWidth(), accent = ChartColors.revenue, sub = "prepaid credit on this account") }
    }
}

@Composable
private fun CampaignCard(c: AdCampaign, universeName: String?) {
    val statusColor = when {
        c.isLive -> ChartColors.revenue
        c.isAttention -> MaterialTheme.colorScheme.error
        c.display_status == 2 || c.display_status == 6 || c.display_status == 1 -> ChartColors.time
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name.ifBlank { "Campaign ${c.id}" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    c.statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                listOfNotNull(universeName, c.objectiveLabel, c.budgetLabel, c.paymentLabel.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val start = c.start_timestamp_ms?.let { Fmt.dateLocal(it) }
            val end = c.end_timestamp_ms?.let { Fmt.dateLocal(it) }
            if (start != null || end != null) Text("${start ?: "?"} – ${end ?: "ongoing"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            val p = c.performance
            if (p == null) {
                Text("No performance data for this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Spend", Fmt.usd(p.spendUsd), ChartColors.retention)
                    Metric("Plays", Fmt.compact(p.play_count ?: 0.0), ChartColors.players)
                    Metric("Per play", p.cost_per_play_usd?.let { Fmt.usd(it) } ?: (p.play_count?.takeIf { it > 0 }?.let { Fmt.usd(p.spendUsd / it) } ?: "–"), MaterialTheme.colorScheme.onSurface)
                    Metric("Impr.", Fmt.compact(p.impression ?: 0.0), ChartColors.visits)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Clicks", Fmt.compact(p.click_count ?: 0.0), ChartColors.time)
                    Metric("CTR", p.ctr?.let { Fmt.percent(it * 100, 2) } ?: "–", MaterialTheme.colorScheme.onSurface)
                    Metric("Robux 30d", Fmt.compact(p.total_robux_revenue_30d ?: 0.0), ChartColors.revenue)
                    Metric("Play 7d", "${Fmt.compact(p.total_play_time_hours_7d ?: 0.0)}h", ChartColors.paying)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
