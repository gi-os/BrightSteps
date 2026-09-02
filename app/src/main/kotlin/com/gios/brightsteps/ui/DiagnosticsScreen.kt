package com.gios.brightsteps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightsteps.steps.ProbeState
import com.gios.brightsteps.ui.theme.Dim
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm")

/**
 * The page that answers "why is the number low?".
 *
 * It is deliberately plain text: every row is one measurement with a plain-language reading
 * beside it, because the point of the screen is to be understood at a glance on a walk, not to
 * look like a dashboard.
 */
@Composable
fun DiagnosticsScreen(
    d: DiagnosticsState,
    probe: ProbeState,
    onToggleProbe: () -> Unit,
    onFixBattery: () -> Unit,
    onFixPermission: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "DIAGNOSTICS",
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            Spacer(Modifier.height(16.dp))

            Section("Live check")
            Text(
                "Walk a counted 100 steps with this open. The detector sees each step as it " +
                    "happens; the counter is the hub's own running total and usually waits for a " +
                    "steady cadence before it commits. A counter well below your real count is " +
                    "the hardware, not this app.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigStat("DETECTOR", probe.detectorSteps.toString())
                BigStat("COUNTER", probe.counterSteps.toString())
            }
            Spacer(Modifier.height(12.dp))
            Button(if (probe.running) "STOP" else "START WALK TEST", onToggleProbe)
            if (!d.detectorAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No step detector on this phone — only the counter is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                )
            }

            Spacer(Modifier.height(28.dp))
            Section("Sampling")
            Stat("Readings today", "${d.samplesToday} of ~${d.expectedToday}")
            Stat("Longest gap today", duration(d.largestGapMs))
            Stat("Last reading", stamp(d.lastReadingMs, d.nowMs))
            Stat("Last alarm", stamp(d.lastFireMs, d.nowMs))
            Stat("Alarms since install", d.fireCount.toString())
            Text(
                "Steps between two readings are spread over the time between them. Long gaps " +
                    "still count every step, but place them roughly — and a gap past " +
                    "${StepMathGapHours}h is thrown away entirely.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(28.dp))
            Section("What was lost")
            Stat("Reboots handled", d.reboots.toString())
            Stat("Intervals discarded", d.droppedIntervals.toString())
            Stat("Steps discarded", "%,d".format(d.stepsDropped))

            Spacer(Modifier.height(28.dp))
            Section("Sensor")
            Stat("Step counter", if (d.sensorAvailable) "present" else "MISSING")
            Stat("Raw counter", "%,d".format(d.rawCounter))
            Stat("Counting since", if (d.bootWallMs > 0) full(d.bootWallMs) else "—")

            Spacer(Modifier.height(28.dp))
            Section("What can stop it")
            Stat("Permission", if (d.permissionLostMs > 0) "REVOKED" else "granted")
            Stat("Battery", if (d.batteryUnrestricted) "unrestricted" else "OPTIMIZED")
            Stat("Midnight alarm", if (d.exactAlarms) "exact" else "approximate")

            if (!d.batteryUnrestricted) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Battery optimization lets the system defer the quarter-hour reading, " +
                        "sometimes for hours. Turning it off is the single biggest thing you can " +
                        "do for accuracy here — the readings themselves cost almost nothing, " +
                        "since the counter accumulates in the sensor hub either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                )
                Spacer(Modifier.height(12.dp))
                Button("ALLOW BACKGROUND", onFixBattery)
            }
            if (d.permissionLostMs > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Android revoked activity recognition, which it does to apps left unopened. " +
                        "Nothing has been counted since ${full(d.permissionLostMs)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                )
                Spacer(Modifier.height(12.dp))
                Button("GRANT AGAIN", onFixPermission)
            }
            Spacer(Modifier.height(32.dp))
        }
        ActionBar(listOf(BarAction("DONE", onDone)))
    }
}

private const val StepMathGapHours = 36

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White)
    Spacer(Modifier.height(4.dp))
    Rule()
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Stat(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Dim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Dim)
    }
}

@Composable
private fun Button(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A1A1A))
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

private fun duration(ms: Long): String = when {
    ms <= 0 -> "—"
    ms < 60_000 -> "${ms / 1000}s"
    ms < 3_600_000 -> "${ms / 60_000}m"
    else -> "%dh %dm".format(ms / 3_600_000, (ms % 3_600_000) / 60_000)
}

private fun stamp(ms: Long, nowMs: Long): String =
    if (ms <= 0) "never" else "${TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))} (${duration(nowMs - ms)} ago)"

private fun full(ms: Long): String =
    DATE_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
