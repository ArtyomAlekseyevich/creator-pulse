package dev.creatorpulse.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sliding-window limiter: at most [maxPerWindow] permits per [windowMs]. Callers suspend until a
 * permit frees up, so bursts are spread out instead of hitting Roblox's 429s. A cooldown can be
 * imposed (from a Retry-After header) which blocks every caller until it expires.
 */
class RateBucket(private val maxPerWindow: Int, private val windowMs: Long) {
    private val mutex = Mutex()
    private val stamps = ArrayDeque<Long>()
    @Volatile private var cooldownUntil = 0L

    suspend fun acquire() {
        while (true) {
            val wait = mutex.withLock {
                val now = System.currentTimeMillis()
                while (stamps.isNotEmpty() && stamps.first() <= now - windowMs) stamps.removeFirst()
                when {
                    now < cooldownUntil -> cooldownUntil - now
                    stamps.size < maxPerWindow -> { stamps.addLast(now); 0L }
                    else -> stamps.first() + windowMs - now
                }
            }
            if (wait <= 0L) return
            delay(wait.coerceIn(50L, 15_000L))
        }
    }

    fun cooldown(ms: Long) { cooldownUntil = maxOf(cooldownUntil, System.currentTimeMillis() + ms) }
    val isCoolingDown: Boolean get() = System.currentTimeMillis() < cooldownUntil
    val cooldownRemainingMs: Long get() = (cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0L)
}
