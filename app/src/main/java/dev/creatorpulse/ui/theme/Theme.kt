package dev.creatorpulse.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.creatorpulse.App
import dev.creatorpulse.data.ThemeMode
import dev.creatorpulse.data.ThemeSpec

/** Per-metric chart colors. With "accent charts" on, everything derives from the theme instead. */
class ChartPalette(
    val players: Color,
    val revenue: Color,
    val visits: Color,
    val time: Color,
    val retention: Color,
    val paying: Color,
)

val DefaultChartPalette = ChartPalette(
    players = Color(0xFF38BDF8),
    revenue = Color(0xFF22C55E),
    visits = Color(0xFFA78BFA),
    time = Color(0xFFF59E0B),
    retention = Color(0xFFF472B6),
    paying = Color(0xFF34D399),
)

val LocalChartPalette = staticCompositionLocalOf { DefaultChartPalette }
val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec() }

/** Convenience accessor used by the screens. */
object ChartColors {
    val players: Color @Composable get() = LocalChartPalette.current.players
    val revenue: Color @Composable get() = LocalChartPalette.current.revenue
    val visits: Color @Composable get() = LocalChartPalette.current.visits
    val time: Color @Composable get() = LocalChartPalette.current.time
    val retention: Color @Composable get() = LocalChartPalette.current.retention
    val paying: Color @Composable get() = LocalChartPalette.current.paying
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun CreatorPulseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember(context) { runCatching { App.get(context).themeStore }.getOrNull() }
    val spec: ThemeSpec = if (store != null) {
        val s by store.spec.collectAsStateWithLifecycle()
        s
    } else ThemeSpec()
    val systemDark = isSystemInDarkTheme()
    val dark = when (spec.mode) { ThemeMode.System -> systemDark; ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val schemes = remember(spec, context) { SchemeBuilder.resolve(context, spec) }
    val scheme = if (dark) schemes.second else schemes.first

    if (!view.isInEditMode) {
        LaunchedEffect(dark) {
            (view.context.findActivity() as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            )
        }
    }

    val charts = remember(scheme, spec.accentCharts) {
        if (!spec.accentCharts) DefaultChartPalette else {
            val (h, s, _) = SchemeBuilder.toHsl(scheme.primary)
            val l = if (dark) 0.68f else 0.42f
            ChartPalette(
                players = scheme.primary,
                revenue = scheme.tertiary,
                visits = SchemeBuilder.hsl(h + 120f, s.coerceAtLeast(0.4f), l),
                time = SchemeBuilder.hsl(h + 180f, s.coerceAtLeast(0.4f), l),
                retention = SchemeBuilder.hsl(h + 240f, s.coerceAtLeast(0.4f), l),
                paying = scheme.secondary,
            )
        }
    }

    CompositionLocalProvider(LocalChartPalette provides charts, LocalThemeSpec provides spec) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
