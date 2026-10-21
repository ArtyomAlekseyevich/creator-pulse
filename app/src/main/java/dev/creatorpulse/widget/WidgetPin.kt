package dev.creatorpulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.creatorpulse.MainActivity

/** Asks the launcher to pin one of our widgets (Android 8+). Returns false if the launcher can't. */
object WidgetPin {
    fun experience(context: Context, universeId: Long?): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return unsupported(context)
        val callback = universeId?.let {
            val intent = Intent(context, WidgetPinnedReceiver::class.java)
                .setAction(WidgetPinnedReceiver.ACTION)
                .putExtra(MainActivity.EXTRA_UNIVERSE, it)
                .setData(Uri.parse("creatorpulse://pin/$it"))
            PendingIntent.getBroadcast(context, it.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }
        return mgr.requestPinAppWidget(ComponentName(context, ExperienceWidgetReceiver::class.java), null, callback)
    }

    fun overview(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return unsupported(context)
        return mgr.requestPinAppWidget(ComponentName(context, OverviewWidgetReceiver::class.java), null, null)
    }

    private fun unsupported(context: Context): Boolean {
        Toast.makeText(context, "Your launcher does not support pinning widgets. Add it from the launcher's widget picker instead.", Toast.LENGTH_LONG).show()
        return false
    }
}
