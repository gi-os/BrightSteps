package com.gios.brightsteps.data

import android.content.Context

/**
 * The daily goal, plus a small health log for the sampling chain.
 *
 * The health fields exist because the two ways this app silently stops counting leave no other
 * trace: the alarm chain can be dropped by the OS, and `ACTIVITY_RECOGNITION` can be auto-revoked
 * from an app that goes unopened. Both look identical from the home screen — a number that is
 * simply lower than it should be — so each background firing writes down what happened.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("brightsteps", Context.MODE_PRIVATE)

    var dailyGoal: Int
        get() = sp.getInt(KEY_GOAL, DEFAULT_GOAL)
        set(value) = sp.edit().putInt(KEY_GOAL, value.coerceIn(MIN_GOAL, MAX_GOAL)).apply()

    /** When the alarm last fired at all, whether or not a reading came of it. */
    var lastFireMs: Long
        get() = sp.getLong(KEY_LAST_FIRE, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_FIRE, value).apply()

    /** When a background reading last actually landed in the database. */
    var lastReadingMs: Long
        get() = sp.getLong(KEY_LAST_READING, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_READING, value).apply()

    /** When the background reader last found the activity-recognition permission gone. 0 = never. */
    var permissionLostMs: Long
        get() = sp.getLong(KEY_PERM_LOST, 0L)
        set(value) = sp.edit().putLong(KEY_PERM_LOST, value).apply()

    /** Firings since install, so a stalled chain shows as a count that stops moving. */
    fun countFire() {
        sp.edit().putLong(KEY_FIRES, sp.getLong(KEY_FIRES, 0L) + 1).apply()
    }

    val fireCount: Long get() = sp.getLong(KEY_FIRES, 0L)

    companion object {
        const val DEFAULT_GOAL = 8_000
        const val MIN_GOAL = 1_000
        const val MAX_GOAL = 50_000
        const val GOAL_STEP = 500
        private const val KEY_GOAL = "daily_goal"
        private const val KEY_LAST_FIRE = "last_fire_ms"
        private const val KEY_LAST_READING = "last_reading_ms"
        private const val KEY_PERM_LOST = "permission_lost_ms"
        private const val KEY_FIRES = "fire_count"
    }
}
