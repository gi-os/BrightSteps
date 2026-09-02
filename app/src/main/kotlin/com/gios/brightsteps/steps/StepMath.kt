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

/** Why a pair of readings was credited, discounted, or thrown away. Read by the diagnostics screen. */
enum class Verdict {
    /** A normal forward delta, credited in full. */
    OK,

    /** The phone rebooted between the readings; the later counter is itself the post-boot total. */
    REBOOT,

    /** Two readings the same instant apart, or out of order. Nothing to credit. */
    EMPTY,

    /** Longer than [StepMath.MAX_GAP_MS] between readings — no honest way to place the steps. */
    GAP_TOO_LONG,

    /** A delta past [StepMath.MAX_DELTA]. A sensor fault, not a marathon. */
    IMPLAUSIBLE,
}

/**
 * One interval between consecutive readings, and what became of it. The audit exists because
 * "the count seems low" is unanswerable without knowing which intervals were dropped and how
 * many steps went with them.
 */
data class IntervalAudit(
    val startMs: Long,
    val endMs: Long,
    val rawDelta: Long,
    val credited: Long,
    val verdict: Verdict,
)

/**
 * Turns a run of raw counter readings into steps placed in time. Deliberately free of any
 * Android import so it runs on the JVM under test — every hard part here is arithmetic, not
 * sensing, and the arithmetic is where steps land on the wrong day.
 *
 * The rules are the ones LightNotebook arrived at the hard way, plus the two corrections that
 * v1.1 was built for:
 *  - a reboot is detected from **elapsedRealtime**, not from the counter falling. The counter
 *    only falls if you walked fewer steps since the boot than you had before it; walk more and
 *    the old heuristic quietly credits `b − a` and loses everything up to `a`. This was the
 *    largest single source of missing steps;
 *  - steps are totalled per day from **unrounded** hour fractions. Rounding each hour first
 *    dropped every hour holding less than half a step, and a long quiet interval is made
 *    entirely of such hours;
 *  - a delta is spread across the wall-clock interval it covers, split at each hour boundary
 *    so a walk that straddles midnight lands partly on each day and DST days still tile. When
 *    the phone was off for part of that interval, only the powered-on tail is used;
 *  - a gap longer than [MAX_GAP_MS] is dropped, and a single delta above [MAX_DELTA] is a
 *    fault. Both now say so in the audit rather than vanishing.
 */
object StepMath {
    /** Longer than this between two readings and the interval is unaccountable. */
    const val MAX_GAP_MS: Long = 36L * 60 * 60 * 1000

    /** No real interval between samples produces this many steps; treat it as a glitch. */
    const val MAX_DELTA: Long = 200_000

    /**
     * How far wall time may outrun uptime between two readings before we call it a power-off.
     * Uptime and wall time advance together on a running phone; only a shutdown, or an NTP
     * correction, separates them. Two minutes clears any plausible clock nudge.
     */
    const val REBOOT_SLACK_MS: Long = 2L * 60 * 1000

    /**
     * True when the phone rebooted between [a] and [b].
     *
     * `elapsedRealtime` counts from boot and never runs backwards while the phone is up, so a
     * fall is proof. A phone that was off also loses uptime it should have gained, which is the
     * second test. The counter falling is kept only as a last resort: it is the weakest signal
     * of the three and the one the old code relied on alone.
     */
    fun rebooted(a: Sample, b: Sample): Boolean {
        if (b.elapsedMs < a.elapsedMs) return true
        val wallAdvance = b.wallMs - a.wallMs
        val upAdvance = b.elapsedMs - a.elapsedMs
        if (wallAdvance - upAdvance > REBOOT_SLACK_MS) return true
        return b.counter < a.counter
    }

    /**
     * Classify every consecutive pair. [hourlyFractions] credits exactly the intervals this
     * returns as [Verdict.OK] or [Verdict.REBOOT]; the diagnostics screen shows the rest.
     */
    fun audit(samples: List<Sample>): List<IntervalAudit> {
        val ordered = samples.sortedBy { it.wallMs }
        val out = ArrayList<IntervalAudit>(maxOf(0, ordered.size - 1))
        for (i in 1 until ordered.size) {
            val a = ordered[i - 1]
            val b = ordered[i]
            val reboot = rebooted(a, b)
            val raw = if (reboot) b.counter else b.counter - a.counter
            val dtWall = b.wallMs - a.wallMs
            val verdict = when {
                dtWall <= 0 -> Verdict.EMPTY
                dtWall > MAX_GAP_MS -> Verdict.GAP_TOO_LONG
                raw <= 0 -> Verdict.EMPTY
                raw > MAX_DELTA -> Verdict.IMPLAUSIBLE
                reboot -> Verdict.REBOOT
                else -> Verdict.OK
            }
            val credited = if (verdict == Verdict.OK || verdict == Verdict.REBOOT) raw else 0L
            out += IntervalAudit(a.wallMs, b.wallMs, raw, credited, verdict)
        }
        return out
    }

    /**
     * Steps per clock hour as unrounded fractions, keyed by the epoch-millis start of each local
     * hour. Going through [zone] rather than dividing millis is what makes a 23- or 25-hour DST
     * day tile exactly. This is the one true accumulator: everything else rounds a copy of it.
     */
    fun hourlyFractions(samples: List<Sample>, zone: ZoneId): Map<Long, Double> {
        val acc = HashMap<Long, Double>()
        val ordered = samples.sortedBy { it.wallMs }
        for (i in 1 until ordered.size) {
            val a = ordered[i - 1]
            val b = ordered[i]
            val reboot = rebooted(a, b)
            val dtWall = b.wallMs - a.wallMs
            if (dtWall <= 0 || dtWall > MAX_GAP_MS) continue
            val delta = if (reboot) b.counter else b.counter - a.counter
            if (delta <= 0 || delta > MAX_DELTA) continue
            // After a reboot the steps can only have happened since the phone came back, so
            // credit the post-boot tail rather than smearing them over hours the phone spent
            // switched off. `b.wallMs - b.elapsedMs` is the boot instant on the wall clock.
            val start = if (reboot) maxOf(a.wallMs, b.wallMs - b.elapsedMs) else a.wallMs
            if (start >= b.wallMs) {
                // A reboot inside the same millisecond as the reading: put it all in that hour.
                addHour(acc, b.wallMs, delta.toDouble(), zone)
                continue
            }
            spread(acc, start, b.wallMs, delta.toDouble(), zone)
        }
        return acc
    }

    /**
     * Steps per clock hour, rounded — for display only. Day totals must not be built from this;
     * see [dayTotals].
     */
    fun hourlyBuckets(samples: List<Sample>, zone: ZoneId): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for ((hour, v) in hourlyFractions(samples, zone)) {
            val r = Math.round(v)
            if (r > 0) out[hour] = r
        }
        return out
    }

    /**
     * Total steps for each local day. Summed from the unrounded fractions and rounded once, so
     * a quiet stretch spread thinly over many hours still adds up instead of rounding away.
     */
    fun dayTotals(samples: List<Sample>, zone: ZoneId): Map<LocalDate, Long> {
        val acc = HashMap<LocalDate, Double>()
        for ((hourStart, steps) in hourlyFractions(samples, zone)) {
            val date = Instant.ofEpochMilli(hourStart).atZone(zone).toLocalDate()
            acc[date] = (acc[date] ?: 0.0) + steps
        }
        val out = HashMap<LocalDate, Long>(acc.size)
        for ((date, v) in acc) out[date] = Math.round(v)
        return out
    }

    /** Steps recorded on [day] in [zone]. */
    fun dayTotal(samples: List<Sample>, day: LocalDate, zone: ZoneId): Long =
        dayTotals(samples, zone)[day] ?: 0L

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

    /** Put all of [steps] in the local hour containing [atMs]. */
    private fun addHour(acc: HashMap<Long, Double>, atMs: Long, steps: Double, zone: ZoneId) {
        val hourStartMs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(atMs), zone)
            .truncatedTo(ChronoUnit.HOURS)
            .toInstant()
            .toEpochMilli()
        acc[hourStartMs] = (acc[hourStartMs] ?: 0.0) + steps
    }
}
