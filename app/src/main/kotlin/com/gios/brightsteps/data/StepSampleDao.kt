package com.gios.brightsteps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepSampleDao {
    @Insert
    fun insert(sample: StepSampleEntity): Long

    /** Every sample at or after [sinceMs], oldest first — the input to the step math. */
    @Query("SELECT * FROM step_samples WHERE wallMs >= :sinceMs ORDER BY wallMs ASC")
    fun samplesSince(sinceMs: Long): List<StepSampleEntity>

    /** Same query as a stream, so the UI recomputes when a background read lands. */
    @Query("SELECT * FROM step_samples WHERE wallMs >= :sinceMs ORDER BY wallMs ASC")
    fun observeSamplesSince(sinceMs: Long): Flow<List<StepSampleEntity>>

    /** The most recent reading, for arming the next sample against it. */
    @Query("SELECT * FROM step_samples ORDER BY wallMs DESC LIMIT 1")
    fun latest(): StepSampleEntity?

    /** History is bounded — a reading older than the window can never be shown again. */
    @Query("DELETE FROM step_samples WHERE wallMs < :beforeMs")
    fun pruneBefore(beforeMs: Long): Int
}
