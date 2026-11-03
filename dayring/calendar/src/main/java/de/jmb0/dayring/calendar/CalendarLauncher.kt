package de.jmb0.dayring.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/** Tapping a complication opens the watch's calendar app; without one, the setup screen. */
object CalendarLauncher {
    /** Known calendar apps on Wear OS; they must also be listed under <queries> in the manifest. */
    private val CALENDAR_PACKAGES = listOf(
        "com.google.android.calendar",
        "com.samsung.android.calendar",
    )

    fun pendingIntent(context: Context): PendingIntent {
        val intent = (intents(context).firstOrNull { isRealApp(context, it) }
            ?: Intent(context, SetupActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    /** Wear OS redirects some intents to placeholder activities that only report "no app". */
    private fun isRealApp(context: Context, intent: Intent): Boolean {
        val pkg = intent.resolveActivity(context.packageManager)?.packageName ?: return false
        return pkg != "android" && !pkg.endsWith(".frameworkpackagestubs")
    }

    private fun intents(context: Context): List<Intent> = listOf(
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
    ) + CALENDAR_PACKAGES.mapNotNull { context.packageManager.getLaunchIntentForPackage(it) } + listOf(
        Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()),
    )
}
