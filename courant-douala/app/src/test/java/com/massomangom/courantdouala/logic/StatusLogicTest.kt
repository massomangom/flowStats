package com.massomangom.courantdouala.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusLogicTest {
    private val t0 = 1_700_000_000_000L

    @Test fun firstReportCreatesState() {
        val e = applyReport(null, on = false, now = t0)
        assertEquals(false, e.on)
        assertEquals(1, e.n)
        assertEquals(t0, e.since)
        assertEquals(State.OFF, stateOf(e, t0 + MINUTE))
    }

    @Test fun sameReportConfirms() {
        val e = applyReport(applyReport(null, false, t0), false, t0 + 10 * MINUTE)
        assertEquals(2, e.n)
        assertEquals(t0, e.since)
        assertEquals(t0 + 10 * MINUTE, e.last)
        assertEquals(1, e.history.size)
    }

    @Test fun oppositeReportFlips() {
        val e = applyReport(applyReport(null, false, t0), true, t0 + 2 * HOUR)
        assertEquals(true, e.on)
        assertEquals(1, e.n)
        assertEquals(t0 + 2 * HOUR, e.since)
        assertEquals(2, e.history.size)
    }

    @Test fun staleBecomesUnknownAndNewPeriod() {
        val e = applyReport(null, false, t0)
        assertEquals(State.UNKNOWN, stateOf(e, t0 + 15 * HOUR))
        val again = applyReport(e, false, t0 + 15 * HOUR)
        assertEquals(1, again.n)
        assertEquals(t0 + 15 * HOUR, again.since)
    }

    @Test fun offDurationCountsClosedAndOpenCuts() {
        var e = applyReport(null, false, t0)
        e = applyReport(e, true, t0 + 3 * HOUR)
        e = applyReport(e, false, t0 + 5 * HOUR)
        // 3 h fermées + 1 h en cours
        assertEquals(4 * HOUR, offDuration(e, t0 + 6 * HOUR))
    }

    @Test fun offDurationClipsToWindow() {
        var e = applyReport(null, false, t0)
        e = applyReport(e, true, t0 + 10 * HOUR)
        assertEquals(2 * HOUR, offDuration(e, t0 + 10 * HOUR, window = 2 * HOUR))
    }

    @Test fun historyIsBounded() {
        var e: Entry? = null
        repeat(40) { i -> e = applyReport(e, i % 2 == 0, t0 + i * MINUTE) }
        assertEquals(MAX_HISTORY, e!!.history.size)
    }

    @Test fun mapRoundTrip() {
        val e = applyReport(applyReport(null, false, t0), true, t0 + HOUR)
        assertEquals(e, entryFromMap(e.toMap()))
        assertNull(entryFromMap("garbage"))
        assertNull(entryFromMap(mapOf("on" to "x")))
    }

    @Test fun cooldown() {
        assertEquals(0, cooldownRemaining(0, null, true, t0))
        assertEquals(14 * MINUTE, cooldownRemaining(t0, false, false, t0 + MINUTE))
        assertEquals(0, cooldownRemaining(t0, false, true, t0 + MINUTE))
    }

    @Test fun formatting() {
        assertEquals("45 min", formatDuration(45 * MINUTE))
        assertEquals("2 h 05", formatDuration(2 * HOUR + 5 * MINUTE))
        assertEquals("3 h", formatDuration(3 * HOUR))
        assertEquals("à l'instant", formatAgo(10_000))
    }
}
