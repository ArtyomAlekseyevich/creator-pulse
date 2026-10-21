package dev.creatorpulse.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.creatorpulse.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Success callback for "Add widget" from the experience screen: binds the freshly pinned widget to that experience. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val universeId = intent.getLongExtra(MainActivity.EXTRA_UNIVERSE, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || universeId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { bindExperienceWidget(context, appWidgetId, universeId) } finally { pending.finish() }
        }
    }

    companion object { const val ACTION = "dev.creatorpulse.WIDGET_PINNED" }
}
