package de.jmb0.dayring.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs the DayRing watch face with Watch Face Push. The APK is bundled in the assets
 * (`default_watchface.apk`, see calendar/build.gradle.kts). Wear OS installs it together with this
 * app; this object puts it back if it was removed and updates it after an app update.
 */
object WatchFaceInstaller {

    const val SET_ACTIVE_PERMISSION = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    private const val TAG = "WatchFaceInstaller"
    private const val ASSET = "default_watchface.apk"

    enum class State { MISSING, INSTALLED, ACTIVE }

    private fun watchFacePackage(context: Context) = context.packageName + ".watchfacepush.dayring"

    /** Whether the watch face is installed and whether it is the current one; null on an error. */
    suspend fun state(context: Context): State? = withContext(Dispatchers.IO) {
        try {
            val manager = WatchFacePushManagerFactory.createWatchFacePushManager(context)
            val pkg = watchFacePackage(context)
            when {
                manager.listWatchFaces().installedWatchFaceDetails.none { it.packageName == pkg } -> State.MISSING
                manager.isWatchFaceActive(pkg) -> State.ACTIVE
                else -> State.INSTALLED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reading the watch face state failed", e)
            null
        }
    }

    /**
     * Installs the bundled watch face, or replaces the installed one if the bundled one is newer.
     * Returns false if that failed.
     */
    suspend fun ensureInstalled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val apk = File.createTempFile("watchface", ".apk", context.cacheDir)
        try {
            context.assets.open(ASSET).use { input -> apk.outputStream().use { input.copyTo(it) } }
            val bundled = context.packageManager
                .getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
                ?.longVersionCode ?: return@withContext false
            val token = context.getString(R.string.default_wf_token)
            val manager = WatchFacePushManagerFactory.createWatchFacePushManager(context)
            // This app only ever has its one watch face, so a slot that is taken holds it
            val installed = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull()
            ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                when {
                    installed == null -> manager.addWatchFace(fd, token)
                    installed.packageName != watchFacePackage(context) || installed.versionCode < bundled ->
                        manager.updateWatchFace(installed.slotId, fd, token)
                    else -> Unit
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Installing the watch face failed", e)
            false
        } finally {
            apk.delete()
        }
    }

    /**
     * Makes the watch face the current one. Needs [SET_ACTIVE_PERMISSION] and works only once per
     * installation of the app; afterwards the user has to choose it on the watch. Returns false if
     * that failed.
     */
    suspend fun setActive(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val manager = WatchFacePushManagerFactory.createWatchFacePushManager(context)
            val slot = manager.listWatchFaces().installedWatchFaceDetails
                .firstOrNull { it.packageName == watchFacePackage(context) } ?: return@withContext false
            manager.setWatchFaceAsActive(slot.slotId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Setting the watch face as active failed", e)
            false
        }
    }
}

/** The app was updated: Wear OS does not update the bundled watch face by itself. */
class WatchFaceUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WatchFaceInstaller.ensureInstalled(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
