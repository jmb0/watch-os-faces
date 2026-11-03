package de.jmb0.dayring.calendar

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Open once to grant calendar access; afterwards it shows the status and switches the colours
 * of the rim ([ColorMode]).
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var colors: Button
    private lateinit var settings: Button

    /** Access was denied and Android no longer asks; it can only be allowed in the settings. */
    private var blocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
        }
        colors = pillButton().apply {
            setOnClickListener {
                val next = if (ColorMode.get(context) == ColorMode.PALETTE) ColorMode.CALENDAR else ColorMode.PALETTE
                ColorMode.set(context, next)
                showColors()
                EventRingService.requestUpdate(context)
            }
        }
        settings = pillButton().apply {
            setText(R.string.open_settings)
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                )
            }
        }
        val pad = resources.displayMetrics.widthPixels / 7
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(settings, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = pad / 6 })
            addView(colors)
        })
        showColors()
        if (!UpcomingEvents.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 0)
        }
    }

    override fun onResume() {
        super.onResume()
        showStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // After a second denial Android answers at once without a dialog; then only the settings help
        blocked = grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED &&
            !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
        showStatus()
    }

    private fun pillButton() = Button(this).apply {
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            setColor(0xFF333333.toInt())
            cornerRadius = 1000f
        }
    }

    private fun showColors() {
        colors.setText(
            if (ColorMode.get(this) == ColorMode.CALENDAR) R.string.colors_calendar else R.string.colors_palette
        )
    }

    private fun showStatus() {
        val granted = UpcomingEvents.hasPermission(this)
        status.setText(
            when {
                granted -> R.string.setup_ok
                blocked -> R.string.setup_blocked
                else -> R.string.setup_denied
            }
        )
        settings.visibility = if (!granted && blocked) View.VISIBLE else View.GONE
        if (granted) {
            EventRingService.requestUpdate(this)
            NextEventService.requestUpdate(this)
        }
    }
}

/** Calendar changed: recompute the rim and the next event right away. */
class CalendarChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventRingService.requestUpdate(context)
        NextEventService.requestUpdate(context)
    }
}
