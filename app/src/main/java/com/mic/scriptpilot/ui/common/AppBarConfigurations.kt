package com.mic.scriptpilot.ui.common

import com.mic.scriptpilot.R
import androidx.navigation.ui.AppBarConfiguration

fun rootAppBarConfiguration(): AppBarConfiguration =
    AppBarConfiguration(
        setOf(
            R.id.homeFragment,
            R.id.projectListFragment,
            R.id.aiToolsFragment,
            R.id.profileFragment,
        ),
    )
