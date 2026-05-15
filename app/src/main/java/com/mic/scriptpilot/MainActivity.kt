package com.mic.scriptpilot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.mic.scriptpilot.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var didWireBottomNav: Boolean = false
    private var navSetupAttempts: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NavHostFragment is attached by FragmentContainerView during inflation, but ordering
        // vs. FragmentManager transactions can still race on some devices — resolve on the next pass.
        binding.root.post { tryWireNavigationUi() }
    }

    private fun tryWireNavigationUi() {
        if (didWireBottomNav) return

        supportFragmentManager.executePendingTransactions()
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
        if (navHost == null) {
            navSetupAttempts++
            if (navSetupAttempts > 40) {
                throw IllegalStateException(
                    "NavHostFragment not found for R.id.nav_host after ${navSetupAttempts} attempts.",
                )
            }
            binding.root.post { tryWireNavigationUi() }
            return
        }

        didWireBottomNav = true
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        val roots = setOf(
            R.id.homeFragment,
            R.id.projectListFragment,
            R.id.aiToolsFragment,
            R.id.profileFragment,
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in roots
        }
    }
}
