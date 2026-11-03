package de.jmb0.dayring.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import java.io.ByteArrayOutputStream

/**
 * Draws the events as coloured arcs with a title in two lanes on a transparent rim.
 * The dimensions use the 450-unit coordinate system of the watch face and must match the dial ring
 * in watchface.xml, which sits on the outer lane.
 */
object RingRenderer {
    const val SIZE = 480
    private const val FACE = 450f

    /** Mid radius, width and title size per lane (0 = inner, 1 = outer), in watch face units. */
    private val LANE_RADIUS = floatArrayOf(192f, 213f)
    private val LANE_WIDTH = floatArrayOf(18f, 24f)
    private val TEXT_SIZE = floatArrayOf(22f, 26f)

    /** Event colours in order of the events; deliberately no orange, that is the face's accent. */
    val PALETTE = intArrayOf(
        0xFF4FA3F7.toInt(), // Blue
        0xFF3CC47C.toInt(), // Green
        0xFFB388FF.toInt(), // Violet
        0xFFFFD54F.toInt(), // Yellow
        0xFF26C6DA.toInt(), // Turquoise
        0xFFF06292.toInt(), // Pink
    )

    /** Gap between adjoining events, in degrees; wide enough to separate two of the same colour. */
    private const val GAP_DEG = 1.5f

    /** Shortest arc, in degrees, so that even an event of a few minutes stays visible. */
    private const val MIN_SWEEP_DEG = 1.5f

    /** Title colours; the one with more contrast to the arc is used. */
    private const val DARK_TEXT = 0xFF141414.toInt()
    private const val LIGHT_TEXT = 0xFFFFFFFF.toInt()

    /** Padding between the title and the end of the arc, in watch face units. */
    private const val TEXT_PAD = 4f

    /** Sample text whose width sets the minimum arc length: about 7 characters. */
    private const val MIN_TITLE_SAMPLE = "Meeting"

    data class Item(val start: Int, val end: Int, val lane: Int, val title: String, val color: Int)

    fun renderPng(items: List<Item>): ByteArray {
        val bitmap = render(items)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }

    private fun render(items: List<Item>): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(SIZE / FACE, SIZE / FACE)

        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Even a short event gets an arc long enough for about 7 characters of its title
        val minSweep = FloatArray(2) { lane ->
            label.textSize = TEXT_SIZE[lane]
            val minArc = label.measureText(MIN_TITLE_SAMPLE) + 2 * TEXT_PAD
            Math.toDegrees((minArc / LANE_RADIUS[lane]).toDouble()).toFloat()
        }
        val lanes = IntArray(items.size) { items[it].lane }

        /** Arc length of item [i] on [lane]: stretched to the minimum, but never into the next event on that lane. */
        fun sweepOf(i: Int, lane: Int): Float {
            val item = items[i]
            val nextStart = (i + 1 until items.size).filter { lanes[it] == lane }.minOfOrNull { items[it].start }
            val free = if (nextStart != null) (nextStart - item.start) * 0.5f - GAP_DEG else 360f - GAP_DEG
            return maxOf((item.end - item.start) * 0.5f - GAP_DEG, minOf(minSweep[lane], free), MIN_SWEEP_DEG)
        }

        /** Whether the arc of item [i] on [lane] runs past the end of the 12 hours onto an event at their start. */
        fun wraps(i: Int, lane: Int): Boolean {
            val end = items[i].start + (GAP_DEG / 2 + sweepOf(i, lane)) / 0.5f
            return items.indices.any { j -> j != i && lanes[j] == lane && items[j].start + DialRing.WINDOW_MINUTES < end }
        }

        // A stretched arc near the end of the 12 hours that would cover an event at their start
        // moves to the other lane if that is free there; otherwise it covers it
        for (i in items.indices) {
            val lane = lanes[i]
            if (!wraps(i, lane)) continue
            val other = 1 - lane
            val busy = items.indices.any { j ->
                j != i && lanes[j] == other && items[j].start <= items[i].start && items[j].end > items[i].start
            }
            if (!busy && !wraps(i, other)) lanes[i] = other
        }

        for ((index, item) in items.withIndex()) {
            val lane = lanes[index]
            val r = LANE_RADIUS[lane]
            arc.strokeWidth = LANE_WIDTH[lane]
            label.textSize = TEXT_SIZE[lane]
            // Shift the baseline so the text sits in the middle of the lane
            val centerShift = -(label.fontMetrics.ascent + label.fontMetrics.descent) / 2

            val oval = RectF(FACE / 2 - r, FACE / 2 - r, FACE / 2 + r, FACE / 2 + r)
            val startDeg = item.start * 0.5f + GAP_DEG / 2
            val sweep = sweepOf(index, lane)

            arc.color = item.color
            canvas.drawArc(oval, startDeg - 90f, sweep, false, arc)
            label.color = textColor(item.color)

            val textDeg = sweep - Math.toDegrees((2 * TEXT_PAD / r).toDouble()).toFloat()
            val room = Math.toRadians(textDeg.toDouble()).toFloat() * r
            if (room < label.textSize * 1.5f) continue
            val text = TextUtils.ellipsize(item.title, label, room, TextUtils.TruncateAt.END).toString()
            // If it is shortened completely only "…" is left; then show nothing instead
            if (text.isEmpty() || text == "\u2026") continue

            // Label the lower half counter-clockwise so the text is not upside down
            val mid = (startDeg + sweep / 2) % 360f
            val path = Path()
            val padDeg = (sweep - textDeg) / 2
            if (mid in 90f..270f) {
                path.addArc(oval, startDeg + sweep - padDeg - 90f, -textDeg)
            } else {
                path.addArc(oval, startDeg + padDeg - 90f, textDeg)
            }
            canvas.drawTextOnPath(text, path, 0f, centerShift, label)
        }
        return bitmap
    }

    /**
     * Dark or light text, whichever contrasts more with [background] (WCAG contrast ratio). Dark
     * calendar colours such as graphite or dark blue get white text.
     */
    private fun textColor(background: Int): Int {
        val l = Color.luminance(background)
        val darkContrast = (l + 0.05f) / (Color.luminance(DARK_TEXT) + 0.05f)
        val lightContrast = 1.05f / (l + 0.05f)
        return if (darkContrast >= lightContrast) DARK_TEXT else LIGHT_TEXT
    }
}
