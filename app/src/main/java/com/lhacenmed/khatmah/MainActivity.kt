package com.lhacenmed.khatmah

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.lhacenmed.khatmah.ui.page.AppEntry
import com.lhacenmed.khatmah.ui.theme.Theme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                AppEntry()
            }
        }
    }
}