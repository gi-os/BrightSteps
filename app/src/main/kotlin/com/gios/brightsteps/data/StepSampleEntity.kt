package com.gios.brightsteps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One stored reading of the hardware step counter. The table is a log of raw readings, not
 * of per-day totals: totals are derived by [com.gios.brightsteps.steps.StepMath] so the
 * bucketing rules live in one tested place and a boundary fix never means a data migration.
 */
@Entity(tableName = "step_samples")
data class StepSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Cumulative steps since boot, as reported by `TYPE_STEP_COUNTER`. */
    val counter: Long,
    /** `System.currentTimeMillis()` at the read. */
    val wallMs: Long,
    /** `SystemClock.elapsedRealtime()` at the read; falls on reboot, unlike wallMs. */
    val elapsedMs: Long,
)
