package com.gios.brightsteps

import com.gios.brightsteps.steps.Sample
import com.gios.brightsteps.steps.StepMath
import com.gios.brightsteps.steps.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test

class StepMathTest {

    private val ny = ZoneId.of("America/New_York")

    /** Wall-clock millis for a local date/time in the New York zone. */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ny).toInstant().toEpochMilli()

    /** A sample whose elapsedRealtime tracks wall time from an arbitrary boot origin. */
    private fun s(counter: Long, wallMs: Long, bootMs: Long = at(2026, 6, 1, 0)): Sample =
        Sample(counter, wallMs, wallMs - bootMs)

    @Test
    fun `steps within one hour land in that hour`() {
        val samples = listOf(
            s(100, at(2026, 6, 1, 9, 0)),
            s(400, at(2026, 6, 1, 9, 30)),
        )
        assertEquals(300L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 1), ny))
    }

    @Test
    fun `a walk across midnight splits proportionally between the two days`() {
        // 400 steps over an hour centered on midnight: half before, half after.
        val samples = listOf(
            s(0, at(2026, 6, 1, 23, 30)),
            s(400, at(2026, 6, 2, 0, 30)),
        )
        val totals = StepMath.dayTotals(samples, ny)
        assertEquals(200L, totals[LocalDate.of(2026, 6, 1)])
        assertEquals(200L, totals[LocalDate.of(2026, 6, 2)])
    }

    @Test
    fun `a reboot is read as steps since the reboot, not a negative delta`() {
        // Counter climbs, phone reboots (counter resets), then climbs again.
        val boot1 = at(2026, 6, 1, 0)
        val boot2 = at(2026, 6, 1, 12)
        val samples = listOf(
            Sample(5000, at(2026, 6, 1, 8, 0), at(2026, 6, 1, 8, 0) - boot1),
            Sample(6000, at(2026, 6, 1, 9, 0), at(2026, 6, 1, 9, 0) - boot1),
            // reboot between 9:00 and 13:00; counter now counts from 0 again
            Sample(700, at(2026, 6, 1, 13, 0), at(2026, 6, 1, 13, 0) - boot2),
            Sample(900, at(2026, 6, 1, 14, 0), at(2026, 6, 1, 14, 0) - boot2),
        )
        // 1000 (8-9) + 700 (post-reboot pair) + 200 (13-14) = 1900, none negative.
        assertEquals(1900L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 1), ny))
    }

    @Test
    fun `a gap longer than the ceiling is dropped`() {
        val samples = listOf(
            s(0, at(2026, 6, 1, 9, 0)),
            s(5000, at(2026, 6, 3, 9, 0)), // 48h later, over MAX_GAP_MS
        )
        assertEquals(0L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 1), ny))
        assertEquals(0L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 3), ny))
    }

    @Test
    fun `an impossible single delta is a fault and dropped`() {
        val samples = listOf(
            s(0, at(2026, 6, 1, 9, 0)),
            s(StepMath.MAX_DELTA + 1, at(2026, 6, 1, 9, 15)),
        )
        assertEquals(0L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 1), ny))
    }

    @Test
    fun `todayTotal folds in a live reading past the last stored sample`() {
        val stored = listOf(
            s(1000, at(2026, 6, 1, 8, 0)),
            s(1500, at(2026, 6, 1, 9, 0)),
        )
        val now = s(1800, at(2026, 6, 1, 10, 0))
        // 500 (8-9) + 300 (9-now) = 800
        assertEquals(800L, StepMath.todayTotal(stored, now, ny))
    }

    @Test
    fun `a spring-forward day still tiles hour by hour`() {
        // 2026-03-08 is a 23-hour day in New York (02:00 -> 03:00 skipped).
        val samples = listOf(
            s(0, at(2026, 3, 8, 0, 30)),
            s(2300, at(2026, 3, 8, 23, 30)),
        )
        val totals = StepMath.dayTotals(samples, ny)
        // Everything belongs to the 8th; nothing bleeds onto neighbouring days. The daily sum
        // can drift a few steps from 2300 because each of the ~23 hour buckets is rounded to a
        // whole step — that is fine for a step count, so the assertion is tolerant.
        assertEquals(setOf(LocalDate.of(2026, 3, 8)), totals.keys)
        // Exact now. The old code rounded each of the ~23 hour buckets to a whole step before
        // summing them, so the day drifted by up to half a step per hour and the assertion here
        // had to be written loose. Totalling the unrounded fractions and rounding once removes
        // the drift entirely.
        assertEquals(2300L, totals[LocalDate.of(2026, 3, 8)])
    }

    @Test
    fun `a reboot the counter cannot see does not lose the steps before it`() {
        val boot1 = at(2026, 6, 1, 0)
        val boot2 = at(2026, 6, 1, 12)
        val samples = listOf(
            // 8,000 steps on the clock by late morning...
            Sample(8000, at(2026, 6, 1, 11, 0), at(2026, 6, 1, 11, 0) - boot1),
            // ...phone reboots at noon, and by evening the fresh counter has climbed past 8,000.
            Sample(9000, at(2026, 6, 1, 20, 0), at(2026, 6, 1, 20, 0) - boot2),
        )
        // The counter never fell, so the old "counter went down = reboot" rule saw an ordinary
        // delta of 9000 - 8000 and credited 1,000 steps for the day. Eight thousand vanished.
        // elapsedRealtime *did* fall, which is what actually proves a reboot happened.
        assertEquals(9000L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 1), ny))
        assertTrue(StepMath.rebooted(samples[0], samples[1]))
    }

    @Test
    fun `steps after a reboot are credited to the time since the boot`() {
        val boot1 = at(2026, 6, 1, 0)
        val boot2 = at(2026, 6, 2, 6) // switched back on at 06:00 on the 2nd
        val samples = listOf(
            Sample(3000, at(2026, 6, 1, 20, 0), at(2026, 6, 1, 20, 0) - boot1),
            Sample(600, at(2026, 6, 2, 8, 0), at(2026, 6, 2, 8, 0) - boot2),
        )
        val totals = StepMath.dayTotals(samples, ny)
        // All 600 were necessarily walked in the two hours since the boot. Spreading them across
        // the whole 20:00-08:00 interval would have put a third of them on the 1st — hours the
        // phone spent switched off.
        assertEquals(600L, totals[LocalDate.of(2026, 6, 2)])
        assertEquals(null, totals[LocalDate.of(2026, 6, 1)])
    }

    @Test
    fun `an ordinary quiet interval is not read as a reboot`() {
        // Wall time and uptime advance together; the counter rises. Nothing here is a reboot,
        // and the slack has to be wide enough that a clock nudge does not trip it.
        val a = s(1000, at(2026, 6, 1, 9, 0))
        val b = s(1010, at(2026, 6, 1, 9, 15))
        assertTrue(!StepMath.rebooted(a, b))
    }

    @Test
    fun `a thin spread over many hours is not rounded away`() {
        // Four steps across twelve hours is a third of a step an hour. Rounding each hour before
        // summing turned every one of those hours into zero, and the day's total with them.
        val samples = listOf(
            s(0, at(2026, 6, 1, 6, 0)),
            s(4, at(2026, 6, 1, 18, 0)),
        )
        assertEquals(4L, StepMath.dayTotal(samples, LocalDate.of(2026, 6, 1), ny))
    }

    @Test
    fun `the audit says what was thrown away and why`() {
        val samples = listOf(
            s(0, at(2026, 6, 1, 9, 0)),
            s(5000, at(2026, 6, 3, 9, 0)), // 48h later, past MAX_GAP_MS
        )
        val audit = StepMath.audit(samples)
        assertEquals(1, audit.size)
        assertEquals(Verdict.GAP_TOO_LONG, audit[0].verdict)
        assertEquals(5000L, audit[0].rawDelta)
        assertEquals(0L, audit[0].credited)
    }

    @Test
    fun `the audit credits an ordinary interval in full`() {
        val samples = listOf(
            s(100, at(2026, 6, 1, 9, 0)),
            s(400, at(2026, 6, 1, 9, 30)),
        )
        val audit = StepMath.audit(samples)
        assertEquals(Verdict.OK, audit[0].verdict)
        assertEquals(300L, audit[0].credited)
    }
}
