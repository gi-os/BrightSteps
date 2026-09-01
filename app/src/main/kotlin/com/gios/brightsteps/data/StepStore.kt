package com.gios.brightsteps.data

import android.content.Context
import com.gios.brightsteps.steps.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How much history the app keeps and shows. Beyond this, samples are pruned. */
private const val RETAIN_DAYS = 60L
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * The one place the rest of the app reads and writes step data. Wraps the DAO, converts
 * between the stored row and the pure [Sample] the math works on, and keeps the log bounded.
 */
class StepStore(context: Context) {
    private val dao = StepDatabase.get(context).sampleDao()

    /** Persist a fresh reading, then drop anything past the retention window. */
    fun record(sample: Sample) {
        dao.insert(StepSampleEntity(counter = sample.counter, wallMs = sample.wallMs, elapsedMs = sample.elapsedMs))
        dao.pruneBefore(System.currentTimeMillis() - RETAIN_DAYS * DAY_MS)
    }

    /** Every kept sample, oldest first. */
    fun samples(): List<Sample> =
        dao.samplesSince(System.currentTimeMillis() - RETAIN_DAYS * DAY_MS).map { it.toSample() }

    /** Samples as a stream for the UI — re-emits when a background read lands. */
    fun observeSamples(): Flow<List<Sample>> =
        dao.observeSamplesSince(System.currentTimeMillis() - RETAIN_DAYS * DAY_MS)
            .map { rows -> rows.map { it.toSample() } }

    fun latest(): Sample? = dao.latest()?.toSample()

    private fun StepSampleEntity.toSample() = Sample(counter, wallMs, elapsedMs)
}
