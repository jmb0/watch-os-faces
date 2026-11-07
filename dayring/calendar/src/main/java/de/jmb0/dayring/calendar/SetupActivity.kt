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
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Open once to grant calendar access and to make DayRing the watch face; afterwards it shows the
 * status and switches the colours of the rim ([ColorMode]).
 */
class SetupActivity : Activity() {

    private val scope = MainScope()
    private var faceJob: Job? = null

    private lateinit var status: TextView
    private lateinit var colors: Button
    private lateinit var settings: Button
    private lateinit var faceStatus: TextView
    private lateinit var face: Button

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
        faceStatus = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            visibility = View.GONE
        }
        face = pillButton().apply {
            setText(R.string.face_activate)
            visibility = View.GONE
            setOnClickListener {
                if (checkSelfPermission(WatchFaceInstaller.SET_ACTIVE_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                    activateFace()
                } else {
                    requestPermissions(arrayOf(WatchFaceInstaller.SET_ACTIVE_PERMISSION), REQUEST_FACE)
                }
            }
        }
        val pad = resources.displayMetrics.widthPixels / 7
        val rowParams = {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = pad / 6 }
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(pad, pad, pad, pad)
                addView(status)
                addView(settings, rowParams())
                addView(colors, rowParams())
                addView(face, rowParams())
                addView(faceStatus, rowParams())
            })
        })
        showColors()
        if (!UpcomingEvents.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
        }
    }

    override fun onResume() {
        super.onResume()
        showStatus()
        refreshFace()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQUEST_CALENDAR -> {
                // After a second denial Android answers at once without a dialog; then only the settings help
                blocked = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
                showStatus()
            }
            // Asked only once by Android; after a denial the user picks the watch face by hand
            REQUEST_FACE -> if (granted) activateFace() else showFaceHint()
        }
    }

    /** Installs the watch face if it is missing and shows whether it is the current one. */
    private fun refreshFace() {
        if (faceJob?.isActive == true) return
        faceJob = scope.launch {
            var state = WatchFaceInstaller.state(this@SetupActivity)
            if (state == WatchFaceInstaller.State.MISSING && WatchFaceInstaller.ensureInstalled(this@SetupActivity)) {
                state = WatchFaceInstaller.state(this@SetupActivity)
            }
            showFace(state)
        }
    }

    private fun activateFace() {
        scope.launch {
            if (WatchFaceInstaller.setActive(this@SetupActivity)) {
                showFace(WatchFaceInstaller.State.ACTIVE)
            } else {
                // Possible only once; after that Wear OS refuses it
                showFaceHint()
            }
        }
    }

    private fun showFace(state: WatchFaceInstaller.State?) {
        face.visibility = if (state == WatchFaceInstaller.State.INSTALLED) View.VISIBLE else View.GONE
        when (state) {
            WatchFaceInstaller.State.ACTIVE -> showFaceStatus(R.string.face_active)
            WatchFaceInstaller.State.INSTALLED -> faceStatus.visibility = View.GONE
            else -> showFaceStatus(R.string.face_missing)
        }
    }

    private fun showFaceHint() {
        face.visibility = View.GONE
        showFaceStatus(R.string.face_hint)
    }

    private fun showFaceStatus(text: Int) {
        faceStatus.setText(text)
        faceStatus.visibility = View.VISIBLE
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

private const val REQUEST_CALENDAR = 0
private const val REQUEST_FACE = 1

/** Calendar changed: recompute the rim and the next event right away. */
class CalendarChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventRingService.requestUpdate(context)
        NextEventService.requestUpdate(context)
    }
}
