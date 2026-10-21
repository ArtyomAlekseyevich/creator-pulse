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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.ui.MainViewModel
import dev.creatorpulse.ui.components.SectionTitle
import dev.creatorpulse.widget.WidgetPin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onLogin: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val cookie by vm.cookie.collectAsStateWithLifecycle()
    val expired by vm.sessionExpired.collectAsStateWithLifecycle()
    val apiKeys by vm.apiKeys.collectAsStateWithLifecycle()
    val groups by vm.knownGroups.collectAsStateWithLifecycle()
    val tracked by vm.trackedGroups.collectAsStateWithLifecycle()
    val manual by vm.manualUniverses.collectAsStateWithLifecycle()
    val refreshMinutes by vm.refreshMinutes.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCookieDialog by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showAddExperience by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- Account
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                            Text("Roblox account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (cookie == null) {
                            Text("Sign in to load your experiences, Robux balance, sales and group revenue. The login happens on roblox.com; only the session cookie is stored, encrypted on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onLogin) { Text("Sign in with Roblox") }
                                OutlinedButton(onClick = { showCookieDialog = true }) { Text("Paste cookie") }
                            }
                        } else {
                            val u = snapshot?.user
                            Text(if (u != null) "${u.displayName} (@${u.name})" else "Signed in", style = MaterialTheme.typography.bodyLarge)
                            if (expired) Text("Session expired. Sign in again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (expired) Button(onClick = onLogin) { Text("Sign in again") }
                                OutlinedButton(onClick = { vm.setCookie(null); android.webkit.CookieManager.getInstance().removeAllCookies(null) }) { Text("Sign out") }
                            }
                        }
                    }
                }
            }
            // ---- Appearance
            item { AppearanceCard(spec = theme, onChange = { vm.updateTheme(it) }) }
            // ---- Groups
            if (groups.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                                Text("Groups to track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Tracked groups add their experiences to the list and their revenue to the Revenue tab (needs group permissions).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            groups.sortedByDescending { it.rank }.forEach { g ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(g.roleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = g.id in tracked, onCheckedChange = { vm.setGroupTracked(g.id, it) })
                                }
                            }
                            if (groups.isNotEmpty()) TextButton(onClick = { vm.refresh() }) { Text("Apply and refresh") }
                        }
                    }
                }
            }
            // ---- Manual experiences
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("Experiences by id") { TextButton(onClick = { showAddExperience = true }) { Text("Add") } }
                        Text("Track any experience by universe id, place id or link. Public stats work without signing in; analytics need a key that covers it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        manual.forEach { id ->
                            val name = snapshot?.experience(id)?.name ?: "Universe $id"
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("$name · $id", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { vm.removeExperience(id) }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                            }
                        }
                    }
                }
            }
            // ---- API keys
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                            Text("Open Cloud API keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showKeyDialog = true }) { Text("Add") }
                        }
                        Text(
                            "Needed for analytics charts (DAU, revenue, playtime, retention, concurrent players). Create one at Creator Hub → Credentials → API keys: add the 'universe-analytics' system with the 'universe.analytics:read' operation and pick your experiences. Group experiences need a key created under the group.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://create.roblox.com/dashboard/credentials"))) }) { Text("Open credentials page") }
                        apiKeys.forEach { k ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(k.label)
                                    Text(k.key.take(6) + "…" + k.key.takeLast(4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.removeApiKey(k.id) }) { Icon(Icons.Default.Delete, contentDescription = "Remove key") }
                            }
                        }
                    }
                }
            }
            // ---- Widgets
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Widgets, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                            Text("Widgets & background refresh", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Two home screen widgets: 'Experience stats' (one experience, pick it from the widget or from an experience's page) and 'Creator overview' (Robux, players online, sales).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { WidgetPin.experience(context, null) }) { Text("Add experience widget") }
                            OutlinedButton(onClick = { WidgetPin.overview(context) }) { Text("Add overview") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Background refresh every:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60, 180).forEach { m ->
                                FilterChip(selected = refreshMinutes == m, onClick = { vm.setRefreshMinutes(m) }, label = { Text(if (m < 60) "${m}m" else "${m / 60}h") })
                            }
                        }
                        Text("Android may stretch intervals to save battery. Tapping the refresh icon on a widget fetches immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snapshot?.let { Text("Last successful refresh: ${Fmt.ago(it.updatedAt)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            // ---- Updates
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                            Text("Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("v${vm.currentVersion}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("New versions are published on GitHub. The app checks once a day and can download and install them here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val info = update.available
                        when {
                            update.downloading != null -> {
                                Text("Downloading ${info?.version ?: ""}… ${update.downloading}%")
                                androidx.compose.material3.LinearProgressIndicator(progress = { (update.downloading ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                            }
                            info != null -> {
                                Text("Version ${info.version} is available (${Fmt.compact(info.sizeBytes.toDouble())}B).", fontWeight = FontWeight.Medium)
                                if (info.notes.isNotBlank()) Text(info.notes.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { vm.downloadAndInstallUpdate() }) { Text("Download and install") }
                                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl))) }) { Text("Release notes") }
                                }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.checkForUpdates(manual = true) }, enabled = !update.checking) { Text(if (update.checking) "Checking…" else "Check for updates") }
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.releasesUrl))) }) { Text("All releases") }
                                }
                                if (update.upToDate) Text("You're on the latest version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        update.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Text("Creator Pulse is an unofficial companion for the Roblox Creator Hub. Public stats come from games.roblox.com; account data uses the same web APIs as the Creator Hub; analytics use the Open Cloud Analytics Query API (beta).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showCookieDialog) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("Paste .ROBLOSECURITY") },
            text = {
                Column {
                    Text("Copy the .ROBLOSECURITY cookie value from your browser's dev tools (Application → Cookies → roblox.com). Never share it with anyone.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Cookie value") }, maxLines = 4, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { if (value.isNotBlank()) vm.setCookie(value); showCookieDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showCookieDialog = false }) { Text("Cancel") } },
        )
    }
    if (showKeyDialog) {
        var label by remember { mutableStateOf("") }
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("Add Open Cloud API key") },
            text = {
                Column {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (e.g. My studio)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API key") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { if (key.isNotBlank()) vm.addApiKey(label, key); showKeyDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showKeyDialog = false }) { Text("Cancel") } },
        )
    }
    if (showAddExperience) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddExperience = false },
            title = { Text("Add experience") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Universe id, place id or link") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.addExperience(value) { status = it }; showAddExperience = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddExperience = false }) { Text("Cancel") } },
        )
    }
}
