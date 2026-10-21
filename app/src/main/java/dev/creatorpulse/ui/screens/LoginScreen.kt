package dev.creatorpulse.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Signs the user in through the real roblox.com login page inside a WebView and grabs the
 * .ROBLOSECURITY session cookie the site sets. Credentials never pass through the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onCookie: (String) -> Unit, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var captured by remember { mutableStateOf(false) }

    fun tryCapture(): Boolean {
        if (captured) return true
        val raw = CookieManager.getInstance().getCookie("https://www.roblox.com") ?: return false
        val value = raw.split(';').map { it.trim() }.firstOrNull { it.startsWith(".ROBLOSECURITY=") }?.removePrefix(".ROBLOSECURITY=")
        if (!value.isNullOrBlank()) {
            captured = true
            CookieManager.getInstance().flush()
            onCookie(value)
            return true
        }
        return false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Roblox") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Log in like you normally would. The app only keeps the session cookie on this device, encrypted, and uses it for the same requests the Creator Hub makes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true; tryCapture() }
                            override fun onPageFinished(view: WebView?, url: String?) { loading = false; tryCapture() }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) { tryCapture() }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val host = request?.url?.host ?: return false
                                // keep everything inside the WebView except non-http schemes (roblox:// deep links etc)
                                return request.url.scheme?.startsWith("http") != true || (!host.endsWith("roblox.com") && !host.endsWith("rbxcdn.com") && !host.contains("arkoselabs") && !host.contains("funcaptcha") && !host.contains("hcaptcha") && !host.contains("google"))
                            }
                        }
                        loadUrl("https://www.roblox.com/login")
                    }
                },
            )
        }
    }
}
