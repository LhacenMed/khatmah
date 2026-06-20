package com.lhacenmed.khatmah.onboarding

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhacenmed.khatmah.core.MainActivity
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.ShellRoutes
import com.lhacenmed.khatmah.core.ui.theme.Theme

/** Exit points out of the onboarding wizard, supplied by [OnboardingActivity]. */
class OnboardingExit(private val activity: ComponentActivity) {
    /** First-run complete → enter the app. */
    fun toMainApp() {
        activity.startActivity(Intent(activity, MainActivity::class.java))
        activity.finish()
    }

    /** Re-entered from settings (change location) → return to the caller. */
    fun toCaller() = activity.finish()
}

val LocalOnboardingExit = staticCompositionLocalOf<OnboardingExit> {
    error("No OnboardingExit provided")
}

/**
 * Hosts the one-time onboarding wizard as a self-contained NavHost
 * (Language → Notifications → Location → Country → City).
 *
 * Launched cold on first run (default start = language) or from Prayer settings'
 * "change location" via [EXTRA_START_ROUTE]. Steps navigate internally with the
 * NavHost; the three completion points exit through [LocalOnboardingExit].
 */
class OnboardingActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val start = intent.getStringExtra(EXTRA_START_ROUTE) ?: ShellRoutes.ONBOARDING_LANGUAGE
        val exit  = OnboardingExit(this)

        setContent {
            Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    CompositionLocalProvider(
                        LocalNavController provides navController,
                        LocalOnboardingExit provides exit,
                    ) {
                        NavHost(navController = navController, startDestination = start) {
                            composable(ShellRoutes.ONBOARDING_LANGUAGE)      { LanguageOnboardingPage() }
                            composable(ShellRoutes.ONBOARDING_NOTIFICATIONS) { NotificationPermissionPage() }
                            composable(ShellRoutes.ONBOARDING_LOCATION)      { LocationPermissionPage() }
                            composable(
                                route     = ShellRoutes.ONBOARDING_COUNTRY_SELECT,
                                arguments = listOf(
                                    navArgument("fromSettings") { type = NavType.BoolType; defaultValue = false },
                                ),
                            ) { CountrySelectPage() }
                            composable(
                                route     = ShellRoutes.ONBOARDING_CITY_SELECT,
                                arguments = listOf(
                                    navArgument("country")      { type = NavType.StringType; defaultValue = "" },
                                    navArgument("iso2")         { type = NavType.StringType; defaultValue = "" },
                                    navArgument("fromSettings") { type = NavType.BoolType;   defaultValue = false },
                                ),
                            ) { back ->
                                CitySelectPage(
                                    country      = back.arguments?.getString("country") ?: "",
                                    iso2         = back.arguments?.getString("iso2") ?: "",
                                    fromSettings = back.arguments?.getBoolean("fromSettings") ?: false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** One of the `ShellRoutes.ONBOARDING_*` route strings; defaults to language. */
        const val EXTRA_START_ROUTE = "start_route"
    }
}
