package com.lhacenmed.khatmah
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    private val homeFragment = HomeFragment()
    private val settingsFragment = SettingsFragment()
    private val notificationFragment = NotificationFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, homeFragment)
            .commit()

        bottomNavigationView.getOrCreateBadge(R.id.notification).apply {
            isVisible = true
            number = 8
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, homeFragment)
                        .commit()
                    true
                }
                R.id.notification -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, notificationFragment)
                        .commit()
                    true
                }
                R.id.settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, settingsFragment)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }
}