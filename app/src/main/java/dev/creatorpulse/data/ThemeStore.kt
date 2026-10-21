package dev.creatorpulse.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

data class ThemePreset(val id: String, val name: String, val seed: Long)

/** Built-in accent palettes. The scheme is generated from the seed, so one color is enough. */
val THEME_PRESETS = listOf(
    ThemePreset("pulse", "Pulse green", 0xFF22C55E),
    ThemePreset("roblox", "Roblox red", 0xFFE2231A),
    ThemePreset("ocean", "Ocean", 0xFF0EA5E9),
    ThemePreset("violet", "Violet", 0xFF8B5CF6),
    ThemePreset("sunset", "Sunset", 0xFFF97316),
    ThemePreset("rose", "Rose", 0xFFF43F5E),
    ThemePreset("gold", "Gold", 0xFFEAB308),
    ThemePreset("mint", "Mint", 0xFF14B8A6),
    ThemePreset("graphite", "Graphite", 0xFF64748B),
)

data class ThemeSpec(
    val mode: ThemeMode = ThemeMode.System,
    /** Material You colors from the wallpaper (Android 12+). Falls back to the seed below. */
    val dynamic: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    /** Id of a [THEME_PRESETS] entry, or "custom". */
    val presetId: String = "pulse",
    /** ARGB seed color used when not dynamic. */
    val seed: Long = 0xFF22C55E,
    /** Pure black backgrounds in dark mode (OLED). */
    val amoled: Boolean = false,
    /** 20..100, background alpha of the home screen widgets. */
    val widgetOpacity: Int = 100,
    /** Charts use the accent instead of the fixed per-metric colors. */
    val accentCharts: Boolean = false,
) {
    val dynamicAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val usesDynamic: Boolean get() = dynamic && dynamicAvailable
}

/** Small, synchronous store so the first frame already has the right colors (no flash of the default theme). */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val _spec = MutableStateFlow(load())
    val spec: StateFlow<ThemeSpec> = _spec
    val current: ThemeSpec get() = _spec.value

    fun update(transform: (ThemeSpec) -> ThemeSpec) {
        val s = transform(current).let { it.copy(widgetOpacity = it.widgetOpacity.coerceIn(20, 100)) }
        prefs.edit()
            .putString("mode", s.mode.name)
            .putBoolean("dynamic", s.dynamic)
            .putString("preset", s.presetId)
            .putLong("seed", s.seed)
            .putBoolean("amoled", s.amoled)
            .putInt("widget_opacity", s.widgetOpacity)
            .putBoolean("accent_charts", s.accentCharts)
            .apply()
        _spec.value = s
    }

    private fun load(): ThemeSpec {
        val d = ThemeSpec()
        return ThemeSpec(
            mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", null) ?: d.mode.name) }.getOrDefault(d.mode),
            dynamic = prefs.getBoolean("dynamic", d.dynamic),
            presetId = prefs.getString("preset", d.presetId) ?: d.presetId,
            seed = prefs.getLong("seed", d.seed),
            amoled = prefs.getBoolean("amoled", d.amoled),
            widgetOpacity = prefs.getInt("widget_opacity", d.widgetOpacity),
            accentCharts = prefs.getBoolean("accent_charts", d.accentCharts),
        )
    }
}
