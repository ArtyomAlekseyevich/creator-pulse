package dev.creatorpulse.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.data.RevenueScope
import dev.creatorpulse.data.TIME_FRAMES
import dev.creatorpulse.data.TotalsLabels
import dev.creatorpulse.data.Transaction
import dev.creatorpulse.ui.MainViewModel
import dev.creatorpulse.ui.components.InfoCard
import dev.creatorpulse.ui.components.KeyValueRow
import dev.creatorpulse.ui.components.SectionTitle
import dev.creatorpulse.ui.components.StatTile
import dev.creatorpulse.ui.theme.ChartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevenueScreen(vm: MainViewModel, onOpenSettings: () -> Unit, onLogin: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val cookie by vm.cookie.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val txMap by vm.transactions.collectAsStateWithLifecycle()
    var scopeKey by rememberSaveable { mutableStateOf("user") }
    var tfIndex by rememberSaveable { mutableIntStateOf(2) }
    val timeFrame = TIME_FRAMES[tfIndex]

    val scopes: List<RevenueScope> = listOf(RevenueScope.User) + (snapshot?.groups?.map { RevenueScope.Group(it.id, it.name) } ?: emptyList())
    val scope = scopes.firstOrNull { vm.scopeKey(it) == scopeKey } ?: RevenueScope.User
    val signedIn = cookie != null && snapshot?.user != null

    LaunchedEffect(scopeKey, signedIn) { if (signedIn) vm.loadTransactions(scope) }
    LaunchedEffect(scopeKey, timeFrame, signedIn) { if (signedIn) vm.ensureTotals(scope, timeFrame) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revenue", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { vm.refresh(); if (signedIn) vm.reloadTransactions(scope) }, enabled = !refreshing) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") } },
            )
        },
    ) { padding ->
        if (!signedIn) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                InfoCard(
                    title = "Sign in to see revenue",
                    body = "Robux balance, sales totals, group revenue and recent transactions come from your Roblox account, the same data the Creator Hub shows.",
                    actionLabel = "Sign in with Roblox", onAction = onLogin,
                )
            }
            return@Scaffold
        }
        val snap = snapshot!!
        val totals: Map<String, Long> = when (scope) {
            RevenueScope.User -> snap.userTotals[timeFrame].orEmpty()
            is RevenueScope.Group -> snap.groups.firstOrNull { it.id == scope.id }?.totals?.get(timeFrame).orEmpty()
        }
        val balance: Long? = when (scope) { RevenueScope.User -> snap.robux; is RevenueScope.Group -> snap.groups.firstOrNull { it.id == scope.id }?.robux }
        val incomeKeys = if (scope is RevenueScope.User) TotalsLabels.userIncomeKeys else TotalsLabels.groupIncomeKeys
        val income = incomeKeys.sumOf { totals[it] ?: 0L }
        val pending = TotalsLabels.pendingKeys.sumOf { totals[it] ?: 0L }
        val adPayouts = TotalsLabels.adPayoutKeys.sumOf { totals[it] ?: 0L }
        val adSpend = TotalsLabels.adSpendKeys.sumOf { totals[it] ?: 0L }
        val hasAdKeys = (TotalsLabels.adPayoutKeys + TotalsLabels.adSpendKeys).any { it in totals }
        val tx = txMap[vm.scopeKey(scope)] ?: dev.creatorpulse.ui.TxState()

        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scopes.size > 1) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        scopes.forEach { s ->
                            val label = when (s) { RevenueScope.User -> snap.user?.displayName ?: "You"; is RevenueScope.Group -> s.name }
                            FilterChip(selected = vm.scopeKey(s) == scopeKey, onClick = { scopeKey = vm.scopeKey(s) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                        }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TIME_FRAMES.forEachIndexed { i, tf ->
                        SegmentedButton(selected = tfIndex == i, onClick = { tfIndex = i }, shape = SegmentedButtonDefaults.itemShape(i, TIME_FRAMES.size)) { Text(tf) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(if (scope is RevenueScope.User) "Sales" else "Item sales", Fmt.robux(income), Modifier.weight(1f), accent = ChartColors.revenue, sub = "this ${timeFrame.lowercase()}")
                        StatTile("Pending", Fmt.robux(pending), Modifier.weight(1f), accent = ChartColors.time, sub = "escrow")
                    }
                    if (balance != null) StatTile("Robux balance", Fmt.robux(balance), Modifier.fillMaxWidth(), accent = ChartColors.paying)
                    if (hasAdKeys) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Ad payouts", Fmt.robux(adPayouts), Modifier.weight(1f), accent = ChartColors.paying, sub = "ads shown in your experiences")
                            StatTile("Ad spend", Fmt.robux(adSpend), Modifier.weight(1f), accent = ChartColors.retention, sub = "sponsoring / Ads Manager")
                        }
                    }
                }
            }
            if (totals.isEmpty() && timeFrame !in listOf("Day", "Month")) {
                item { Text("Loading $timeFrame totals…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (totals.isEmpty()) {
                item { Text("No totals returned for this scope. For groups you need the 'view group payouts/revenue' permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            SectionTitle("Breakdown · $timeFrame")
                            totals.entries.sortedByDescending { kotlin.math.abs(it.value) }.forEach { (k, v) ->
                                KeyValueRow(TotalsLabels.label(k), Fmt.robux(v), emphasize = v != 0L)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Recent sales") { if (tx.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) } }
            if (tx.error != null) item { Text(tx.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (tx.loaded && tx.items.isEmpty() && tx.error == null) item { Text("No sales yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(tx.items, key = { it.id }) { t -> TransactionRow(t); HorizontalDivider() }
            if (tx.cursor != null) {
                item { OutlinedButton(onClick = { vm.loadTransactions(scope, more = true) }, enabled = !tx.loading, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.itemName.ifBlank { t.itemType }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t.agentName.takeIf { it.isNotBlank() }, t.placeName, t.itemType.takeIf { it.isNotBlank() && it != t.itemName }, Fmt.isoToLocal(t.created).takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (t.amount >= 0) "+" else "") + Fmt.robux(t.amount), color = if (t.amount >= 0) ChartColors.revenue else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            if (t.isPending) Text("pending", style = MaterialTheme.typography.labelSmall, color = ChartColors.time)
        }
    }
}
