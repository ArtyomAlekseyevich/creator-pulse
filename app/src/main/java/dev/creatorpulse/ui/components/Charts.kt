package dev.creatorpulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.creatorpulse.data.Point

/** Simple time-series chart drawn with Canvas. Points must be sorted by time. */
@Composable
fun TimeSeriesChart(
    points: List<Point>,
    color: Color,
    bars: Boolean,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Int = 160,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
            Text("No data for this range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val maxV = points.maxOf { it.value }.let { if (it <= 0.0) 1.0 else it }
    val minV = 0.0
    val maxLabel = formatValue(maxV)
    val midLabel = formatValue(maxV / 2)
    val firstT = formatTime(points.first().time)
    val lastT = formatTime(points.last().time)
    val latest = points.last()

    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val leftPad = measurer.measure(maxLabel, labelStyle).size.width.toFloat() + 8f
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val w = size.width - leftPad
        val h = size.height - bottomPad - topPad
        val n = points.size
        fun x(i: Int) = leftPad + if (n == 1) w / 2 else w * i / (n - 1).toFloat()
        fun y(v: Double) = topPad + (h - (h * ((v - minV) / (maxV - minV))).toFloat())

        // grid + y labels
        listOf(1.0, 0.5, 0.0).forEach { f ->
            val yy = topPad + h * (1 - f).toFloat()
            drawLine(gridColor, Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1f)
        }
        drawText(measurer, maxLabel, Offset(0f, topPad - 6f), labelStyle)
        drawText(measurer, midLabel, Offset(0f, topPad + h / 2 - 6f), labelStyle)

        if (bars) {
            val slot = w / n
            val bw = (slot * 0.62f).coerceAtLeast(2f)
            points.forEachIndexed { i, p ->
                val cx = leftPad + slot * i + slot / 2
                val top = y(p.value)
                drawRoundRect(color, topLeft = Offset(cx - bw / 2, top), size = Size(bw, (topPad + h - top).coerceAtLeast(0f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
            }
        } else {
            val path = Path()
            val fill = Path()
            points.forEachIndexed { i, p ->
                val px = x(i); val py = y(p.value)
                if (i == 0) { path.moveTo(px, py); fill.moveTo(px, topPad + h); fill.lineTo(px, py) } else { path.lineTo(px, py); fill.lineTo(px, py) }
            }
            fill.lineTo(x(n - 1), topPad + h); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.0f)), startY = topPad, endY = topPad + h))
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(n - 1), y(latest.value)))
        }

        // x labels
        drawText(measurer, firstT, Offset(leftPad, size.height - bottomPad + 4f), labelStyle)
        val lastW = measurer.measure(lastT, labelStyle).size.width
        drawText(measurer, lastT, Offset(size.width - lastW, size.height - bottomPad + 4f), labelStyle)
    }
}
