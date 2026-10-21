package dev.creatorpulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.creatorpulse.data.THEME_PRESETS
import dev.creatorpulse.data.ThemeMode
import dev.creatorpulse.data.ThemeSpec
import dev.creatorpulse.ui.theme.SchemeBuilder

/** The "Appearance" settings card: mode, palette, custom accent, OLED black, chart and widget options. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceCard(spec: ThemeSpec, onChange: ((ThemeSpec) -> ThemeSpec) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))

            // Mode
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(selected = spec.mode == m, onClick = { onChange { it.copy(mode = m) } }, shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.label) }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Dynamic color
            if (spec.dynamicAvailable) {
                SettingSwitch("Material You colors", "Pull the palette from your wallpaper (Android 12+).", spec.dynamic) { on -> onChange { it.copy(dynamic = on) } }
            }

            // Presets
            Text(
                if (spec.usesDynamic) "Accent palette · tap one to switch off Material You" else "Accent palette",
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
            val presetsEnabled = !spec.usesDynamic
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                THEME_PRESETS.forEach { p ->
                    Swatch(
                        color = Color(p.seed.toInt()), label = p.name, selected = presetsEnabled && spec.presetId == p.id, dimmed = !presetsEnabled,
                        onClick = { onChange { it.copy(dynamic = false, presetId = p.id, seed = p.seed) } },
                    )
                }
                Swatch(
                    color = Color(spec.seed.toInt()), label = "Custom", selected = presetsEnabled && spec.presetId == "custom", dimmed = !presetsEnabled, rainbow = true,
                    onClick = { onChange { it.copy(dynamic = false, presetId = "custom") } },
                )
            }
            if (presetsEnabled && spec.presetId == "custom") {
                Spacer(Modifier.height(8.dp))
                CustomColorPicker(seed = Color(spec.seed.toInt())) { c -> onChange { it.copy(seed = c.toArgb().toLong() and 0xFFFFFFFFL) } }
            }
            Spacer(Modifier.height(10.dp))

            // Preview
            SchemePreview()
            Spacer(Modifier.height(6.dp))

            SettingSwitch("Pure black dark theme", "True black backgrounds for OLED screens.", spec.amoled) { on -> onChange { it.copy(amoled = on) } }
            SettingSwitch("Accent-colored charts", "Charts follow the theme instead of the fixed per-metric colors.", spec.accentCharts) { on -> onChange { it.copy(accentCharts = on) } }

            Spacer(Modifier.height(6.dp))
            Text("Widget background opacity · ${spec.widgetOpacity}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = spec.widgetOpacity.toFloat(),
                onValueChange = { v -> onChange { it.copy(widgetOpacity = v.toInt()) } },
                valueRange = 20f..100f,
                steps = 15,
            )
            Text("Widgets use the same palette and light/dark mode as the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, dimmed: Boolean, onClick: () -> Unit, rainbow: Boolean = false) {
    val alpha = if (dimmed) 0.55f else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(2.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .then(if (rainbow) Modifier.background(Brush.sweepGradient(RAINBOW), alpha = alpha) else Modifier.background(color.copy(alpha = alpha)))
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, null, tint = if (rainbow) Color.White else contrastOn(color), modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 1)
    }
}

private fun contrastOn(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorPicker(seed: Color, onSeed: (Color) -> Unit) {
    val hsl = remember(seed) { SchemeBuilder.toHsl(seed) }
    var hue by remember(seed) { mutableStateOf(hsl[0]) }
    var sat by remember(seed) { mutableStateOf(hsl[1].coerceIn(0.1f, 1f)) }
    var hex by remember(seed) { mutableStateOf(String.format("%06X", seed.toArgb() and 0xFFFFFF)) }
    val live = SchemeBuilder.hsl(hue, sat, 0.5f)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = hue, onValueChange = { hue = it }, onValueChangeFinished = { onSeed(SchemeBuilder.hsl(hue, sat, 0.5f)) }, valueRange = 0f..360f,
            track = { SegmentTrack { f -> SchemeBuilder.hsl(f * 360f, 1f, 0.5f) } },
            thumb = { Box(Modifier.size(24.dp).clip(CircleShape).background(live).border(2.dp, Color.White, CircleShape)) },
        )
        Text("Vibrance", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = sat, onValueChange = { sat = it }, onValueChangeFinished = { onSeed(SchemeBuilder.hsl(hue, sat, 0.5f)) }, valueRange = 0.1f..1f,
            track = { SegmentTrack { f -> SchemeBuilder.hsl(hue, 0.1f + 0.9f * f, 0.5f) } },
            thumb = { Box(Modifier.size(24.dp).clip(CircleShape).background(live).border(2.dp, Color.White, CircleShape)) },
            colors = SliderDefaults.colors(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(live))
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = hex,
                onValueChange = { v ->
                    val clean = v.trimStart('#').uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    hex = clean
                    if (clean.length == 6) runCatching { Color(0xFF000000L or clean.toLong(16)).also { onSeed(it) } }
                },
                prefix = { Text("#") }, label = { Text("Hex") }, singleLine = true, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Slider track drawn as solid color segments: no gradient shaders, so it renders the same on every GPU. */
@Composable
private fun SegmentTrack(colorAt: (Float) -> Color) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
        val n = 48
        val w = size.width / n
        for (i in 0 until n) {
            drawRect(color = colorAt(i / (n - 1).toFloat()), topLeft = androidx.compose.ui.geometry.Offset(i * w, 0f), size = androidx.compose.ui.geometry.Size(w + 1f, size.height))
        }
    }
}

@Composable
private fun SchemePreview() {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerLow).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Primary" to cs.primary, "Container" to cs.primaryContainer, "Secondary" to cs.secondary, "Tertiary" to cs.tertiary, "Surface" to cs.surfaceContainerHighest).forEach { (name, c) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(c).border(1.dp, cs.outlineVariant, RoundedCornerShape(8.dp)))
                Text(name, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
