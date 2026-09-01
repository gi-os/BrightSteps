package com.gios.brightsteps.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads `TYPE_STEP_COUNTER` once and lets go.
 *
 * The counter lives in the sensor hub and accumulates while this process is dead, so there is
 * nothing to keep running: registering a listener delivers the current cumulative value almost
 * immediately, we take that one reading and unregister. No foreground service, no wake lock —
 * which is the whole reason steps are cheap to track on a phone that sleeps aggressively.
 */
class StepSampler(context: Context) {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** True when the phone exposes a hardware step counter at all. */
    val available: Boolean get() = sensor != null

    /**
     * One reading, or null if the sensor is absent or stays silent past [timeoutMs]. A first
     * event normally arrives within a second; the timeout guards the rare phone that registers
     * the listener but never delivers.
     */
    suspend fun readOnce(timeoutMs: Long = 5_000): Sample? {
        val s = sensor ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) {
                            cont.resume(
                                Sample(
                                    counter = event.values[0].toLong(),
                                    wallMs = System.currentTimeMillis(),
                                    elapsedMs = SystemClock.elapsedRealtime(),
                                ),
                            )
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }
    }
}
