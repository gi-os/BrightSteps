package com.gios.brightsteps.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Arms the next background step reading.
 *
 * `setAndAllowWhileIdle` is the one alarm that fires in Doze, and firing it hands the app a
 * short network-and-CPU window — which is all a sensor read needs. It is inexact (so no
 * `SCHEDULE_EXACT_ALARM`) and the OS throttles it to roughly once every nine minutes while the
 * phone is idle, so the interval is set above that rather than fighting it. There is no
 * repeating form: each firing arms the next, and [arm] is also called at launch and on boot.
 */
object SampleScheduler {
    /** Comfortably above the ~9-minute idle throttle, so the OS never has to defer us twice. */
    const val INTERVAL_MS: Long = 15L * 60 * 1000

    const val ACTION_SAMPLE = "com.gios.brightsteps.SAMPLE"
    private const val REQUEST_CODE = 4201

    fun arm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            pending(context),
        )
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, SampleReceiver::class.java).setAction(ACTION_SAMPLE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
