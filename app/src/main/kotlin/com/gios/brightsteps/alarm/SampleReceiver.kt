package com.gios.brightsteps.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gios.brightsteps.data.StepStore
import com.gios.brightsteps.steps.StepSampler
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Wakes on the alarm (and on boot / package-replace), reads the step counter once, and stores
 * it. Also the receiver that keeps the chain alive across the two events that would otherwise
 * silently end it: a reboot cancels every alarm, and an Obtainium update landing on a closed
 * app leaves it unscheduled until the user next opens it.
 */
class SampleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Arm the next firing FIRST. A process killed for overrunning the ~10s broadcast budget
        // never reaches a finally block, and a chain that stops re-arming stops for good.
        SampleScheduler.arm(context)

        // BOOT_COMPLETED and MY_PACKAGE_REPLACED only need the re-arm above.
        if (intent.action != SampleScheduler.ACTION_SAMPLE) return

        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                val sampler = StepSampler(appContext)
                val sample = runBlocking { sampler.readOnce() }
                if (sample != null) StepStore(appContext).record(sample)
            } finally {
                pending.finish()
            }
        }
    }
}
