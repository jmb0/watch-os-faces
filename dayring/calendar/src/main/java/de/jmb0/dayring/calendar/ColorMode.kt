package de.jmb0.dayring.calendar

import android.content.Context

/** How the events on the rim are coloured; chosen in the companion app ([SetupActivity]). */
enum class ColorMode {
    /** Colour of the event, or else of its calendar; the palette if the provider has none. */
    CALENDAR,

    /** Fixed palette; the colour follows the start time, so an event keeps it as time passes. */
    PALETTE;

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "color_mode"

        fun get(context: Context): ColorMode {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == name } ?: PALETTE
        }

        fun set(context: Context, mode: ColorMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}
