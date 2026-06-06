package com.mic.scriptpilot

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class InAppUpdateManager(
    private val activity: AppCompatActivity,
) {
    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity.applicationContext)

    private val updateLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            handleUpdateResult(result)
        }

    fun checkForAppUpdate() {
        runCatching {
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        startImmediateUpdate(appUpdateInfo)
                    }
                }
                .addOnFailureListener {
                    // In-app updates only work for Play-installed builds. Debug APKs should continue normally.
                }
        }
    }

    fun resumeUpdateIfInProgress() {
        runCatching {
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    if (appUpdateInfo.updateAvailability() ==
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    ) {
                        startImmediateUpdate(appUpdateInfo)
                    }
                }
                .addOnFailureListener {
                    // Play Store may be unavailable or this app may not be installed from Play.
                }
        }
    }

    private fun startImmediateUpdate(appUpdateInfo: AppUpdateInfo) {
        runCatching {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                updateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }
    }

    private fun handleUpdateResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            return
        }

        // User canceled or the update failed. Keep the app usable and allow future launches/resumes to check again.
    }
}
