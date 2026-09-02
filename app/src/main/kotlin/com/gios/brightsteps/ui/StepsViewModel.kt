package com.gios.brightsteps.ui

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.brightsteps.alarm.SampleScheduler
import com.gios.brightsteps.data.Prefs
import com.gios.brightsteps.data.StepStore
import com.gios.brightsteps.steps.DetectorProbe
import com.gios.brightsteps.steps.IntervalAudit
import com.gios.brightsteps.steps.ProbeState
import com.gios.brightsteps.steps.Sample
import com.gios.brightsteps.steps.StepMath
import com.gios.brightsteps.steps.StepSampler
import com.gios.brightsteps.steps.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** One day in the seven-day strip. */
data class DayBar(val date: LocalDate, val steps: Long)

data class StepsUiState(
    val today: Long = 0,
    val goal: Int = Prefs.DEFAULT_GOAL,
    val week: List<DayBar> = emptyList(),
    val sensorAvailable: Boolean = true,
    val hasReading: Boolean = false,
)

/**
 * What the sampling chain has actually been doing. Every number here answers a specific way the
 * count can come out low, so a reading that "seems off" resolves to a cause rather than a shrug.
 */
data class DiagnosticsState(
    val sensorAvailable: Boolean = true,
    val detectorAvailable: Boolean = true,
    val exactAlarms: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val permissionLostMs: Long = 0,
    val lastFireMs: Long = 0,
    val lastReadingMs: Long = 0,
    val fireCount: Long = 0,
    val samplesToday: Int = 0,
    val expectedToday: Int = 0,
    val largestGapMs: Long = 0,
    val rawCounter: Long = 0,
    val bootWallMs: Long = 0,
    val reboots: Int = 0,
    val droppedIntervals: Int = 0,
    val stepsDropped: Long = 0,
    val nowMs: Long = 0,
)

class StepsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StepStore(app)
    private val prefs = Prefs(app)
    private val sampler = StepSampler(app)
    private val probe = DetectorProbe(app)

    private val liveSample = MutableStateFlow<Sample?>(null)
    private val goal = MutableStateFlow(prefs.dailyGoal)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val state: StateFlow<StepsUiState> =
        combine(store.observeSamples(), liveSample, goal) { samples, live, g ->
            val effective = if (live != null) samples + live else samples
            val totals = StepMath.dayTotals(effective, zone)
            val today = LocalDate.now(zone)
            val week = (0..6).map { i ->
                val d = today.minusDays((6 - i).toLong())
                DayBar(d, totals[d] ?: 0L)
            }
            StepsUiState(
                today = totals[today] ?: 0L,
                goal = g,
                week = week,
                sensorAvailable = sampler.available,
                hasReading = effective.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepsUiState())

    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    private val _probeState = MutableStateFlow(ProbeState())
    val probeState: StateFlow<ProbeState> = _probeState.asStateFlow()
    private var probeJob: Job? = null

    /** True when the permission was found missing by the background reader and never regained. */
    val permissionLost: Boolean get() = prefs.permissionLostMs > 0

    /** Take a fresh reading so the on-screen number is current; persist it for the record too. */
    fun sampleNow() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { sampler.readOnce() } ?: return@launch
            withContext(Dispatchers.IO) { store.record(s) }
            liveSample.value = s
        }
    }

    fun setGoal(value: Int) {
        prefs.dailyGoal = value
        goal.value = prefs.dailyGoal
    }

    /** Recompute the health picture from the stored samples. Cheap; called on opening the screen. */
    fun refreshDiagnostics() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val now = System.currentTimeMillis()
            val computed = withContext(Dispatchers.IO) {
                val samples = store.samples()
                val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                val today = samples.filter { it.wallMs >= dayStart }
                // The boundary sample counts on both sides: a gap starts at the last reading
                // before midnight, not at the first one after it.
                val withEdge = (samples.lastOrNull { it.wallMs < dayStart }?.let { listOf(it) } ?: emptyList()) + today
                var largest = 0L
                for (i in 1 until withEdge.size) {
                    val gap = withEdge[i].wallMs - withEdge[i - 1].wallMs
                    if (gap > largest) largest = gap
                }
                if (withEdge.size >= 1) {
                    val trailing = now - withEdge.last().wallMs
                    if (trailing > largest) largest = trailing
                }
                val audits: List<IntervalAudit> = StepMath.audit(samples)
                val latest = store.latest()
                Triple(
                    today.size to largest,
                    audits,
                    latest,
                )
            }
            val (todayCount, largestGap) = computed.first
            val audits = computed.second
            val latest = computed.third
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

            _diagnostics.value = DiagnosticsState(
                sensorAvailable = sampler.available,
                detectorAvailable = probe.available,
                exactAlarms = SampleScheduler.canBeExact(am),
                batteryUnrestricted = pm.isIgnoringBatteryOptimizations(app.packageName),
                permissionLostMs = prefs.permissionLostMs,
                lastFireMs = prefs.lastFireMs,
                lastReadingMs = prefs.lastReadingMs,
                fireCount = prefs.fireCount,
                samplesToday = todayCount,
                expectedToday = (((now - dayStart) / SampleScheduler.INTERVAL_MS) + 1).toInt(),
                largestGapMs = largestGap,
                rawCounter = latest?.counter ?: 0,
                bootWallMs = latest?.let { it.wallMs - it.elapsedMs } ?: 0,
                reboots = audits.count { it.verdict == Verdict.REBOOT },
                droppedIntervals = audits.count {
                    it.verdict == Verdict.GAP_TOO_LONG || it.verdict == Verdict.IMPLAUSIBLE
                },
                stepsDropped = audits
                    .filter { it.verdict == Verdict.GAP_TOO_LONG || it.verdict == Verdict.IMPLAUSIBLE }
                    .sumOf { it.rawDelta.coerceAtLeast(0) },
                nowMs = now,
            )
        }
    }

    fun startProbe() {
        if (probeJob?.isActive == true) return
        probeJob = viewModelScope.launch {
            probe.run().collect { _probeState.value = it }
        }
    }

    fun stopProbe() {
        probeJob?.cancel()
        probeJob = null
        _probeState.value = _probeState.value.copy(running = false)
    }

    override fun onCleared() {
        stopProbe()
        super.onCleared()
    }
}
