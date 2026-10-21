package dev.creatorpulse.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.creatorpulse.data.ThemeSpec
import kotlin.math.abs

/**
 * Generates Material 3 color schemes from one seed color, roughly following the M3 tonal-role
 * recipe (tone 40 primary in light, tone 80 in dark, tone 90/30 containers, tinted neutrals).
 * Uses HSL lightness as the "tone", which is close enough for a UI accent.
 */
object SchemeBuilder {

    fun hsl(h: Float, s: Float, l: Float): Color {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val light = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * light - 1f)) * sat
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = light - c / 2f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r1 + m, g1 + m, b1 + m)
    }

    /** Returns (hue 0..360, saturation 0..1, lightness 0..1). */
    fun toHsl(color: Color): FloatArray {
        val r = color.red; val g = color.green; val b = color.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return floatArrayOf(0f, 0f, l)
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }
        return floatArrayOf(h, s, l)
    }

    private class Palette(private val hue: Float, private val sat: Float) {
        fun tone(t: Int): Color {
            // Tame saturation near black/white so very light/dark tones don't look neon.
            val edge = (abs(t - 50) / 50f)
            val s = sat * (1f - 0.35f * edge * edge)
            return hsl(hue, s, t / 100f)
        }
    }

    fun fromSeed(seed: Color, dark: Boolean, amoled: Boolean): ColorScheme {
        val (h, sRaw, _) = toHsl(seed)
        val s = sRaw.coerceIn(0.08f, 0.95f)
        val p = Palette(h, s)
        val sec = Palette(h, s * 0.45f)
        val ter = Palette(h + 60f, (s * 0.7f).coerceAtLeast(0.2f))
        val n = Palette(h, (s * 0.08f).coerceIn(0.02f, 0.08f))
        val nv = Palette(h, (s * 0.18f).coerceIn(0.04f, 0.16f))
        val err = Palette(2f, 0.78f)
        return if (!dark) lightColorScheme(
            primary = p.tone(40), onPrimary = p.tone(100), primaryContainer = p.tone(90), onPrimaryContainer = p.tone(10),
            inversePrimary = p.tone(80),
            secondary = sec.tone(40), onSecondary = sec.tone(100), secondaryContainer = sec.tone(90), onSecondaryContainer = sec.tone(10),
            tertiary = ter.tone(40), onTertiary = ter.tone(100), tertiaryContainer = ter.tone(90), onTertiaryContainer = ter.tone(10),
            error = err.tone(40), onError = err.tone(100), errorContainer = err.tone(90), onErrorContainer = err.tone(10),
            background = n.tone(98), onBackground = n.tone(10),
            surface = n.tone(98), onSurface = n.tone(10), surfaceVariant = nv.tone(90), onSurfaceVariant = nv.tone(30),
            surfaceTint = p.tone(40), inverseSurface = n.tone(20), inverseOnSurface = n.tone(95),
            outline = nv.tone(50), outlineVariant = nv.tone(80), scrim = Color.Black,
            surfaceBright = n.tone(98), surfaceDim = n.tone(87),
            surfaceContainerLowest = n.tone(100), surfaceContainerLow = n.tone(96), surfaceContainer = n.tone(94),
            surfaceContainerHigh = n.tone(92), surfaceContainerHighest = n.tone(90),
        ) else {
            val bg = if (amoled) Color.Black else n.tone(6)
            darkColorScheme(
                primary = p.tone(80), onPrimary = p.tone(20), primaryContainer = p.tone(30), onPrimaryContainer = p.tone(90),
                inversePrimary = p.tone(40),
                secondary = sec.tone(80), onSecondary = sec.tone(20), secondaryContainer = sec.tone(30), onSecondaryContainer = sec.tone(90),
                tertiary = ter.tone(80), onTertiary = ter.tone(20), tertiaryContainer = ter.tone(30), onTertiaryContainer = ter.tone(90),
                error = err.tone(80), onError = err.tone(20), errorContainer = err.tone(30), onErrorContainer = err.tone(90),
                background = bg, onBackground = n.tone(90),
                surface = bg, onSurface = n.tone(90), surfaceVariant = if (amoled) n.tone(14) else nv.tone(30), onSurfaceVariant = nv.tone(80),
                surfaceTint = p.tone(80), inverseSurface = n.tone(90), inverseOnSurface = n.tone(20),
                outline = nv.tone(60), outlineVariant = nv.tone(30), scrim = Color.Black,
                surfaceBright = if (amoled) n.tone(18) else n.tone(24), surfaceDim = bg,
                surfaceContainerLowest = if (amoled) Color.Black else n.tone(4),
                surfaceContainerLow = if (amoled) n.tone(5) else n.tone(10),
                surfaceContainer = if (amoled) n.tone(8) else n.tone(12),
                surfaceContainerHigh = if (amoled) n.tone(11) else n.tone(17),
                surfaceContainerHighest = if (amoled) n.tone(14) else n.tone(22),
            )
        }
    }

    private fun ColorScheme.blackened(): ColorScheme = copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B), surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF222222),
        surfaceVariant = Color(0xFF222222),
    )

    /** Light and dark schemes for a theme spec (dynamic colors when chosen and available). */
    fun resolve(context: Context, spec: ThemeSpec): Pair<ColorScheme, ColorScheme> {
        if (spec.usesDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val light = dynamicLightColorScheme(context)
            val dark = dynamicDarkColorScheme(context).let { if (spec.amoled) it.blackened() else it }
            return light to dark
        }
        val seed = Color(spec.seed.toInt())
        return fromSeed(seed, dark = false, amoled = false) to fromSeed(seed, dark = true, amoled = spec.amoled)
    }
}
