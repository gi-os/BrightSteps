package com.gios.brightsteps.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Arms the background step readings — the routine quarter-hour chain, and a separate reading
 * pinned to midnight.
 *
 * `setAndAllowWhileIdle` is the one alarm that fires in Doze, and firing it hands the app a
 * short CPU window, which is all a sensor read needs. It is inexact and the OS throttles it to
 * roughly once every nine minutes while the phone is idle, so the interval is set above that
 * rather than fighting it. There is no repeating form: each firing arms the next, and [arm] is
 * also called at launch and on boot.
 *
 * **Why midnight is armed separately.** A day's total is the steps credited to that day's
 * hours, and steps between two readings are spread across the time between them. If the last
 * reading before midnight is at 23:47 and the next at 00:31 — routine, once the phone is idle
 * overnight — then whatever was walked in that 44 minutes is split across two days by the clock
 * rather than by where the steps actually fell. Landing a reading on the boundary itself makes
 * the split exact, and it is the one alarm worth asking for exactness on.
 */
object SampleScheduler {
    /** Comfortably above the ~9-minute idle throttle, so the OS never has to defer us twice. */
    const val INTERVAL_MS: Long = 15L * 60 * 1000

    const val ACTION_SAMPLE = "com.gios.brightsteps.SAMPLE"
    const val ACTION_MIDNIGHT = "com.gios.brightsteps.MIDNIGHT"
    private const val REQUEST_CODE = 4201
    private const val REQUEST_CODE_MIDNIGHT = 4202

    fun arm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            pending(context, ACTION_SAMPLE, REQUEST_CODE),
        )
    }

    /**
     * Arms a reading for the next local midnight. Exact where the OS allows it — a boundary
     * reading that drifts ten minutes is most of the point gone — and inexact otherwise, which
     * is still far better than hoping the quarter-hour chain happens to land near 00:00.
     */
    fun armMidnight(context: Context, zone: ZoneId = ZoneId.systemDefault()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val intent = pending(context, ACTION_MIDNIGHT, REQUEST_CODE_MIDNIGHT)
        if (canBeExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
        }
    }

    /** Both chains at once — what launch, boot and package-replace all want. */
    fun armAll(context: Context) {
        arm(context)
        armMidnight(context)
    }

    /** Whether the midnight reading can be pinned rather than merely requested. */
    fun canBeExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, SampleReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
