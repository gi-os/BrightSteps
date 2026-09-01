package com.gios.brightsteps.steps

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * One reading of the hardware step counter.
 *
 * [counter] is the sensor's cumulative value — steps since the phone last booted, which
 * `TYPE_STEP_COUNTER` accumulates in the sensor hub whether or not this process is alive.
 * [wallMs] is `System.currentTimeMillis()` at the read; [elapsedMs] is
 * `SystemClock.elapsedRealtime()`. Both clocks are kept because they disagree in exactly
 * the case that matters: a reboot resets the counter *and* elapsedRealtime, while wall time
 * keeps running.
 */
data class Sample(val counter: Long, val wallMs: Long, val elapsedMs: Long)

/**
 * Turns a run of raw counter readings into steps placed in time. Deliberately free of any
 * Android import so it runs on the JVM under test — every hard part here is arithmetic, not
 * sensing, and the arithmetic is where steps land on the wrong day.
 *
 * The rules are the ones LightNotebook arrived at the hard way:
 *  - a reboot shows up as the counter going *down*; the pair is read as steps-since-reboot;
 *  - a delta is spread across the wall-clock interval it covers, split at each hour boundary
 *    so a walk that straddles midnight lands partly on each day and DST days still tile;
 *  - a gap longer than [MAX_GAP_MS] is dropped — the phone was off or unsampled and there is
 *    no honest way to attribute it;
 *  - a single delta above [MAX_DELTA] is a fault, not a marathon, and is dropped.
 */
object StepMath {
    /** Longer than this between two readings and the interval is unaccountable. */
    const val MAX_GAP_MS: Long = 36L * 60 * 60 * 1000

    /** No real interval between samples produces this many steps; treat it as a glitch. */
    const val MAX_DELTA: Long = 200_000

    /**
     * Steps per clock hour, keyed by the epoch-millis start of each local hour. Going through
     * [zone] rather than dividing millis is what makes a 23- or 25-hour DST day tile exactly.
     */
    fun hourlyBuckets(samples: List<Sample>, zone: ZoneId): Map<Long, Long> {
        val acc = HashMap<Long, Double>()
        val ordered = samples.sortedBy { it.wallMs }
        for (i in 1 until ordered.size) {
            val a = ordered[i - 1]
            val b = ordered[i]
            val dtWall = b.wallMs - a.wallMs
            if (dtWall <= 0) continue
            if (dtWall > MAX_GAP_MS) continue
            // A reboot resets both the counter and elapsedRealtime. If the counter fell, the
            // later reading is itself the step total since the reboot; the tail before the
            // reboot is gone and unknowable.
            val delta = if (b.counter >= a.counter) b.counter - a.counter else b.counter
            if (delta <= 0 || delta > MAX_DELTA) continue
            spread(acc, a.wallMs, b.wallMs, delta.toDouble(), zone)
        }
        val out = HashMap<Long, Long>(acc.size)
        for ((hour, v) in acc) {
            val r = Math.round(v)
            if (r > 0) out[hour] = r
        }
        return out
    }

    /** Total steps for each local day covered by [buckets]. */
    fun dayTotals(buckets: Map<Long, Long>, zone: ZoneId): Map<LocalDate, Long> {
        val out = HashMap<LocalDate, Long>()
        for ((hourStart, steps) in buckets) {
            val date = Instant.ofEpochMilli(hourStart).atZone(zone).toLocalDate()
            out[date] = (out[date] ?: 0L) + steps
        }
        return out
    }

    /** Steps recorded on [day] in [zone]. */
    fun dayTotal(samples: List<Sample>, day: LocalDate, zone: ZoneId): Long =
        dayTotals(hourlyBuckets(samples, zone), zone)[day] ?: 0L

    /**
     * Steps so far today. [now] is a fresh counter reading; appending it lets the number climb
     * on screen between the every-quarter-hour background samples.
     */
    fun todayTotal(samples: List<Sample>, now: Sample, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now.wallMs).atZone(zone).toLocalDate()
        return dayTotal(samples + now, today, zone)
    }

    /** Split [steps] over [startMs, endMs) into whole-hour buckets, proportional to time. */
    private fun spread(
        acc: HashMap<Long, Double>,
        startMs: Long,
        endMs: Long,
        steps: Double,
        zone: ZoneId,
    ) {
        val span = (endMs - startMs).toDouble()
        var cur = startMs
        while (cur < endMs) {
            val hourStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(cur), zone)
                .truncatedTo(ChronoUnit.HOURS)
            val hourStartMs = hourStart.toInstant().toEpochMilli()
            val nextHourMs = hourStart.plusHours(1).toInstant().toEpochMilli()
            val segEnd = minOf(endMs, nextHourMs)
            val frac = (segEnd - cur) / span
            acc[hourStartMs] = (acc[hourStartMs] ?: 0.0) + steps * frac
            cur = segEnd
        }
    }
}
