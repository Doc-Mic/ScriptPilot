package com.mic.scriptpilot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.mic.scriptpilot.data.repository.AuthRepository
import com.mic.scriptpilot.databinding.ActivityMainBinding
import com.mic.scriptpilot.ui.common.navigateHomeClearingWorkflow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var authRepository: AuthRepository

    private lateinit var binding: ActivityMainBinding
    private lateinit var inAppUpdateManager: InAppUpdateManager
    private var didWireBottomNav: Boolean = false
    private var navSetupAttempts: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!authRepository.isSignedIn()) {
            startActivity(
                android.content.Intent(this, AuthActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            return
        }
        inAppUpdateManager = InAppUpdateManager(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inAppUpdateManager.checkForAppUpdate()

        // NavHostFragment is attached by FragmentContainerView during inflation, but ordering
        // vs. FragmentManager transactions can still race on some devices — resolve on the next pass.
        binding.root.post { tryWireNavigationUi() }
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateManager.isInitialized) {
            inAppUpdateManager.resumeUpdateIfInProgress()
        }
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
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                navController.navigateHomeClearingWorkflow()
                true
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                navController.navigateHomeClearingWorkflow()
            }
        }

        val roots = setOf(
            R.id.homeFragment,
            R.id.projectListFragment,
            R.id.profileFragment,
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in roots
        }
    }
}
