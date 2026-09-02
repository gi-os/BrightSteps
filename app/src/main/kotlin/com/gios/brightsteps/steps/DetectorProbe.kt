package com.gios.brightsteps.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A live side-by-side reading, taken over one stretch of walking with the screen on. */
data class ProbeState(
    val running: Boolean = false,
    val detectorSteps: Long = 0,
    val counterSteps: Long = 0,
    val startCounter: Long = 0,
    val startElapsedMs: Long = 0,
    val nowElapsedMs: Long = 0,
    val detectorAvailable: Boolean = true,
) {
    val elapsedMs: Long get() = (nowElapsedMs - startElapsedMs).coerceAtLeast(0)
}

/**
 * Counts `TYPE_STEP_DETECTOR` events and `TYPE_STEP_COUNTER` movement over the same window, so
 * "it under-counts" can be settled instead of argued.
 *
 * The two sensors come from the same hardware but are not the same measurement. The detector
 * emits one event per step it recognises, as it recognises it. The counter is a running total
 * the hub maintains, and on most hubs it only commits a step once a walking cadence has been
 * confirmed — which is why a dozen steps across a room can register as nothing at all while a
 * sustained walk registers correctly. That behaviour is in the silicon and no amount of app
 * arithmetic recovers it.
 *
 * So: walk a counted hundred steps with this open. If the detector agrees with your count and
 * the counter lags it, the loss is the hub's confirmation threshold. If both lag your count,
 * the loss is the pedometer itself. If both match and the daily total still reads low, the
 * fault is in this app's sampling or arithmetic — and the rest of the diagnostics screen says
 * which.
 *
 * This only runs while the screen is open and the flow is collected. Holding a detector
 * listener open is the thing BrightSteps deliberately never does in the background.
 */
class DetectorProbe(context: Context) {
    private val app = context.applicationContext
    private val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val detector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val counter: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val available: Boolean get() = detector != null

    /** Emits a running comparison until collection stops, at which point both listeners go. */
    fun run(): Flow<ProbeState> = callbackFlow {
        var state = ProbeState(
            running = true,
            startElapsedMs = SystemClock.elapsedRealtime(),
            nowElapsedMs = SystemClock.elapsedRealtime(),
            detectorAvailable = detector != null,
        )
        var baseCounter: Long? = null
        trySend(state)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_DETECTOR -> {
                        // One event per step, but the contract allows a batch to arrive as a
                        // single event with values[0] holding the count.
                        val n = event.values.firstOrNull()?.toLong()?.coerceAtLeast(1L) ?: 1L
                        state = state.copy(
                            detectorSteps = state.detectorSteps + n,
                            nowElapsedMs = SystemClock.elapsedRealtime(),
                        )
                    }
                    Sensor.TYPE_STEP_COUNTER -> {
                        val v = event.values[0].toLong()
                        val base = baseCounter ?: v.also { baseCounter = it }
                        state = state.copy(
                            startCounter = base,
                            counterSteps = (v - base).coerceAtLeast(0),
                            nowElapsedMs = SystemClock.elapsedRealtime(),
                        )
                    }
                }
                trySend(state)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        detector?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) }
        counter?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) }

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
