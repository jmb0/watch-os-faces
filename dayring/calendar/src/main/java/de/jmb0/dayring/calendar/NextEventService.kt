package de.jmb0.dayring.calendar

import android.content.ComponentName
import android.content.Context
import android.text.format.DateFormat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoPermissionComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.util.Date

/**
 * Provides the next event to start as LONG_TEXT, without an icon: title = time ("now" for an
 * event that is already running, shown only if no other one starts), text = event name.
 */
class NextEventService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.LONG_TEXT) return null
        return build(title = "10:30", text = "Meeting")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (!UpcomingEvents.hasPermission(this)) {
            return NoPermissionComplicationData.Builder()
                .setText(text(getString(R.string.needs_permission)))
                .build()
        }
        val now = System.currentTimeMillis()
        // The next event to start; a running one only if none starts within the 12 hours, so a
        // long running event does not hide the next one
        val events = UpcomingEvents.load(this, now)
        val next = events.firstOrNull { it.begin >= now } ?: events.firstOrNull()
            ?: return build(title = null, text = getString(R.string.no_events))
        val time = if (next.begin <= now) getString(R.string.now)
        else DateFormat.getTimeFormat(this).format(Date(next.begin))
        return build(title = time, text = next.title.ifBlank { getString(R.string.busy) })
    }

    private fun build(title: String?, text: String) =
        LongTextComplicationData.Builder(text(text), text(text))
            .apply { title?.let { setTitle(text(it)) } }
            .setTapAction(CalendarLauncher.pendingIntent(this))
            .build()

    private fun text(s: String) = PlainComplicationText.Builder(s).build()

    companion object {
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, NextEventService::class.java))
                .requestUpdateAll()
        }
    }
}
