package de.jmb0.dayring.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract.Instances
import android.util.Log

/**
 * One occurrence of an event from the watch's calendar. [color] is the colour of the event, or
 * else of its calendar (opaque ARGB); null if the provider does not supply one.
 */
data class UpcomingEvent(val begin: Long, val end: Long, val title: String, val color: Int? = null)

object UpcomingEvents {
    const val HORIZON_MS = 12 * 60 * 60 * 1000L

    private const val TAG = "DayRingCal"

    /**
     * Possible sources, in this order. Many watches (e.g. Samsung) have no calendar of their own;
     * there Wear OS mirrors the events from the phone into a provider of its own.
     */
    private val SOURCES = listOf(
        Instances.CONTENT_URI,
        Uri.parse("content://com.google.android.wearable.provider.calendar/instances/when"),
    )

    private val PROJECTION = arrayOf(
        Instances.BEGIN,
        Instances.END,
        Instances.TITLE,
        Instances.ALL_DAY,
        Instances.DISPLAY_COLOR,
        Instances.EVENT_COLOR,
        Instances.CALENDAR_COLOR,
    )

    /**
     * Colour columns, best first. The Wear provider does not always fill DISPLAY_COLOR (or does
     * not have it), so the event and calendar colours are read directly as well.
     */
    private val COLOR_COLUMNS = listOf(Instances.DISPLAY_COLOR, Instances.EVENT_COLOR, Instances.CALENDAR_COLOR)

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Events that are not all-day and take place between [now] and [now] + 12 h, sorted by start.
     * All sources are read; an event found in more than one appears once.
     */
    fun load(context: Context, now: Long): List<UpcomingEvent> =
        SOURCES.flatMap { query(context, it, now).orEmpty() }
            .distinctBy { Triple(it.begin, it.end, it.title) }
            .sortedBy { it.begin }

    /** @return null if the source does not exist or rejects the query */
    private fun query(context: Context, source: Uri, now: Long): List<UpcomingEvent>? {
        val uri = source.buildUpon()
            .also { ContentUris.appendId(it, now) }
            .also { ContentUris.appendId(it, now + HORIZON_MS) }
            .build()
        // No selection and no sort order: the Wear provider does not support either reliably.
        // Filtering and sorting happen here. If the provider does not know a column of the
        // projection, the query is repeated with all columns.
        val cursor = listOf(PROJECTION, null).firstNotNullOfOrNull { projection ->
            try {
                context.contentResolver.query(uri, projection, null, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Query of $source failed (projection: ${projection != null})", e)
                null
            }
        } ?: return null
        val events = cursor.use { read(it) }
        Log.d(TAG, "$source: ${events.size} entries")
        return events.filter { it.end > now && it.begin < now + HORIZON_MS }.sortedBy { it.begin }
    }

    /** Columns by name, because providers do not always keep the order of the projection. */
    private fun read(c: Cursor): List<UpcomingEvent> {
        val begin = c.getColumnIndex(Instances.BEGIN)
        val end = c.getColumnIndex(Instances.END)
        if (begin < 0 || end < 0) return emptyList()
        val title = c.getColumnIndex(Instances.TITLE)
        val allDay = c.getColumnIndex(Instances.ALL_DAY)
        val colors = COLOR_COLUMNS.map { c.getColumnIndex(it) }.filter { it >= 0 }
        if (colors.isEmpty()) Log.d(TAG, "No colour column; columns: ${c.columnNames.joinToString()}")
        val out = mutableListOf<UpcomingEvent>()
        while (c.moveToNext()) {
            if (allDay >= 0 && c.getInt(allDay) != 0) continue
            out += UpcomingEvent(
                begin = c.getLong(begin),
                end = c.getLong(end),
                title = if (title >= 0) c.getString(title).orEmpty() else "",
                // 0 means "not set"; providers often store the colour without alpha
                color = colors.firstNotNullOfOrNull { i -> c.getInt(i).takeIf { !c.isNull(i) && it != 0 } }
                    ?.let { it or 0xFF000000.toInt() },
            )
        }
        return out
    }
}
