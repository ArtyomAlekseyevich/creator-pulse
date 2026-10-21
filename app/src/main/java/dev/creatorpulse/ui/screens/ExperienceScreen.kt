package dev.creatorpulse.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.MainActivity
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.data.Series
import dev.creatorpulse.ui.ChartKind
import dev.creatorpulse.ui.AD_METRICS
import dev.creatorpulse.ui.DETAIL_METRICS
import dev.creatorpulse.ui.MainViewModel
import dev.creatorpulse.ui.MetricSpec
import dev.creatorpulse.ui.ValueKind
import dev.creatorpulse.ui.components.GameIcon
import dev.creatorpulse.ui.components.InfoCard
import dev.creatorpulse.ui.components.StatTile
import dev.creatorpulse.ui.components.TimeSeriesChart
import dev.creatorpulse.ui.theme.ChartColors
import dev.creatorpulse.widget.WidgetPin

private val RANGES = listOf(7, 30, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperienceScreen(vm: MainViewModel, universeId: Long, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenWeb: (String) -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val apiKeys by vm.apiKeys.collectAsStateWithLifecycle()
    val showAds by vm.showAdMetrics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    val days = RANGES[rangeIndex]
    val exp = snapshot?.experience(universeId)
    val detail = details[universeId]

    LaunchedEffect(universeId, days, apiKeys.size, showAds) { if (apiKeys.isNotEmpty()) vm.loadDetail(universeId, days, withAds = showAds) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exp?.name ?: "Experience", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (detail?.loading == true) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { vm.refresh(); vm.loadDetail(universeId, days, force = true) }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(exp?.iconUrl, size = 72)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exp?.name ?: "Universe $universeId", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(exp?.creatorName?.takeIf { it.isNotBlank() }, "Universe $universeId", exp?.updated?.let { "updated ${Fmt.isoToLocal(it)}" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (exp != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Playing now", Fmt.int(exp.playing), Modifier.weight(1f), accent = ChartColors.players, sub = "max ${exp.maxPlayers}/server")
                            StatTile("Visits", Fmt.compact(exp.visits), Modifier.weight(1f), accent = ChartColors.visits, sub = Fmt.int(exp.visits))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Favorites", Fmt.compact(exp.favorites), Modifier.weight(1f), accent = ChartColors.retention)
                            StatTile("Rating", exp.ratingPercent?.let { "$it%" } ?: "–", Modifier.weight(1f), accent = ChartColors.paying, sub = "${Fmt.compact(exp.upVotes)} up · ${Fmt.compact(exp.downVotes)} down")
                        }
                        snapshot?.analytics?.get(universeId)?.let { a ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatTile("Revenue, last 7 days", Fmt.robux(a.revenue7d), Modifier.weight(1f), accent = ChartColors.revenue)
                                StatTile("Spent today", a.spentToday?.let { Fmt.robux(it) } ?: "–", Modifier.weight(1f), accent = ChartColors.time, sub = "realtime, UTC")
                            }
                            if (a.adRevenue7d > 0) {
                                StatTile("Ad revenue, last 7 days", Fmt.robux(a.adRevenue7d), Modifier.fillMaxWidth(), accent = ChartColors.paying, sub = "immersive + rewarded video ads")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onOpenWeb("https://create.roblox.com/dashboard/creations/experiences/$universeId/overview") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Creator Hub")
                    }
                    OutlinedButton(onClick = { WidgetPin.experience(context, universeId) }) { Icon(Icons.Default.Widgets, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add widget") }
                }
            }
            if (apiKeys.isEmpty()) {
                item {
                    InfoCard(
                        title = "Analytics need an Open Cloud API key",
                        body = "Create a key at create.roblox.com → Credentials with the universe-analytics system and universe.analytics:read permission for this experience, then add it in Settings.",
                        actionLabel = "Open settings", onAction = onOpenSettings,
                    )
                }
            } else if (detail?.noKeyMessage != null) {
                item { InfoCard(title = "Key does not cover this experience", body = detail.noKeyMessage, actionLabel = "Manage keys", onAction = onOpenSettings) }
            } else {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        RANGES.forEachIndexed { i, d ->
                            SegmentedButton(selected = rangeIndex == i, onClick = { rangeIndex = i }, shape = SegmentedButtonDefaults.itemShape(i, RANGES.size)) { Text("${d}D") }
                        }
                    }
                }
                items(DETAIL_METRICS, key = { it.metric }) { spec -> MetricCard(spec, detail?.series?.get(spec.metric), loading = detail?.loading == true) }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Revenue, impressions and EPM from immersive and rewarded video ads in this experience. Loads 6 more queries.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = showAds, onCheckedChange = { vm.setShowAdMetrics(it) })
                    }
                }
                if (showAds) {
                    items(AD_METRICS, key = { it.metric }) { spec -> MetricCard(spec, detail?.series?.get(spec.metric), loading = detail?.loading == true) }
                }
                item {
                    Text(
                        "Daily metrics are bucketed by UTC day and usually finalize a few hours after midnight UTC. Results are cached (hourly data 10 min, daily data 2 h) to stay under Roblox's ~30 queries per minute. Source: Roblox Open Cloud Analytics Query API (beta).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MetricCard(spec: MetricSpec, series: Series?, loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            val points = series?.points.orEmpty()
            val isFraction = spec.value == ValueKind.PERCENT && points.isNotEmpty() && points.maxOf { it.value } <= 1.0
            val fmt: (Double) -> String = when (spec.value) {
                ValueKind.COUNT -> { v -> Fmt.compact(v) }
                ValueKind.ROBUX -> { v -> Fmt.robuxCompact(v) }
                ValueKind.MINUTES -> { v -> Fmt.minutes(v) }
                ValueKind.PERCENT -> { v -> Fmt.percent(if (isFraction) v * 100 else v) }
            }
            val color = when (spec.value) {
                ValueKind.COUNT -> if (spec.metric.contains("Paying")) ChartColors.paying else if (spec.metric == "Visits") ChartColors.visits else ChartColors.players
                ValueKind.ROBUX -> ChartColors.revenue
                ValueKind.MINUTES -> ChartColors.time
                ValueKind.PERCENT -> ChartColors.retention
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val summary = when {
                        points.isEmpty() -> null
                        spec.kind == ChartKind.BAR -> "Total ${fmt(points.sumOf { it.value })} · latest ${fmt(points.last().value)}"
                        else -> "Latest ${fmt(points.last().value)} · avg ${fmt(points.map { it.value }.average())}"
                    }
                    if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (series == null && loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(8.dp))
            when {
                series?.error != null -> Text(series.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                series == null -> Spacer(Modifier.height(60.dp))
                else -> {
                    TimeSeriesChart(
                        points = points, color = color, bars = spec.kind == ChartKind.BAR, formatValue = fmt,
                        formatTime = { t -> if (spec.realtime) Fmt.timeLocal(t) else Fmt.dateUtc(t) },
                        height = if (spec.realtime) 140 else 160,
                    )
                    if (series.note != null) Text(series.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}
