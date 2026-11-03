package de.jmb0.dayring.calendar

/**
 * Events on the rim of a 12-hour watch face.
 *
 * Times are minutes from now; a 12-hour window is [WINDOW_MINUTES] long. Positions on the face
 * are minutes from 12 o'clock (0 = 12 o'clock, 180 = 3 o'clock, ...).
 */
object DialRing {
    const val WINDOW_MINUTES = 720

    /** Lanes on the rim: the outer one holds all events, the inner one only overlapping ones. */
    const val INNER = 0
    const val OUTER = 1

    /** An event as minutes from now (may start negative if it is already running). */
    data class Span(val fromNow: Int, val toNow: Int)

    /**
     * Puts every event on the outer lane. Only if the outer lane is still busy when the event
     * starts does it go to the inner one, so overlapping events stay visible. If both are busy
     * (three events at the same time), it goes to the lane that became free first.
     *
     * @param spans events, sorted by start
     * @return the lane of each event, in the same order
     */
    fun lanes(spans: List<Span>): List<Int> {
        val busyUntil = IntArray(2) { Int.MIN_VALUE }
        return spans.map { s ->
            val lane = when {
                busyUntil[OUTER] <= s.fromNow -> OUTER
                busyUntil[INNER] <= s.fromNow -> INNER
                busyUntil[INNER] < busyUntil[OUTER] -> INNER
                else -> OUTER
            }
            busyUntil[lane] = maxOf(busyUntil[lane], s.toNow)
            lane
        }
    }

    /**
     * Visible part of an event as face positions [start, end) in minutes from 12 o'clock; end can
     * exceed 720 if the event runs past 12 o'clock. An event without duration counts as one minute.
     * null if nothing is visible.
     */
    fun visible(nowDial: Int, span: Span): Pair<Int, Int>? {
        val from = span.fromNow.coerceAtLeast(0)
        val to = maxOf(span.toNow, span.fromNow + 1).coerceAtMost(WINDOW_MINUTES)
        if (to <= from) return null
        return (nowDial + from) to (nowDial + to)
    }
}
