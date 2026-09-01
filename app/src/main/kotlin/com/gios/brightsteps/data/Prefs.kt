package com.gios.brightsteps.data

import android.content.Context

/** The one setting v1 has: a daily step goal. Kept in SharedPreferences — no schema, no DAO. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("brightsteps", Context.MODE_PRIVATE)

    var dailyGoal: Int
        get() = sp.getInt(KEY_GOAL, DEFAULT_GOAL)
        set(value) = sp.edit().putInt(KEY_GOAL, value.coerceIn(MIN_GOAL, MAX_GOAL)).apply()

    companion object {
        const val DEFAULT_GOAL = 8_000
        const val MIN_GOAL = 1_000
        const val MAX_GOAL = 50_000
        const val GOAL_STEP = 500
        private const val KEY_GOAL = "daily_goal"
    }
}
