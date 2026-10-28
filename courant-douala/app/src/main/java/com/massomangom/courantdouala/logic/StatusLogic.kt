package com.massomangom.courantdouala.logic

/**
 * Logique pure (sans Android) : comment les signalements deviennent un statut.
 * Testée dans StatusLogicTest.
 */

enum class Utility(val key: String) { POWER("p"), WATER("w") }

enum class State { ON, OFF, UNKNOWN }

/** Changement d'état : coupure (on=false) ou retour (on=true) à l'instant t (ms). */
data class Flip(val on: Boolean, val t: Long)

/**
 * État d'un service dans un quartier.
 * since : début de l'état actuel ; last : dernière confirmation ; n : nb de confirmations.
 */
data class Entry(
    val on: Boolean,
    val since: Long,
    val last: Long,
    val n: Int,
    val history: List<Flip>,
)

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE
const val DAY = 24 * HOUR
const val MAX_HISTORY = 16

/** Au-delà, sans nouvelle confirmation, on ne sait plus. Une coupure non confirmée vieillit plus vite. */
fun staleAfter(on: Boolean): Long = if (on) DAY else 14 * HOUR

fun isStale(e: Entry, now: Long): Boolean = now - e.last > staleAfter(e.on)

fun stateOf(e: Entry?, now: Long): State = when {
    e == null || isStale(e, now) -> State.UNKNOWN
    e.on -> State.ON
    else -> State.OFF
}

/** Intègre un signalement : confirmation si même état récent, sinon nouvel état. */
fun applyReport(old: Entry?, on: Boolean, now: Long): Entry =
    if (old != null && old.on == on && !isStale(old, now)) {
        old.copy(last = now, n = old.n + 1)
    } else {
        Entry(
            on = on,
            since = now,
            last = now,
            n = 1,
            history = ((old?.history ?: emptyList()) + Flip(on, now))
                .filter { now - it.t <= 2 * DAY }
                .takeLast(MAX_HISTORY),
        )
    }

/** Durée sans service sur les dernières [window] ms (ex. "9 h sans courant aujourd'hui"). */
fun offDuration(e: Entry?, now: Long, window: Long = DAY): Long {
    if (e == null) return 0
    val start = now - window
    val flips = e.history.filter { it.t <= now }.sortedBy { it.t }
    var total = 0L
    for (i in flips.indices) {
        val f = flips[i]
        if (f.on) continue
        val end = if (i + 1 < flips.size) {
            flips[i + 1].t
        } else {
            // Coupure en cours : on ne compte pas au-delà de sa validité.
            minOf(now, e.last + staleAfter(false))
        }
        val s = maxOf(f.t, start)
        if (end > s) total += end - s
    }
    return total
}

/** "2 h 15", "45 min", "à l'instant". */
fun formatDuration(ms: Long): String {
    val minutes = ms / MINUTE
    return when {
        minutes < 1 -> "moins d'une minute"
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h h" else "$h h ${m.toString().padStart(2, '0')}"
        }
        else -> "${minutes / (24 * 60)} jours"
    }
}

fun formatAgo(ms: Long): String = if (ms < MINUTE) "à l'instant" else "il y a ${formatDuration(ms)}"

/** Anti-abus local : délai minimal entre deux signalements d'un même appareil. */
fun cooldownRemaining(lastReportAt: Long, lastReportOn: Boolean?, on: Boolean, now: Long): Long {
    if (lastReportOn == null) return 0
    val wait = if (lastReportOn == on) 15 * MINUTE else 1 * MINUTE
    return maxOf(0, lastReportAt + wait - now)
}

// --- Sérialisation Firestore (Map) : pas de réflexion, compatible R8. ---

fun Entry.toMap(): Map<String, Any> = mapOf(
    "on" to on,
    "since" to since,
    "last" to last,
    "n" to n,
    "h" to history.map { mapOf("on" to it.on, "t" to it.t) },
)

fun entryFromMap(m: Any?): Entry? {
    if (m !is Map<*, *>) return null
    val on = m["on"] as? Boolean ?: return null
    val since = (m["since"] as? Number)?.toLong() ?: return null
    val last = (m["last"] as? Number)?.toLong() ?: since
    val n = (m["n"] as? Number)?.toInt() ?: 1
    val h = (m["h"] as? List<*>)?.mapNotNull { f ->
        if (f !is Map<*, *>) return@mapNotNull null
        val fon = f["on"] as? Boolean ?: return@mapNotNull null
        val t = (f["t"] as? Number)?.toLong() ?: return@mapNotNull null
        Flip(fon, t)
    } ?: emptyList()
    return Entry(on, since, last, n, h)
}
