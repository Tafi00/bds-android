package vn.futaland.app.core.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Store-driven update state. The dialog fires only when Google Play reports
 * [UpdateAvailability.UPDATE_AVAILABLE] for the installed package — never
 * from CMS flags or bundled config.
 *
 * Why the old popup never appeared:
 * - `hasChecked` latched on the first call, so the `onResume` re-check was a
 *   no-op and a transient Play failure stuck forever.
 * - The update requestCode (1001) collided with the notification-permission
 *   requestCode, and there was no resume path for an in-progress IMMEDIATE
 *   update, so the Play flow could die silently.
 * - Debug builds (`...realestate.debug`) are not a Play listing; Play Core
 *   always fails there and must stay silent instead of nagging.
 */
object PlayStoreUpdateManager {
    const val UPDATE_REQUEST_CODE = 9001
    const val PACKAGE_NAME = "com.futaland.realestate"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.futaland.realestate"

    private const val PREFS = "futa_update_prefs"
    private const val KEY_DISMISSED_CODE = "dismissedVersionCode"
    private const val KEY_LAST_CHECK_AT = "lastCheckAt"
    private const val CHECK_THROTTLE_MS = 6 * 60 * 60 * 1000L

    val shouldShowAlert = mutableStateOf(false)
    val availableVersionCode = mutableStateOf(0)
    val installedVersionName = mutableStateOf("")

    fun checkForUpdates(activity: Activity, force: Boolean = false) {
        // Sideloaded / debug builds are not the Play listing: never nag.
        if (activity.packageName != PACKAGE_NAME) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!force) {
            val last = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
            if (last > 0L && System.currentTimeMillis() - last < CHECK_THROTTLE_MS) return
        }
        prefs.edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
        installedVersionName.value = installedVersionName(activity)

        try {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    when (info.updateAvailability()) {
                        // IMMEDIATE flow was interrupted (process death / user back):
                        // restart it straight away, no dialog needed.
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                            actOnUpdate(activity)
                        }
                        UpdateAvailability.UPDATE_AVAILABLE -> {
                            val playCode = info.availableVersionCode()
                            val installed = installedVersionCode(activity)
                            val dismissed = prefs.getInt(KEY_DISMISSED_CODE, 0)
                            if (shouldPrompt(playCode, installed, dismissed) &&
                                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                            ) {
                                availableVersionCode.value = playCode
                                shouldShowAlert.value = true
                            }
                        }
                        else -> Unit
                    }
                }
                .addOnFailureListener {
                    // No Play Store / no network / sideloaded APK: stay silent.
                }
        } catch (_: Exception) {
        }
    }

    /** Pure decision: Play build is newer than installed, and not snoozed. */
    fun shouldPrompt(playCode: Int, installedCode: Int, dismissedCode: Int): Boolean {
        if (playCode <= 0 || installedCode <= 0) return false
        if (playCode <= installedCode) return false
        return playCode != dismissedCode
    }

    /** "Để sau": hide and don't nag again for this exact Play versionCode. */
    fun dismiss(context: Context) {
        val code = availableVersionCode.value
        if (code > 0) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_DISMISSED_CODE, code).apply()
        }
        shouldShowAlert.value = false
    }

    /** "Cập nhật ngay": hide the card and jump into the Play update flow. */
    fun actOnUpdate(activity: Activity) {
        shouldShowAlert.value = false
        startUpdateFlow(activity)
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
