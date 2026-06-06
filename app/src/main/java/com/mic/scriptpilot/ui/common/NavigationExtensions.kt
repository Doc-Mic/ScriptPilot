package com.mic.scriptpilot.ui.common

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.mic.scriptpilot.R

fun NavController.navigateHomeClearingWorkflow() {
    val options =
        NavOptions.Builder()
            .setPopUpTo(R.id.homeFragment, false)
            .setLaunchSingleTop(true)
            .build()
    navigate(R.id.homeFragment, null, options)
}
