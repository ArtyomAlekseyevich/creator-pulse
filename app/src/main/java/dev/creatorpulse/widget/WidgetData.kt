package dev.creatorpulse.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.creatorpulse.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object WidgetKeys {
    val UNIVERSE_ID = longPreferencesKey("universe_id")
}

object WidgetData {
    /** Universe ids currently shown by any Experience widget, so the refresh prioritizes them. */
    suspend fun widgetUniverseIds(context: Context): Set<Long> = runCatching {
        val mgr = GlanceAppWidgetManager(context)
        mgr.getGlanceIds(ExperienceWidget::class.java).mapNotNull { id ->
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetKeys.UNIVERSE_ID]
        }.toSet()
    }.getOrDefault(emptySet())

    suspend fun updateAllWidgets(context: Context) {
        runCatching { ExperienceWidget().updateAll(context) }
        runCatching { OverviewWidget().updateAll(context) }
    }

    /** Small on-disk cache for game icons so widgets can render them as bitmaps. */
    suspend fun loadIcon(context: Context, url: String?, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        url ?: return@withContext null
        runCatching {
            val dir = File(context.cacheDir, "widget_icons").apply { mkdirs() }
            val file = File(dir, md5(url) + ".png")
            val bytes = if (file.exists()) file.readBytes() else {
                val req = Request.Builder().url(url).build()
                App.get(context).api.client.newCall(req).execute().use { r -> if (!r.isSuccessful) return@runCatching null; r.body?.bytes() }
                    ?.also { file.writeBytes(it) } ?: return@runCatching null
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
        }.getOrNull()
    }

    private fun md5(s: String): String = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Draws a sparkline into a bitmap; Glance has no Canvas so charts are rendered this way. */
    fun sparkline(values: List<Double>, widthPx: Int, heightPx: Int, color: Int, bars: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(2), heightPx.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (values.size < 2) return bmp
        val max = values.max().takeIf { it > 0 } ?: 1.0
        val pad = heightPx * 0.08f
        val h = heightPx - pad * 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = heightPx * 0.07f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        if (bars) {
            val slot = widthPx.toFloat() / values.size
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
            values.forEachIndexed { i, v ->
                val top = pad + (h - (h * (v / max)).toFloat())
                val left = slot * i + slot * 0.2f
                c.drawRoundRect(left, top, left + slot * 0.6f, heightPx.toFloat(), 4f, 4f, fill)
            }
        } else {
            val path = Path()
            val fillPath = Path()
            values.forEachIndexed { i, v ->
                val x = widthPx * i / (values.size - 1).toFloat()
                val y = pad + (h - (h * (v / max)).toFloat())
                if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, heightPx.toFloat()); fillPath.lineTo(x, y) } else { path.lineTo(x, y); fillPath.lineTo(x, y) }
            }
            fillPath.lineTo(widthPx.toFloat(), heightPx.toFloat()); fillPath.close()
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 50; style = Paint.Style.FILL }
            c.drawPath(fillPath, fill)
            c.drawPath(path, paint)
        }
        return bmp
    }
}

/** Binds an Experience widget instance to a universe and redraws it. */
suspend fun bindExperienceWidget(context: Context, appWidgetId: Int, universeId: Long) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { it[WidgetKeys.UNIVERSE_ID] = universeId }
    ExperienceWidget().update(context, glanceId)
}
