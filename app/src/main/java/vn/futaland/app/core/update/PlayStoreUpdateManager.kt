package vn.futaland.app.core.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Store-driven update state. The dialog fires only when Google Play reports
 * [UpdateAvailability.UPDATE_AVAILABLE] for the installed package — never
 * from CMS flags or bundled config.
 *
 * Reliability rules (each one fixes a way the popup used to stay hidden):
 * - No client-side throttle. `appUpdateInfo` is a cheap local IPC to the Play
 *   Store, and the old 6h throttle was written *before* the query finished, so
 *   one failed query (or a resume while an IMMEDIATE update was in flight)
 *   silenced the popup for hours.
 * - "Để sau" snoozes a Play versionCode for [SNOOZE_MS], it never mutes it
 *   forever, so one accidental tap cannot hide the popup permanently.
 * - The popup no longer depends on `isUpdateTypeAllowed(IMMEDIATE)`. When Play
 *   refuses the in-app flow, [startUpdateFlow] opens the store page instead.
 * - The update requestCode does not collide with the notification-permission
 *   requestCode, and an interrupted IMMEDIATE update is resumed on every resume.
 * - Debug builds (`...realestate.debug`) are not a Play listing; Play Core
 *   always fails there and must stay silent. Use [previewIfRequested] to look at
 *   the dialog instead.
 *
 * In-app updates only work for installs that came from Google Play, and Play
 * can take a while after a release to report the new build to devices.
 */
object PlayStoreUpdateManager {
    const val UPDATE_REQUEST_CODE = 9001
    const val PACKAGE_NAME = "com.futaland.realestate"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.futaland.realestate"

    /** Debug builds: `adb shell am start -n com.futaland.realestate.debug/vn.futaland.app.MainActivity --ez futa_preview_update true` */
    const val EXTRA_PREVIEW = "futa_preview_update"

    const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    private const val PREFS = "futa_update_prefs"
    private const val KEY_DISMISSED_CODE = "dismissedVersionCode"
    private const val KEY_DISMISSED_AT = "dismissedAt"

    val shouldShowAlert = mutableStateOf(false)
    val availableVersionCode = mutableStateOf(0)
    val installedVersionName = mutableStateOf("")

    @Volatile
    private var isChecking = false

    fun checkForUpdates(activity: Activity) {
        // Sideloaded / debug builds are not the Play listing: never nag.
        if (activity.packageName != PACKAGE_NAME) return
        // onCreate + onResume fire back to back on launch: one query is enough.
        if (isChecking) return
        isChecking = true
        installedVersionName.value = installedVersionName(activity)

        try {
            AppUpdateManagerFactory.create(activity).appUpdateInfo
                .addOnSuccessListener { info -> handleUpdateInfo(activity, info) }
                .addOnFailureListener {
                    // No Play Store / no network / sideloaded APK: stay silent.
                }
                .addOnCompleteListener { isChecking = false }
        } catch (_: Exception) {
            isChecking = false
        }
    }

    private fun handleUpdateInfo(activity: Activity, info: AppUpdateInfo) {
        when (info.updateAvailability()) {
            // IMMEDIATE flow was interrupted (process death / user back):
            // restart it straight away, no dialog and no snooze in the way.
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                actOnUpdate(activity)
            }
            UpdateAvailability.UPDATE_AVAILABLE -> {
                val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val playCode = info.availableVersionCode()
                if (shouldPrompt(
                        playCode = playCode,
                        installedCode = installedVersionCode(activity),
                        dismissedCode = prefs.getInt(KEY_DISMISSED_CODE, 0),
                        dismissedAt = prefs.getLong(KEY_DISMISSED_AT, 0L)
                    )
                ) {
                    availableVersionCode.value = playCode
                    shouldShowAlert.value = true
                }
            }
            else -> Unit
        }
    }

    /**
     * Pure decision: Play build is newer than installed, and not inside a snooze
     * window. A snooze without a timestamp (written by an older build that muted
     * a version forever) or with a timestamp in the future counts as expired.
     */
    fun shouldPrompt(
        playCode: Int,
        installedCode: Int,
        dismissedCode: Int,
        dismissedAt: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (playCode <= 0 || installedCode <= 0) return false
        if (playCode <= installedCode) return false
        if (playCode != dismissedCode) return true
        if (dismissedAt <= 0L) return true
        val elapsed = now - dismissedAt
        return elapsed < 0L || elapsed >= SNOOZE_MS
    }

    /** "Để sau" / back / tap outside: hide and stay quiet for [SNOOZE_MS]. */
    fun dismiss(context: Context) {
        snooze(context)
        shouldShowAlert.value = false
    }

    /**
     * "Cập nhật ngay": hide the card and jump into the Play update flow. It also
     * snoozes, so cancelling Play's own sheet does not bounce the user straight
     * back into this dialog on the next resume.
     */
    fun actOnUpdate(activity: Activity) {
        snooze(activity)
        shouldShowAlert.value = false
        startUpdateFlow(activity)
    }

    private fun snooze(context: Context) {
        val code = availableVersionCode.value
        if (code <= 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DISMISSED_CODE, code)
            .putLong(KEY_DISMISSED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Debug-only: show the dialog without Play, see [EXTRA_PREVIEW]. */
    fun previewIfRequested(activity: Activity, intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_PREVIEW, false) != true) return
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        installedVersionName.value = installedVersionName(activity)
        availableVersionCode.value = installedVersionCode(activity) + 1
        shouldShowAlert.value = true
    }

    fun startUpdateFlow(activity: Activity) {
        try {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo.addOnSuccessListener { info ->
                val allowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                val inProgress =
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                val available =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if (allowed && (inProgress || available)) {
                    try {
                        manager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                            UPDATE_REQUEST_CODE
                        )
                        return@addOnSuccessListener
                    } catch (_: Exception) {
                    }
                }
                openPlayStore(activity)
            }.addOnFailureListener {
                openPlayStore(activity)
            }
        } catch (_: Exception) {
            openPlayStore(activity)
        }
    }

    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (_: Exception) {
            }
        }
    }

    fun installedVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
        } catch (_: Exception) {
            0
        }
    }

    fun installedVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
