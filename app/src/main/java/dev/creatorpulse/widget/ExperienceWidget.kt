package dev.creatorpulse.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.creatorpulse.App
import dev.creatorpulse.MainActivity
import dev.creatorpulse.R
import dev.creatorpulse.data.Experience
import dev.creatorpulse.data.ExperienceAnalytics
import dev.creatorpulse.data.Fmt

class ExperienceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExperienceWidget()
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}

class ExperienceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = App.get(context)
        val universeId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetKeys.UNIVERSE_ID]
        val snapshot = app.settings.snapshotNow()
        val exp = universeId?.let { snapshot?.experience(it) }
        val analytics = universeId?.let { snapshot?.analytics?.get(it) }
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val density = context.resources.displayMetrics.density
        val icon = WidgetData.loadIcon(context, exp?.iconUrl, (44 * density).toInt())
        val dau = analytics?.dau?.sortedBy { it.time }?.takeLast(8)?.map { it.value }.orEmpty()
        val theme = WidgetTheme.resolve(context)
        val sparkColor = runCatching { theme.accent.getColor(context).toArgb() }.getOrDefault(0xFF38BDF8.toInt())
        val spark = if (dau.size >= 2) WidgetData.sparkline(dau, (320 * density).toInt(), (44 * density).toInt(), sparkColor) else null
        val updatedAt = snapshot?.updatedAt ?: 0L

        provideContent {
            GlanceTheme(colors = theme.colors) {
                Root(universeId, appWidgetId, exp, analytics, icon, spark, updatedAt, theme)
            }
        }
    }

    @Composable
    private fun Root(universeId: Long?, appWidgetId: Int, exp: Experience?, analytics: ExperienceAnalytics?, icon: Bitmap?, spark: Bitmap?, updatedAt: Long, theme: WidgetTheme.Resolved) {
        val context = LocalContext.current
        val size = LocalSize.current
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                if (universeId != null) putExtra(MainActivity.EXTRA_UNIVERSE, universeId)
                data = Uri.parse("creatorpulse://experience/${universeId ?: 0}/$appWidgetId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        val configure = actionStartActivity(
            Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("creatorpulse://configure/$appWidgetId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        val base = GlanceModifier.fillMaxSize().appWidgetBackground().background(theme.background).cornerRadius(18.dp)
        val muted = GlanceTheme.colors.onSurfaceVariant
        val fg = GlanceTheme.colors.onSurface

        when {
            universeId == null -> Box(base.clickable(configure).padding(12.dp), contentAlignment = Alignment.Center) {
                Text("Tap to pick an experience", style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            exp == null -> Box(base.clickable(openApp).padding(12.dp), contentAlignment = Alignment.Center) {
                Text("Open Creator Pulse to load this experience", style = TextStyle(color = fg, fontSize = 12.sp))
            }
            size.height < MEDIUM.height -> Row(base.clickable(openApp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Image(ImageProvider(icon), null, GlanceModifier.size(28.dp).cornerRadius(8.dp), contentScale = ContentScale.Crop); Spacer(GlanceModifier.width(10.dp)) }
                Column(GlanceModifier.defaultWeight()) {
                    Text(exp.name, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                    Text("${Fmt.compact(exp.playing)} playing", style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                if (size.width >= MEDIUM.width) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.compact(exp.visits), style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text("visits", style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1)
                    }
                }
            }
            else -> Column(base.clickable(openApp).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { Image(ImageProvider(icon), null, GlanceModifier.size(36.dp).cornerRadius(10.dp), contentScale = ContentScale.Crop); Spacer(GlanceModifier.width(10.dp)) }
                    Column(GlanceModifier.defaultWeight()) {
                        Text(exp.name, style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(exp.creatorName.ifBlank { "Roblox" }, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                    }
                    Image(
                        ImageProvider(R.drawable.ic_refresh), "Refresh",
                        GlanceModifier.size(22.dp).clickable(actionRunCallback<RefreshAction>()),
                        colorFilter = ColorFilter.tint(muted),
                    )
                }
                Spacer(GlanceModifier.height(10.dp))
                val latestDau = analytics?.latestDau
                Row(GlanceModifier.fillMaxWidth()) {
                    Metric("Playing", Fmt.compact(exp.playing), theme.accent)
                    Metric("Visits", Fmt.compact(exp.visits), fg)
                    if (latestDau != null) Metric("DAU ${Fmt.dateUtc(latestDau.time)}", Fmt.compact(latestDau.value), fg)
                    else Metric("Favorites", Fmt.compact(exp.favorites), fg)
                }
                if (size.height >= LARGE.height) {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        if (analytics != null) {
                            Metric("Revenue 7d", Fmt.robuxCompact(analytics.revenue7d), theme.positive)
                            Metric("Spent today", analytics.spentToday?.let { Fmt.robuxCompact(it) } ?: "–", fg)
                            Metric("Favorites", Fmt.compact(exp.favorites), fg)
                        } else {
                            Metric("Rating", exp.ratingPercent?.let { "$it%" } ?: "–", fg)
                            Metric("Max/server", exp.maxPlayers.toString(), fg)
                            Metric("Likes", Fmt.compact(exp.upVotes), fg)
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    if (spark != null) {
                        Image(ImageProvider(spark), "DAU trend", GlanceModifier.fillMaxWidth().height(44.dp), contentScale = ContentScale.FillBounds)
                        Text("DAU, last ${analytics?.dau?.size ?: 0} days", style = TextStyle(color = muted, fontSize = 10.sp))
                    } else {
                        Text(
                            if (analytics == null) "Add an Open Cloud API key in the app for DAU and revenue here" else "No DAU data yet",
                            style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 2,
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text("Updated ${Fmt.ago(updatedAt)}", style = TextStyle(color = muted, fontSize = 10.sp))
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Metric(label: String, value: String, color: ColorProvider) {
        Column(GlanceModifier.defaultWeight()) {
            Text(value, style = TextStyle(color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val MEDIUM = DpSize(180.dp, 100.dp)
        val LARGE = DpSize(250.dp, 170.dp)
    }
}
