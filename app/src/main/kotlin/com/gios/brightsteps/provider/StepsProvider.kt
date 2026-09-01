package com.gios.brightsteps.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.gios.brightsteps.data.StepStore
import com.gios.brightsteps.steps.StepMath
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads-only window onto the step log, for other Light apps — the same "ask by date, take what
 * comes, treat nothing as nothing" bus BrightNotebook already reads four other providers over.
 *
 *   content://com.gios.brightsteps.steps/day/<yyyy-MM-dd>    -> one row: date, total
 *   content://com.gios.brightsteps.steps/hours/<yyyy-MM-dd>  -> rows: hourStartMs, steps
 *
 * Exported, but every caller is checked against an allowlist by package name. Signature
 * permissions are no use here: each Light app carries its own keystore, so there is no shared
 * signature to gate on.
 */
class StepsProvider : ContentProvider() {

    private lateinit var store: StepStore
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun onCreate(): Boolean {
        store = StepStore(requireNotNull(context).applicationContext)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (callingPackage !in ALLOWED_CALLERS) return null

        return when (MATCHER.match(uri)) {
            DAY -> {
                val date = parseDate(uri.lastPathSegment) ?: return null
                val total = StepMath.dayTotal(store.samples(), date, zone)
                MatrixCursor(arrayOf(COL_DATE, COL_STEPS)).apply {
                    addRow(arrayOf<Any?>(date.toString(), total))
                }
            }
            HOURS -> {
                val date = parseDate(uri.lastPathSegment) ?: return null
                val buckets = StepMath.hourlyBuckets(store.samples(), zone)
                val cursor = MatrixCursor(arrayOf(COL_HOUR_START, COL_STEPS))
                buckets.toSortedMap().forEach { (hourStartMs, steps) ->
                    val onDate = java.time.Instant.ofEpochMilli(hourStartMs).atZone(zone).toLocalDate()
                    if (onDate == date) cursor.addRow(arrayOf<Any?>(hourStartMs, steps))
                }
                cursor
            }
            else -> null
        }
    }

    private fun parseDate(seg: String?): LocalDate? =
        seg?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.gios.brightsteps.steps"
        const val COL_DATE = "date"
        const val COL_STEPS = "steps"
        const val COL_HOUR_START = "hour_start_ms"

        private const val DAY = 1
        private const val HOURS = 2

        /** LightNotebook is the intended reader; keep the pre- and post-rename package names. */
        private val ALLOWED_CALLERS = setOf(
            "com.gios.lightnotebook",
            "com.gios.brightnotebook",
            "com.gios.brightsteps",
        )

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "day/*", DAY)
            addURI(AUTHORITY, "hours/*", HOURS)
        }
    }
}
