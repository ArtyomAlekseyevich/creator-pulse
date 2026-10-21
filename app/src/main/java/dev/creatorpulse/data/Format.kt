package dev.creatorpulse.data

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val intFmt: NumberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    fun int(n: Long): String = intFmt.format(n)
    fun int(n: Double): String = intFmt.format(n.toLong())

    fun compact(n: Double): String {
        val a = abs(n)
        return when {
            a < 1_000 -> if (n == n.toLong().toDouble()) n.toLong().toString() else String.format(Locale.US, "%.1f", n)
            a < 10_000 -> String.format(Locale.US, "%.2fK", n / 1_000)
            a < 1_000_000 -> String.format(Locale.US, "%.1fK", n / 1_000)
            a < 10_000_000 -> String.format(Locale.US, "%.2fM", n / 1_000_000)
            a < 1_000_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000)
            else -> String.format(Locale.US, "%.2fB", n / 1_000_000_000)
        }
    }

    fun compact(n: Long): String = compact(n.toDouble())

    fun robux(n: Long): String = "R$ " + int(n)
    fun robux(n: Double): String = "R$ " + int(n)
    fun robuxCompact(n: Double): String = "R$ " + compact(n)

    fun usd(v: Double): String = if (abs(v) >= 1000) "$" + compact(v) else String.format(Locale.US, "$%.2f", v)
    fun percent(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f%%", v)

    fun minutes(v: Double): String = if (v >= 60) String.format(Locale.US, "%.1fh", v / 60) else String.format(Locale.US, "%.1fm", v)

    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val shortTime = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateTime = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

    fun dateUtc(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).format(shortDate)
    fun dateLocal(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(shortDate)
    fun timeLocal(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(shortTime)
    fun dateTimeLocal(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateTime)

    fun isoToLocal(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching { dateTimeLocal(Instant.parse(iso).toEpochMilli()) }.getOrDefault(iso.take(10))
    }

    fun ago(epochMs: Long): String {
        if (epochMs <= 0) return "never"
        val d = (System.currentTimeMillis() - epochMs) / 1000
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60}m ago"
            d < 86400 -> "${d / 3600}h ago"
            else -> "${d / 86400}d ago"
        }
    }

    fun utcDay(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate()
}
