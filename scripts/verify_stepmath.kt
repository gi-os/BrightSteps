/*
 * A standalone harness for the JVM half of the step math.
 *
 * The sandbox that builds these apps has no Android SDK and no JDK 17, so `gradlew test` cannot
 * run there — but StepMath is deliberately free of Android imports, which means kotlinc alone
 * can compile and exercise it. This mirrors app/src/test/.../StepMathTest.kt case for case so
 * the arithmetic is checked before a push, and CI runs the real suite after.
 *
 *   kotlinc StepMath.kt verify_stepmath.kt -include-runtime -d /tmp/v.jar && java -jar /tmp/v.jar
 */

import com.gios.brightsteps.steps.Sample
import com.gios.brightsteps.steps.StepMath
import com.gios.brightsteps.steps.Verdict
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val ny = ZoneId.of("America/New_York")
private var failures = 0

private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
    ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ny).toInstant().toEpochMilli()

private fun s(counter: Long, wallMs: Long, bootMs: Long = at(2026, 6, 1, 0)): Sample =
    Sample(counter, wallMs, wallMs - bootMs)

private fun check(name: String, expected: Any?, actual: Any?) {
    if (expected == actual) {
        println("  ok   $name")
    } else {
        failures++
        println("  FAIL $name — expected $expected, got $actual")
    }
}

fun main() {
    println("StepMath")

    check(
        "steps within one hour land in that hour",
        300L,
        StepMath.dayTotal(
            listOf(s(100, at(2026, 6, 1, 9, 0)), s(400, at(2026, 6, 1, 9, 30))),
            LocalDate.of(2026, 6, 1),
            ny,
        ),
    )

    val midnight = StepMath.dayTotals(
        listOf(s(0, at(2026, 6, 1, 23, 30)), s(400, at(2026, 6, 2, 0, 30))),
        ny,
    )
    check("midnight walk splits — 1st", 200L, midnight[LocalDate.of(2026, 6, 1)])
    check("midnight walk splits — 2nd", 200L, midnight[LocalDate.of(2026, 6, 2)])

    val boot1 = at(2026, 6, 1, 0)
    val boot2 = at(2026, 6, 1, 12)
    check(
        "a falling counter reads as steps since the reboot",
        1900L,
        StepMath.dayTotal(
            listOf(
                Sample(5000, at(2026, 6, 1, 8, 0), at(2026, 6, 1, 8, 0) - boot1),
                Sample(6000, at(2026, 6, 1, 9, 0), at(2026, 6, 1, 9, 0) - boot1),
                Sample(700, at(2026, 6, 1, 13, 0), at(2026, 6, 1, 13, 0) - boot2),
                Sample(900, at(2026, 6, 1, 14, 0), at(2026, 6, 1, 14, 0) - boot2),
            ),
            LocalDate.of(2026, 6, 1),
            ny,
        ),
    )

    // The headline regression: a reboot the counter cannot see.
    val hidden = listOf(
        Sample(8000, at(2026, 6, 1, 11, 0), at(2026, 6, 1, 11, 0) - boot1),
        Sample(9000, at(2026, 6, 1, 20, 0), at(2026, 6, 1, 20, 0) - boot2),
    )
    check("a hidden reboot keeps its steps", 9000L, StepMath.dayTotal(hidden, LocalDate.of(2026, 6, 1), ny))
    check("a hidden reboot is detected", true, StepMath.rebooted(hidden[0], hidden[1]))

    val across = StepMath.dayTotals(
        listOf(
            Sample(3000, at(2026, 6, 1, 20, 0), at(2026, 6, 1, 20, 0) - boot1),
            Sample(600, at(2026, 6, 2, 8, 0), at(2026, 6, 2, 8, 0) - at(2026, 6, 2, 6)),
        ),
        ny,
    )
    check("post-boot steps land after the boot", 600L, across[LocalDate.of(2026, 6, 2)])
    check("nothing lands while the phone was off", null, across[LocalDate.of(2026, 6, 1)])

    check(
        "a quiet interval is not a reboot",
        false,
        StepMath.rebooted(s(1000, at(2026, 6, 1, 9, 0)), s(1010, at(2026, 6, 1, 9, 15))),
    )

    check(
        "a thin spread is not rounded away",
        4L,
        StepMath.dayTotal(
            listOf(s(0, at(2026, 6, 1, 6, 0)), s(4, at(2026, 6, 1, 18, 0))),
            LocalDate.of(2026, 6, 1),
            ny,
        ),
    )

    val longGap = listOf(s(0, at(2026, 6, 1, 9, 0)), s(5000, at(2026, 6, 3, 9, 0)))
    check("a gap past the ceiling is dropped", 0L, StepMath.dayTotal(longGap, LocalDate.of(2026, 6, 1), ny))
    val audit = StepMath.audit(longGap)
    check("the audit names the gap", Verdict.GAP_TOO_LONG, audit[0].verdict)
    check("the audit keeps the raw delta", 5000L, audit[0].rawDelta)
    check("the audit credits nothing", 0L, audit[0].credited)

    check(
        "an impossible delta is a fault",
        0L,
        StepMath.dayTotal(
            listOf(s(0, at(2026, 6, 1, 9, 0)), s(StepMath.MAX_DELTA + 1, at(2026, 6, 1, 9, 15))),
            LocalDate.of(2026, 6, 1),
            ny,
        ),
    )

    check(
        "a live reading extends the day",
        800L,
        StepMath.todayTotal(
            listOf(s(1000, at(2026, 6, 1, 8, 0)), s(1500, at(2026, 6, 1, 9, 0))),
            s(1800, at(2026, 6, 1, 10, 0)),
            ny,
        ),
    )

    // 2026-03-08 is a 23-hour day in New York.
    val dst = StepMath.dayTotals(
        listOf(s(0, at(2026, 3, 8, 0, 30)), s(2300, at(2026, 3, 8, 23, 30))),
        ny,
    )
    check("spring forward tiles exactly", 2300L, dst[LocalDate.of(2026, 3, 8)])
    check("spring forward bleeds nowhere", setOf(LocalDate.of(2026, 3, 8)), dst.keys)

    println(if (failures == 0) "\nall passed" else "\n$failures FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
