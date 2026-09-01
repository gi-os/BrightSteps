package com.gios.brightsteps.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.brightsteps.data.Prefs
import com.gios.brightsteps.data.StepStore
import com.gios.brightsteps.steps.Sample
import com.gios.brightsteps.steps.StepMath
import com.gios.brightsteps.steps.StepSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

class StepsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StepStore(app)
    private val prefs = Prefs(app)
    private val sampler = StepSampler(app)

    private val liveSample = MutableStateFlow<Sample?>(null)
    private val goal = MutableStateFlow(prefs.dailyGoal)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val state: StateFlow<StepsUiState> =
        combine(store.observeSamples(), liveSample, goal) { samples, live, g ->
            val effective = if (live != null) samples + live else samples
            val totals = StepMath.dayTotals(StepMath.hourlyBuckets(effective, zone), zone)
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
}
