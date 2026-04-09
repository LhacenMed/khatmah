package com.lhacenmed.khatmah

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    private val todayFragment = TodayFragment()
    private val athkarFragment = AthkarFragment()
    private val prayersFragment = PrayersFragment()
    private val indexFragment = IndexFragment()
    private val moreFragment = MoreFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, todayFragment)
                .commit()
            supportActionBar?.title = getString(R.string.today)
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.today -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, todayFragment)
                        .commit()
                    supportActionBar?.title = getString(R.string.today)
                    true
                }
                R.id.athkar -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, athkarFragment)
                        .commit()
                    supportActionBar?.title = getString(R.string.athkar)
                    true
                }
                R.id.prayers -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, prayersFragment)
                        .commit()
                    supportActionBar?.title = getString(R.string.prayers)
                    true
                }
                R.id.index -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, indexFragment)
                        .commit()
                    supportActionBar?.title = getString(R.string.index)
                    true
                }
                R.id.more -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, moreFragment)
                        .commit()
                    supportActionBar?.title = getString(R.string.more)
                    true
                }
                else -> false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomNavigationView.selectedItemId != R.id.today) {
                    bottomNavigationView.selectedItemId = R.id.today
                    supportActionBar?.title = getString(R.string.today)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}