package com.lhacenmed.khatmah

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfig: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        setSupportActionBar(toolbar)

        val topLevelDestinations = setOf(
            R.id.today, R.id.athkar, R.id.prayers, R.id.index, R.id.more
        )
        appBarConfig = AppBarConfiguration(topLevelDestinations)

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig)
        bottomNav.setupWithNavController(navController)

        // Hide bottom nav when navigating into sub-pages
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.visibility =
                if (destination.id in topLevelDestinations) View.VISIBLE else View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        return NavigationUI.navigateUp(navController, appBarConfig) || super.onSupportNavigateUp()
    }
}
