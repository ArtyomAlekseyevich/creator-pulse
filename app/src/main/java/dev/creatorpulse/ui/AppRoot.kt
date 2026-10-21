package dev.creatorpulse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.ui.screens.ExperienceScreen
import dev.creatorpulse.ui.screens.HomeScreen
import dev.creatorpulse.ui.screens.LoginScreen
import dev.creatorpulse.ui.screens.RevenueScreen
import dev.creatorpulse.ui.screens.SettingsScreen
import dev.creatorpulse.ui.screens.WebScreen
import dev.creatorpulse.ui.screens.WebSession
import androidx.compose.ui.platform.LocalContext

enum class Tab(val label: String) { Home("Experiences"), Revenue("Revenue"), Web("Ads & web"), Settings("Settings") }

@Composable
fun AppRoot(vm: MainViewModel, requestedUniverse: Long?, requestedTab: String?, onRequestConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var openUniverse by rememberSaveable { mutableStateOf<Long?>(null) }
    var login by rememberSaveable { mutableStateOf(false) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val web = remember { WebSession(context) }
    val cookie by vm.cookie.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshIfStale(); vm.checkForUpdates() }
    LaunchedEffect(requestedUniverse, requestedTab) {
        if (requestedUniverse != null) { tab = Tab.Home; openUniverse = requestedUniverse; onRequestConsumed() }
        else if (requestedTab != null) { tab = Tab.entries.firstOrNull { it.name.equals(requestedTab, true) } ?: Tab.Home; openUniverse = null; onRequestConsumed() }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    BackHandler(enabled = login || openUniverse != null || tab != Tab.Home) {
        when {
            login -> login = false
            openUniverse != null -> openUniverse = null
            else -> tab = Tab.Home
        }
    }

    if (login) {
        LoginScreen(onCookie = { vm.setCookie(it); login = false; tab = Tab.Home }, onClose = { login = false })
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (openUniverse == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) { Tab.Home -> Icons.Default.SportsEsports; Tab.Revenue -> Icons.Default.Payments; Tab.Web -> Icons.Default.Campaign; Tab.Settings -> Icons.Default.Settings },
                                    contentDescription = t.label,
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            AnimatedContent(
                targetState = openUniverse to tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { (universe, currentTab) ->
                when {
                    universe != null -> ExperienceScreen(
                        vm, universe,
                        onBack = { openUniverse = null },
                        onOpenSettings = { openUniverse = null; tab = Tab.Settings },
                        onOpenWeb = { url -> webUrl = url; openUniverse = null; tab = Tab.Web },
                    )
                    currentTab == Tab.Home -> HomeScreen(vm, onOpen = { openUniverse = it }, onOpenSettings = { tab = Tab.Settings })
                    currentTab == Tab.Revenue -> RevenueScreen(vm, onOpenSettings = { tab = Tab.Settings }, onLogin = { login = true })
                    currentTab == Tab.Web -> WebScreen(
                        session = web, requestedUrl = webUrl, onRequestConsumed = { webUrl = null },
                        cookie = cookie, onCookieCaptured = { vm.setCookie(it) },
                    )
                    else -> SettingsScreen(vm, onLogin = { login = true })
                }
            }
        }
    }
}
