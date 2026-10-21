package dev.creatorpulse.widget

import android.content.Context
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import dev.creatorpulse.App
import dev.creatorpulse.data.ThemeMode
import dev.creatorpulse.ui.theme.SchemeBuilder

/** Gives the Glance widgets the same colors as the app, honoring the forced light/dark mode and opacity. */
object WidgetTheme {
    class Resolved(val colors: ColorProviders, val background: ColorProvider, val accent: ColorProvider, val positive: ColorProvider, val warm: ColorProvider)

    fun resolve(context: Context): Resolved {
        val spec = App.get(context).themeStore.current
        val (light, dark) = SchemeBuilder.resolve(context, spec)
        val alpha = spec.widgetOpacity.coerceIn(20, 100) / 100f
        val (day, night) = when (spec.mode) {
            ThemeMode.Light -> light to light
            ThemeMode.Dark -> dark to dark
            ThemeMode.System -> light to dark
        }
        val bgDay = (if (spec.usesDynamic) day.surfaceContainer else day.surface).copy(alpha = alpha)
        val bgNight = (if (spec.usesDynamic) night.surfaceContainer else night.surface).copy(alpha = alpha)
        return Resolved(
            colors = ColorProviders(light = day, dark = night),
            background = androidx.glance.color.ColorProvider(day = bgDay, night = bgNight),
            accent = androidx.glance.color.ColorProvider(day = day.primary, night = night.primary),
            positive = androidx.glance.color.ColorProvider(day = day.tertiary, night = night.tertiary),
            warm = androidx.glance.color.ColorProvider(day = day.secondary, night = night.secondary),
        )
    }
}
