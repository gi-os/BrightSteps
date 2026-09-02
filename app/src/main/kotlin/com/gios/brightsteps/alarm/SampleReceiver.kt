package com.gios.brightsteps.alarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gios.brightsteps.data.Prefs
import com.gios.brightsteps.data.StepStore
import com.gios.brightsteps.steps.StepSampler
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Wakes on an alarm (and on boot / package-replace), reads the step counter once, and stores
 * it. Also the receiver that keeps its own alarm chains alive across the two events that would
 * otherwise silently end them: a reboot cancels every alarm, and an Obtainium update landing on
 * a closed app leaves it unscheduled until the user next opens it.
 */
class SampleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Arm the next firings FIRST. A process killed for overrunning the ~10s broadcast budget
        // never reaches a finally block, and a chain that stops re-arming stops for good.
        // Both chains are re-armed on every firing, so one being dropped is repaired by the other.
        SampleScheduler.armAll(context)

        val action = intent.action
        if (action != SampleScheduler.ACTION_SAMPLE && action != SampleScheduler.ACTION_MIDNIGHT) {
            // BOOT_COMPLETED and MY_PACKAGE_REPLACED only need the re-arm above.
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                val prefs = Prefs(appContext)
                prefs.lastFireMs = System.currentTimeMillis()
                prefs.countFire()

                // Android auto-revokes permissions from apps left unopened for a few months.
                // Reading the counter without it throws nothing and returns nothing — the app
                // just quietly stops counting — so check, and leave a note the UI can surface.
                if (!hasRecognition(appContext)) {
                    prefs.permissionLostMs = System.currentTimeMillis()
                    return@thread
                }
                prefs.permissionLostMs = 0L

                val sampler = StepSampler(appContext)
                val sample = runBlocking { sampler.readOnce() }
                if (sample != null) {
                    StepStore(appContext).record(sample)
                    prefs.lastReadingMs = sample.wallMs
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasRecognition(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    }
}
