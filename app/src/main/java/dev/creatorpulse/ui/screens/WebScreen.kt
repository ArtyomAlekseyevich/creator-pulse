package dev.creatorpulse.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Pages worth one tap. Ads Manager has no read API, so it is wrapped here with the user's session. */
data class WebLink(val label: String, val url: String, val desktop: Boolean = true)

val WEB_LINKS = listOf(
    WebLink("Ads Manager", "https://create.roblox.com/advertise/"),
    WebLink("Creator Hub", "https://create.roblox.com/dashboard/creations"),
    WebLink("Transactions", "https://www.roblox.com/transactions", desktop = false),
    WebLink("Group payouts", "https://create.roblox.com/dashboard/group", desktop = true),
)

/**
 * One WebView that survives tab switches (kept by the activity-level composition), signed in with the
 * same .ROBLOSECURITY cookie the app uses. Navigation stays on Roblox domains; anything else opens
 * in the system browser.
 */
class WebSession(private val context: Context) {
    var progress by mutableIntStateOf(100)
    var currentUrl by mutableStateOf("")
    var title by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var desktop by mutableStateOf(true)
        private set
    var onCookie: ((String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    val view: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { this@WebSession.progress = newProgress }
            override fun onReceivedTitle(view: WebView?, t: String?) { this@WebSession.title = t.orEmpty() }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { currentUrl = url.orEmpty(); sync() }
            override fun onPageFinished(view: WebView?, url: String?) { currentUrl = url.orEmpty(); sync(); captureCookie() }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host ?: return false
                val onRoblox = host.endsWith("roblox.com") || host.endsWith("rbxcdn.com") || host.contains("arkoselabs") || host.contains("funcaptcha") || host.contains("hcaptcha") || host.contains("google")
                if (uri.scheme?.startsWith("http") == true && onRoblox) return false
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }
        }
        // `view` is not assigned yet inside this initializer, so set the desktop UA directly.
        settings.userAgentString = DESKTOP_UA
    }

    private fun sync() { canGoBack = view.canGoBack(); canGoForward = view.canGoForward() }

    private fun captureCookie() {
        val raw = CookieManager.getInstance().getCookie("https://www.roblox.com") ?: return
        val value = raw.split(';').map { it.trim() }.firstOrNull { it.startsWith(".ROBLOSECURITY=") }?.removePrefix(".ROBLOSECURITY=") ?: return
        if (value.isNotBlank()) onCookie?.invoke(value)
    }

    /** Makes sure the WebView cookie jar has the app's session (it won't if the cookie was pasted). */
    fun seedCookie(cookie: String?) {
        cookie ?: return
        val cm = CookieManager.getInstance()
        val existing = cm.getCookie("https://www.roblox.com").orEmpty()
        if (!existing.contains(".ROBLOSECURITY=$cookie")) {
            cm.setCookie("https://www.roblox.com", ".ROBLOSECURITY=$cookie; Domain=.roblox.com; Path=/; Secure; HttpOnly")
            cm.flush()
        }
    }

    private fun applyDesktop(on: Boolean) {
        desktop = on
        view.settings.userAgentString = if (on) DESKTOP_UA else null
        view.settings.loadWithOverviewMode = true
        view.settings.useWideViewPort = true
    }

    fun useDesktop(on: Boolean) { if (on != desktop) { applyDesktop(on); view.reload() } }

    fun load(url: String, desktopMode: Boolean? = null) {
        desktopMode?.let { if (it != desktop) applyDesktop(it) }
        view.loadUrl(url)
    }

    companion object {
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(session: WebSession, requestedUrl: String?, onRequestConsumed: () -> Unit, cookie: String?, onCookieCaptured: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(cookie) { session.seedCookie(cookie) }
    LaunchedEffect(Unit) {
        session.onCookie = { captured -> if (captured != cookie) onCookieCaptured(captured) }
        if (session.currentUrl.isBlank() && requestedUrl == null) session.load(WEB_LINKS.first().url, WEB_LINKS.first().desktop)
    }
    LaunchedEffect(requestedUrl) {
        if (requestedUrl != null) { session.load(requestedUrl); onRequestConsumed() }
    }
    BackHandler(enabled = session.canGoBack) { session.view.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(session.title.ifBlank { "Roblox" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(session.currentUrl.removePrefix("https://"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        IconButton(onClick = { session.view.goBack() }, enabled = session.canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        IconButton(onClick = { session.view.goForward() }, enabled = session.canGoForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
                        IconButton(onClick = { session.view.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                        IconButton(onClick = { session.useDesktop(!session.desktop) }) {
                            Icon(if (session.desktop) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows, if (session.desktop) "Switch to mobile site" else "Switch to desktop site")
                        }
                        IconButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.currentUrl.ifBlank { WEB_LINKS.first().url }))) }
                        }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser") }
                    },
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEB_LINKS.forEach { link ->
                        val selected = session.currentUrl.startsWith(link.url.removeSuffix("/"))
                        FilterChip(selected = selected, onClick = { session.load(link.url, link.desktop) }, label = { Text(link.label) })
                    }
                }
                if (session.progress < 100) LinearProgressIndicator(progress = { session.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = {
                (session.view.parent as? ViewGroup)?.removeView(session.view)
                session.view
            },
            onRelease = { v -> (v.parent as? ViewGroup)?.removeView(v) },
        )
    }
}
