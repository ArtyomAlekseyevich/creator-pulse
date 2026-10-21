package dev.creatorpulse.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.creatorpulse.App
import dev.creatorpulse.MainActivity
import dev.creatorpulse.R
import dev.creatorpulse.data.Fmt
import dev.creatorpulse.data.Snapshot
import kotlinx.coroutines.flow.first

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}

class OverviewWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = App.get(context).settings.snapshotNow()
        val lastError = runCatching { App.get(context).settings.lastError.first() }.getOrNull()
        val theme = WidgetTheme.resolve(context)
        provideContent { GlanceTheme(colors = theme.colors) { Root(snapshot, lastError, theme) } }
    }

    @Composable
    private fun Root(snap: Snapshot?, lastError: String?, theme: WidgetTheme.Resolved) {
        val context = LocalContext.current
        val size = LocalSize.current
        val open = actionStartActivity(Intent(context, MainActivity::class.java).apply { data = Uri.parse("creatorpulse://home"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        val openRevenue = actionStartActivity(Intent(context, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_TAB, "Revenue"); data = Uri.parse("creatorpulse://revenue"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        val base = GlanceModifier.fillMaxSize().appWidgetBackground().background(theme.background).cornerRadius(18.dp)
        val fg = GlanceTheme.colors.onSurface
        val muted = GlanceTheme.colors.onSurfaceVariant
        val green = theme.positive
        val blue = theme.accent
        val amber = theme.warm

        if (snap == null) {
            Box(base.clickable(open).padding(12.dp), contentAlignment = Alignment.Center) {
                Text("Open Creator Pulse to load your stats", style = TextStyle(color = fg, fontSize = 12.sp))
            }
            return
        }
        val salesToday = (snap.userTotals["Day"]?.get("salesTotal") ?: 0L) + snap.groups.sumOf { it.totals["Day"]?.get("itemSaleRobux") ?: 0L }
        val salesMonth = (snap.userTotals["Month"]?.get("salesTotal") ?: 0L) + snap.groups.sumOf { it.totals["Month"]?.get("itemSaleRobux") ?: 0L }
        val hasRevenue = snap.user != null

        Column(base.clickable(open).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (size.width < WIDE.width) {
                    Tile("Online", Fmt.compact(snap.onlineNow), blue)
                    if (hasRevenue) Tile("Today", Fmt.robuxCompact(salesToday.toDouble()), green, openRevenue)
                    else Tile("Visits", Fmt.compact(snap.experiences.sumOf { it.visits }), fg)
                } else {
                    if (snap.robux != null) Tile("Robux", Fmt.compact(snap.robux), green, openRevenue)
                    Tile("Online now", Fmt.compact(snap.onlineNow), blue)
                    if (hasRevenue) {
                        Tile("Sales today", Fmt.robuxCompact(salesToday.toDouble()), amber, openRevenue)
                        Tile("This month", Fmt.robuxCompact(salesMonth.toDouble()), fg, openRevenue)
                    } else {
                        Tile("Visits", Fmt.compact(snap.experiences.sumOf { it.visits }), fg)
                        Tile("Experiences", snap.experiences.size.toString(), fg)
                    }
                    Image(ImageProvider(R.drawable.ic_refresh), "Refresh", GlanceModifier.size(20.dp).clickable(actionRunCallback<RefreshAction>()), colorFilter = ColorFilter.tint(muted))
                }
            }
            if (size.height >= TALL.height) {
                Spacer(GlanceModifier.height(8.dp))
                val top = snap.experiences.take(if (size.height >= 180.dp) 5 else 3)
                top.forEach { e ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(8.dp))
                        Text("${Fmt.compact(e.playing)} playing", style = TextStyle(color = if (e.playing > 0) blue else muted, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        snap.analytics[e.universeId]?.let { a ->
                            Spacer(GlanceModifier.width(8.dp))
                            Text(Fmt.robuxCompact(a.revenue7d) + "/7d", style = TextStyle(color = green, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    if (lastError != null) "Last refresh failed: $lastError" else "Updated ${Fmt.ago(snap.updatedAt)}",
                    style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun RowScope.Tile(label: String, value: String, color: ColorProvider, action: androidx.glance.action.Action? = null) {
        val m = GlanceModifier.defaultWeight().let { if (action != null) it.clickable(action) else it }
        Column(m) {
            Text(value, style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val WIDE = DpSize(250.dp, 40.dp)
        val TALL = DpSize(250.dp, 110.dp)
    }
}
