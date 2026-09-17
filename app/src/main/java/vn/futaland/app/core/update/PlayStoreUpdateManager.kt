package vn.futaland.app.core.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

object PlayStoreUpdateManager {
    val shouldShowAlert = mutableStateOf(false)
    val availableVersionCode = mutableStateOf(0)

    private var appUpdateInfo: AppUpdateInfo? = null
    private var playUpdateManager: AppUpdateManager? = null
    private var hasChecked = false

    fun checkForUpdates(activity: Activity, force: Boolean = false) {
        if (hasChecked && !force) return
        hasChecked = true

        try {
            val manager = playUpdateManager ?: AppUpdateManagerFactory.create(activity).also {
                playUpdateManager = it
            }
            val appUpdateInfoTask = manager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { info ->
                appUpdateInfo = info
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    availableVersionCode.value = info.availableVersionCode()
                    shouldShowAlert.value = true
                }
            }.addOnFailureListener {
                // Sideloaded / debug APK or no Google Play Store installed
            }
        } catch (_: Exception) {}
    }

    fun startUpdateFlow(activity: Activity) {
        val info = appUpdateInfo
        val manager = playUpdateManager
        if (info != null && manager != null) {
            try {
                manager.startUpdateFlowForResult(
                    info,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    1001
                )
                return
            } catch (_: Exception) {}
        }
        openPlayStore(activity)
    }

    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.futaland.realestate"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.futaland.realestate"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }
}
