package com.gios.brightsteps

import com.gios.brightsteps.steps.Sample
import com.gios.brightsteps.steps.StepMath
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
        val totals = StepMath.dayTotals(StepMath.hourlyBuckets(samples, ny), ny)
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
        val totals = StepMath.dayTotals(StepMath.hourlyBuckets(samples, ny), ny)
        // Everything belongs to the 8th; nothing bleeds onto neighbouring days. The daily sum
        // can drift a few steps from 2300 because each of the ~23 hour buckets is rounded to a
        // whole step — that is fine for a step count, so the assertion is tolerant.
        assertEquals(setOf(LocalDate.of(2026, 3, 8)), totals.keys)
        assertTrue(totals[LocalDate.of(2026, 3, 8)]!! in 2275..2325)
    }
}
