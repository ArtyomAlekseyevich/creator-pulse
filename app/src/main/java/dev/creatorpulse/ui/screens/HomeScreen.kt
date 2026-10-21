package dev.creatorpulse.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.data.Experience
import dev.creatorpulse.data.ExperienceAnalytics
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.data.Snapshot
import dev.creatorpulse.ui.MainViewModel
import dev.creatorpulse.ui.components.GameIcon
import dev.creatorpulse.ui.components.InfoCard
import dev.creatorpulse.ui.components.SectionTitle
import dev.creatorpulse.ui.components.StatTile
import dev.creatorpulse.ui.theme.ChartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val cookie by vm.cookie.collectAsStateWithLifecycle()
    val expired by vm.sessionExpired.collectAsStateWithLifecycle()
    val apiKeys by vm.apiKeys.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Creator Pulse", fontWeight = FontWeight.Bold)
                        snapshot?.let { Text("Updated ${Fmt.ago(it.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    if (refreshing) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 2.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { vm.refresh() }, enabled = !refreshing) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize().padding(padding)) {
            val snap = snapshot
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                update.available?.let { info ->
                    item {
                        InfoCard(
                            title = "Update available: ${info.version}",
                            body = if (update.downloading != null) "Downloading… ${update.downloading}%" else info.notes.lines().firstOrNull { it.isNotBlank() }?.take(140) ?: "A new version of Creator Pulse is ready.",
                            actionLabel = if (update.downloading != null) null else "Download and install",
                            onAction = { vm.downloadAndInstallUpdate() },
                        )
                    }
                }
                if (cookie == null) {
                    item {
                        InfoCard(
                            title = "Sign in to see your Creator Hub",
                            body = "Sign in with your Roblox account to load your experiences, Robux balance, sales and group revenue. You can also add experiences by id without signing in.",
                            actionLabel = "Open settings", onAction = onOpenSettings,
                        )
                    }
                } else if (expired) {
                    item { InfoCard(title = "Roblox session expired", body = "Sign in again to keep revenue and private stats updating.", actionLabel = "Sign in", onAction = onOpenSettings) }
                }
                if (snap != null) {
                    item { SummaryRow(snap, hasKeys = apiKeys.isNotEmpty()) }
                    if (snap.warnings.isNotEmpty()) {
                        item { Text(snap.warnings.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                    item { SectionTitle("Experiences (${snap.experiences.size})") }
                    if (snap.experiences.isEmpty()) {
                        item { InfoCard(title = "No experiences yet", body = "Sign in, track a group, or add an experience by id in Settings.", actionLabel = "Settings", onAction = onOpenSettings) }
                    }
                    items(snap.experiences, key = { it.universeId }) { exp ->
                        ExperienceCard(exp, snap.analytics[exp.universeId], onClick = { onOpen(exp.universeId) })
                    }
                    if (apiKeys.isEmpty() && snap.experiences.isNotEmpty()) {
                        item {
                            InfoCard(
                                title = "Add an Open Cloud API key for analytics",
                                body = "DAU, revenue, playtime and retention charts use Roblox's official Analytics API, which needs an API key with the universe.analytics:read permission.",
                                actionLabel = "Add key", onAction = onOpenSettings,
                            )
                        }
                    }
                } else if (!refreshing) {
                    item { Text("Pull to refresh.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryRow(snap: Snapshot, hasKeys: Boolean) {
    val todaySales = snap.userTotals["Day"]?.get("salesTotal")
    val monthSales = snap.userTotals["Month"]?.get("salesTotal")
    val groupMonth = snap.groups.sumOf { it.totals["Month"]?.get("itemSaleRobux") ?: 0L }
    val groupDay = snap.groups.sumOf { it.totals["Day"]?.get("itemSaleRobux") ?: 0L }
    val spentToday = snap.analytics.values.mapNotNull { it.spentToday }.takeIf { it.isNotEmpty() }?.sum()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Online now", Fmt.compact(snap.onlineNow), Modifier.weight(1f), accent = ChartColors.players, sub = "${snap.experiences.count { it.playing > 0 }} live")
            if (snap.robux != null) StatTile("Robux", Fmt.compact(snap.robux), Modifier.weight(1f), accent = ChartColors.revenue, sub = "balance")
            else StatTile("Visits", Fmt.compact(snap.experiences.sumOf { it.visits }), Modifier.weight(1f), accent = ChartColors.visits, sub = "all time")
        }
        if (todaySales != null || monthSales != null || spentToday != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dayTotal = (todaySales ?: 0L) + groupDay
                StatTile("Sales today", Fmt.robuxCompact(dayTotal.toDouble()), Modifier.weight(1f), accent = ChartColors.revenue, sub = if (groupDay > 0) "incl. groups" else "you")
                StatTile("Sales this month", Fmt.robuxCompact(((monthSales ?: 0L) + groupMonth).toDouble()), Modifier.weight(1f), accent = ChartColors.paying, sub = if (groupMonth > 0) "incl. groups" else "you")
            }
        }
        val adRevenue7d = snap.analytics.values.sumOf { it.adRevenue7d }
        if (hasKeys && (spentToday != null || adRevenue7d > 0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (spentToday != null) StatTile("Spent today (realtime)", Fmt.robuxCompact(spentToday), Modifier.weight(1f), accent = ChartColors.time, sub = "gross Robux, UTC day")
                if (adRevenue7d > 0) StatTile("Ad revenue, 7 days", Fmt.robuxCompact(adRevenue7d), Modifier.weight(1f), accent = ChartColors.paying, sub = "immersive + video ads")
            }
        }
    }
}

@Composable
fun ExperienceCard(exp: Experience, analytics: ExperienceAnalytics?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcon(exp.iconUrl, size = 60)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exp.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(exp.creatorName.ifBlank { exp.source.replaceFirstChar { it.uppercase() } })
                        if (!exp.isActive) append(" · private")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(Icons.Default.People, Fmt.compact(exp.playing), if (exp.playing > 0) ChartColors.players else MaterialTheme.colorScheme.onSurfaceVariant)
                    Stat(Icons.Default.Visibility, Fmt.compact(exp.visits), MaterialTheme.colorScheme.onSurfaceVariant)
                    Stat(Icons.Default.Favorite, Fmt.compact(exp.favorites), MaterialTheme.colorScheme.onSurfaceVariant)
                    exp.ratingPercent?.let { Stat(Icons.Default.ThumbUp, "$it%", MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (analytics != null) {
                    Spacer(Modifier.height(4.dp))
                    val parts = ArrayList<String>()
                    analytics.latestDau?.let { parts += "DAU ${Fmt.compact(it.value)} (${Fmt.dateUtc(it.time)})" }
                    parts += "7d ${Fmt.robuxCompact(analytics.revenue7d)}"
                    analytics.spentToday?.let { parts += "today ${Fmt.robuxCompact(it)}" }
                    if (analytics.adRevenue7d > 0) parts += "ads ${Fmt.robuxCompact(analytics.adRevenue7d)}"
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = ChartColors.revenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
