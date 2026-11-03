package de.jmb0.dayring.calendar

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoPermissionComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Provides the event rim as an image (PHOTO_IMAGE): the events of the next 12 hours as coloured
 * arcs with a title on the outer lane; overlapping events go on the inner one ([DialRing.lanes]).
 * The colours follow the [ColorMode] chosen in the companion app.
 */
class EventRingService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.PHOTO_IMAGE) return null
        // 10:08, four events, the second one overlaps
        fun at(min: Int) = min * 60_000L
        val preview = listOf(
            UpcomingEvent(at(22), at(97), "Office hours"),
            UpcomingEvent(at(82), at(142), "Call"),
            UpcomingEvent(at(112), at(172), "Lunch"),
            UpcomingEvent(at(262), at(352), "Meeting"),
        )
        return build(preview, now = 0L, nowDial = 10 * 60 + 8, ColorMode.PALETTE)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (!UpcomingEvents.hasPermission(this)) {
            return NoPermissionComplicationData.Builder()
                .setText(text(getString(R.string.needs_permission)))
                .build()
        }
        val now = System.currentTimeMillis()
        val events = UpcomingEvents.load(this, now)
        return build(events, now, dialMinute(now), ColorMode.get(this))
    }

    private fun build(events: List<UpcomingEvent>, now: Long, nowDial: Int, mode: ColorMode): ComplicationData {
        // Minutes of wall-clock time, not elapsed time: across a change to or from summer time an
        // event stays where the hour hand will be when it starts
        val zone = TimeZone.getDefault()
        fun wallMinutesFromNow(t: Long) = ((t + zone.getOffset(t) - now - zone.getOffset(now)) / 60_000L).toInt()
        val spans = events.map {
            DialRing.Span(
                fromNow = wallMinutesFromNow(it.begin),
                toNow = wallMinutesFromNow(it.end),
            )
        }
        val lanes = DialRing.lanes(spans)
        val items = events.indices.mapNotNull { i ->
            val (start, end) = DialRing.visible(nowDial, spans[i]) ?: return@mapNotNull null
            RingRenderer.Item(
                start = start,
                end = end,
                lane = lanes[i],
                // Without a title (e.g. shared only as "busy") the arc shows the start time
                title = events[i].title.ifBlank { DateFormat.getTimeFormat(this).format(Date(events[i].begin)) },
                color = color(events[i], mode),
            )
        }
        val png = RingRenderer.renderPng(items)
        Log.d(TAG, "${events.size} events, image ${png.size / 1024} KB")
        return PhotoImageComplicationData.Builder(
            Icon.createWithData(png, 0, png.size),
            text(getString(R.string.ring_description, events.size.toString())),
        )
            .setTapAction(CalendarLauncher.pendingIntent(this))
            .build()
    }

    /**
     * In palette mode the colour follows the start time: every quarter of an hour the next colour.
     * An event keeps its colour while the events before it end, and events close together in
     * time get different colours.
     */
    private fun color(event: UpcomingEvent, mode: ColorMode): Int {
        if (mode == ColorMode.CALENDAR) event.color?.let { return it }
        val quarter = Math.floorDiv(event.begin, 15 * 60_000L)
        return RingRenderer.PALETTE[quarter.mod(RingRenderer.PALETTE.size)]
    }

    private fun text(s: String) = PlainComplicationText.Builder(s).build()

    private fun dialMinute(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR) * 60 + cal.get(Calendar.MINUTE)
    }

    companion object {
        private const val TAG = "DayRingCal"

        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, EventRingService::class.java))
                .requestUpdateAll()
        }
    }
}
