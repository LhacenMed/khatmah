package com.lhacenmed.khatmah

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lhacenmed.khatmah.ui.page.AppEntry
import com.lhacenmed.khatmah.ui.theme.Theme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                // Surface fills the window with MaterialTheme.colorScheme.background,
                // covering the XML windowBackground so it never bleeds through during
                // NavHost transition frames where composables render at reduced alpha.
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppEntry()
                }
            }
        }
    }
}